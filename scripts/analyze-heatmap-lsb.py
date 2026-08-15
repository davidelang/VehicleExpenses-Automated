#!/usr/bin/env python3
"""Analyze heatmap float dumps + angle threshold / LSB-force experiments.

Expects HeatmapStageDump writeBins output: scale{N}_heatmap.f32 (row-major float32),
and optional results.jsonl for dims.

Usage (paddle_env_v3 recommended):
  ~/miniconda3/envs/paddle_env_v3/bin/python scripts/analyze-heatmap-lsb.py \\
    --a path/pixel/fp32_run --b path/emu/fp32_run

Angle experiments (on each heat plane):
  thr 0.0 / 1/255 / 0.20 (app default)
  force LSB of quantised u8 heat: clear bit0, set bit0, force 0, force 254 on nonzeros
"""
from __future__ import annotations

import argparse
import json
import re
import struct
from collections import Counter
from pathlib import Path

import numpy as np

try:
    import cv2
except ImportError:
    cv2 = None


def load_jsonl(root: Path) -> dict[str, dict]:
    p = root / "results.jsonl"
    if not p.exists():
        # nested run dir
        for c in root.iterdir():
            if c.is_dir() and (c / "results.jsonl").exists():
                return load_jsonl(c)
        return {}
    out = {}
    for line in p.open():
        o = json.loads(line)
        out[o["file"]] = o
    return out


def run_root(root: Path) -> Path:
    if (root / "results.jsonl").exists():
        return root
    for c in root.iterdir():
        if c.is_dir() and (c / "results.jsonl").exists():
            return c
    return root


def subdir(root: Path, name: str) -> Path | None:
    safe = re.sub(r"[^A-Za-z0-9._-]", "_", name)
    for p in root.iterdir():
        if p.is_dir() and safe in p.name:
            return p
    return None


def load_heat(path: Path, h: int, w: int) -> np.ndarray:
    raw = path.read_bytes()
    n = h * w
    arr = np.frombuffer(raw, dtype=np.float32)
    if arr.size < n:
        raise ValueError(f"{path}: {arr.size} floats < {n}")
    return arr[:n].reshape(h, w).copy()


def float_diff_stats(a: np.ndarray, b: np.ndarray) -> dict:
    d = a.astype(np.float64) - b.astype(np.float64)
    ad = np.abs(d)
    nz = int(np.count_nonzero(d))
    # u8-equivalent steps if heat is u8/255
    step = 1.0 / 255.0
    u8a = np.clip(np.rint(a * 255.0), 0, 255).astype(np.int16)
    u8b = np.clip(np.rint(b * 255.0), 0, 255).astype(np.int16)
    du = u8a - u8b
    hist = Counter(int(x) for x in du.ravel() if x != 0)
    return {
        "nz": nz,
        "n": int(a.size),
        "max_abs": float(ad.max()) if nz else 0.0,
        "mean_abs": float(ad.mean()),
        "mean_abs_nz": float(ad[d != 0].mean()) if nz else 0.0,
        "frac_gt_half_step": float(np.mean(ad > 0.5 * step)),
        "frac_gt_1_step": float(np.mean(ad > step)),
        "frac_gt_2_step": float(np.mean(ad > 2 * step)),
        "u8_nz": int(np.count_nonzero(du)),
        "u8_max_abs": int(np.max(np.abs(du))) if nz else 0,
        "u8_hist_top": hist.most_common(8),
        "max_eq_one_u8_step": bool(nz == 0 or (ad.max() <= step * 1.01 and int(np.max(np.abs(du))) <= 1)),
    }


def heatmap_to_angle(heat: np.ndarray, threshold: float) -> float:
    """Mirror nativeHeatmapToAngle (approx): mask > thr, CC, minAreaRect, 0.5° buckets."""
    if cv2 is None:
        raise RuntimeError("need cv2")
    h, w = heat.shape
    mask = (heat > threshold).astype(np.uint8)
    nlab, labels, stats, _ = cv2.connectedComponentsWithStats(mask, connectivity=8)
    if nlab <= 1:
        return 0.0
    buckets: dict[int, float] = {}
    for lab in range(1, nlab):
        area = int(stats[lab, cv2.CC_STAT_AREA])
        if area < 10:
            continue
        left = int(stats[lab, cv2.CC_STAT_LEFT])
        top = int(stats[lab, cv2.CC_STAT_TOP])
        width = int(stats[lab, cv2.CC_STAT_WIDTH])
        height = int(stats[lab, cv2.CC_STAT_HEIGHT])
        ys, xs = np.where(labels[top : top + height, left : left + width] == lab)
        if len(xs) == 0:
            continue
        pts = np.column_stack([xs + left, ys + top]).astype(np.float32)
        conf = float(heat[ys + top, xs + left].mean())
        rect = cv2.minAreaRect(pts)
        (_, _), (rw, rh), ang = rect
        # same spirit as calculateAngle — normalize to deskew-ish small range
        if rw < rh:
            ang = ang - 90.0
        # clamp to [-45,45]-ish like many deskew helpers
        while ang <= -90:
            ang += 180
        while ang > 90:
            ang -= 180
        if ang > 45:
            ang -= 90
        elif ang < -45:
            ang += 90
        b = int(round(ang * 2.0))
        peri = float(cv2.arcLength(pts.reshape(-1, 1, 2), True))
        buckets[b] = buckets.get(b, 0.0) + peri * conf
    if not buckets:
        return 0.0
    best = max(buckets.items(), key=lambda kv: kv[1])[0]
    return best * 0.5


