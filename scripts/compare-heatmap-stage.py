#!/usr/bin/env python3
"""Compare HeatmapStageDump results.jsonl files (baby-step gates).

Gates (in order; stop attributing later stages if earlier fails):
  1) source_sha256  — decode/mono identity
  2) feed_sha256 per scale — preprocess/scale identity
  3) heat_crc32_f32_bits / heatmap_hist_sha256 — det heatmap identity

Angles (paddle_cpp_angle) are reported but not used as pass/fail.

Usage:
  scripts/compare-heatmap-stage.py \\
    --a path/to/runA/results.jsonl --a-label pixel-fp32 \\
    --b path/to/runB/results.jsonl --b-label emu-fp32
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def load_jsonl(p: Path) -> dict[str, dict]:
    out = {}
    with p.open() as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            o = json.loads(line)
            out[o["file"]] = o
    return out


def scale_map(row: dict) -> dict[int, dict]:
    m = {}
    for s in row.get("scales") or []:
        m[int(s["scale"])] = s
    return m


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--a", required=True, type=Path)
    ap.add_argument("--b", required=True, type=Path)
    ap.add_argument("--a-label", default="A")
    ap.add_argument("--b-label", default="B")
    args = ap.parse_args()

    a = load_jsonl(args.a)
    b = load_jsonl(args.b)
    common = sorted(set(a) & set(b))
    only_a = sorted(set(a) - set(b))
    only_b = sorted(set(b) - set(a))
    print(f"common={len(common)} only_{args.a_label}={len(only_a)} only_{args.b_label}={len(only_b)}")

    src_eq = feed_eq = heat_eq = 0
    src_diff = feed_diff = heat_diff = []
    angles = []

    for name in common:
        ra, rb = a[name], b[name]
        if ra.get("error") or rb.get("error"):
            print(f"  SKIP {name}: error a={ra.get('error')} b={rb.get('error')}")
            continue
        def src_key(r):
            return r.get("source_sha256") or r.get("source_crc32")

        sa, sb = src_key(ra), src_key(rb)
        if sa is not None and sa == sb:
            src_eq += 1
        else:
            src_diff.append(name)
            # later stages not meaningful as shared-input compare
            continue

        sma, smb = scale_map(ra), scale_map(rb)
        all_feed = True
        all_heat = True
        for sc in sorted(set(sma) & set(smb)):
            fa, fb = sma[sc], smb[sc]

            def feed_key(s):
                return s.get("feed_sha256") or s.get("feed_crc32")

            if feed_key(fa) != feed_key(fb):
                all_feed = False
            ha = fa.get("heat_crc32_f32_bits")
            hb = fb.get("heat_crc32_f32_bits")
            hsa, hsb = fa.get("heatmap_hist_sha256"), fb.get("heatmap_hist_sha256")
            if ha is not None and hb is not None:
                if ha != hb:
                    all_heat = False
            elif hsa is not None and hsb is not None and hsa != hsb:
                all_heat = False
            elif fa.get("heatmap_mass_bins1_99") != fb.get("heatmap_mass_bins1_99"):
                all_heat = False
            aa, ab = fa.get("paddle_cpp_angle"), fb.get("paddle_cpp_angle")
            if aa is not None and ab is not None and aa != ab:
                angles.append((name, sc, aa, ab))
        if all_feed:
            feed_eq += 1
        else:
            feed_diff.append(name)
            continue
        if all_heat:
            heat_eq += 1
        else:
            heat_diff.append(name)

    n = len(common)
    print(f"\n=== Gates ({args.a_label} vs {args.b_label}) ===")
    print(f"1 source_sha256 exact: {src_eq}/{n - len([x for x in common if a[x].get('error') or b[x].get('error')])}  (diff {len(src_diff)})")
    print(f"2 feed_sha256 exact (among source-match): {feed_eq}/{src_eq}  (diff {len(feed_diff)})")
    print(f"3 heat CRC/hist exact (among feed-match): {heat_eq}/{feed_eq}  (diff {len(heat_diff)})")
    print(f"paddle_cpp_angle diffs (any scale, info only): {len(angles)}")

    if src_diff[:8]:
        print("\nsource diffs (first 8):", src_diff[:8])
    if feed_diff[:8]:
        print("feed diffs (first 8):", feed_diff[:8])
    if heat_diff[:8]:
        print("heat diffs (first 8):", heat_diff[:8])
    if angles[:8]:
        print("angle info (first 8):", angles[:8])

    # Exit non-zero if heat not identical among feed-matched (still useful as CI later)
    return 0


if __name__ == "__main__":
    sys.exit(main())