def force_u8_ops(heat: np.ndarray) -> dict[str, np.ndarray]:
    """Heat in [0,1] as u8/255; return variants."""
    u8 = np.clip(np.rint(heat * 255.0), 0, 255).astype(np.uint8)
    out = {
        "as_u8": (u8.astype(np.float32) / 255.0),
        "lsb_clear": ((u8 & 0xFE).astype(np.float32) / 255.0),
        "lsb_set": ((u8 | 0x01).astype(np.float32) / 255.0),
        "force0": np.zeros_like(heat, dtype=np.float32),  # all zero
        "nonzero_to_254": np.where(u8 > 0, 254, 0).astype(np.float32) / 255.0,
        "nonzero_to_1": np.where(u8 > 0, 1, 0).astype(np.float32) / 255.0,
    }
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--a", type=Path, required=True)
    ap.add_argument("--b", type=Path, required=True)
    ap.add_argument("--a-label", default="A")
    ap.add_argument("--b-label", default="B")
    args = ap.parse_args()
    ra, rb = run_root(args.a), run_root(args.b)
    ja, jb = load_jsonl(ra), load_jsonl(rb)
    common = sorted(set(ja) & set(jb))
    print(f"roots a={ra} b={rb} common={len(common)}")

    for name in common:
        print("=" * 72)
        print(name)
        rowa, rowb = ja[name], jb[name]
        sa, sb = subdir(ra, name), subdir(rb, name)
        if not sa or not sb:
            print("  missing subdir")
            continue
        for sc in (224, 608, 1024):
            fa = next(sa.glob(f"scale{sc}_heatmap.f32"), None)
            fb = next(sb.glob(f"scale{sc}_heatmap.f32"), None)
            if not fa or not fb:
                continue
            # dims from json
            sma = {int(s["scale"]): s for s in rowa.get("scales") or []}
            smb = {int(s["scale"]): s for s in rowb.get("scales") or []}
            ha = int(sma[sc].get("heat_h") or sma[sc].get("tier") or sc)
            wa = int(sma[sc].get("heat_w") or sc)
            hb = int(smb[sc].get("heat_h") or sc)
            wb = int(smb[sc].get("heat_w") or sc)
            if (ha, wa) != (hb, wb):
                print(f"  sc{sc}: dim mismatch {wa}x{ha} vs {wb}x{hb}")
                continue
            A = load_heat(fa, ha, wa)
            B = load_heat(fb, hb, wb)
            st = float_diff_stats(A, B)
            print(
                f"  sc{sc} {wa}x{ha}: nz={st['nz']}/{st['n']} max|d|={st['max_abs']:.6g} "
                f"mean_abs_nz={st['mean_abs_nz']:.6g} "
                f"frac>|1/255|={st['frac_gt_1_step']:.4f} "
                f"u8_nz={st['u8_nz']} u8_max|d|={st['u8_max_abs']} "
                f"lsb_only={st['max_eq_one_u8_step']} hist={st['u8_hist_top'][:5]}"
            )
            if cv2 is None:
                continue
            # angle threshold sweep on A
            for thr_name, thr in [("0", 0.0), ("1/255", 1.0 / 255.0), ("2/255", 2.0 / 255.0), ("0.20", 0.20)]:
                aa = heatmap_to_angle(A, thr)
                ab = heatmap_to_angle(B, thr)
                print(f"    angle thr={thr_name}: {args.a_label}={aa:.2f} {args.b_label}={ab:.2f} d={abs(aa-ab):.2f}")
            # LSB force on A only (stability of angle under quantization noise)
            base = heatmap_to_angle(A, 0.20)
            for lab, H in force_u8_ops(A).items():
                if lab == "as_u8":
                    continue
                ang = heatmap_to_angle(H, 0.20)
                ang0 = heatmap_to_angle(H, 0.0)
                ang1 = heatmap_to_angle(H, 1.0 / 255.0)
                print(
                    f"    force[{lab}] thr0.20={ang:.2f} (d_base={abs(ang-base):.2f}) "
                    f"thr0={ang0:.2f} thr1/255={ang1:.2f}"
                )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
