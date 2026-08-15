#!/usr/bin/env python3
"""Score angle algorithms against human phase2 GT.

Grade bands (abs error degrees):
  ≤0.5 perfect | ≤1 excellent | ≤1.5 good | ≤2 acceptable | ≤2.5 poor | >2.5 failure

Usage:
  ~/miniconda3/envs/paddle_env_v3/bin/python scripts/deskew-angle-review/score_against_gt.py \\
    --gt …/deskew_angle_selections_phase2.json \\
    --phase1-out …/scratch/deskew-angle-review-20260807 \\
    --out …/scratch/deskew-score-…
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from datetime import datetime
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from estimators import bin_half_deg  # noqa: E402
from estimators_exp import run_experiment_suite  # noqa: E402
from run_phase1 import load_heat  # noqa: E402


def grade(err: float) -> str:
    a = abs(err)
    if a <= 0.5:
        return "perfect"
    if a <= 1.0:
        return "excellent"
    if a <= 1.5:
        return "good"
    if a <= 2.0:
        return "acceptable"
    if a <= 2.5:
        return "poor"
    return "failure"


def load_gt(path: Path) -> dict[str, dict]:
    raw = json.loads(path.read_text())
    out: dict[str, dict] = {}
    for f, v in (raw.get("ground_truth_angles") or {}).items():
        if not isinstance(v, dict):
            continue
        if v.get("status") == "none_good":
            out[f] = {"status": "none_good", "set": v.get("set")}
        elif v.get("status") in ("human_selected", "selection") or "gt_angle" in v:
            out[f] = {
                "status": "human_selected",
                "gt_angle": float(v.get("gt_angle", v.get("angle_bin", 0))),
                "angle_bin": float(v.get("angle_bin", v.get("gt_angle", 0))),
                "set": v.get("set"),
                "source": v.get("selection_source"),
            }
    for _id, c in (raw.get("selections_new") or raw.get("selections") or {}).items():
        if not isinstance(c, dict) or not c.get("file"):
            continue
        f = c["file"]
        if c.get("choice") == "best":
            out[f] = {
                "status": "human_selected",
                "gt_angle": float(c.get("angle_raw_mean", c.get("angle_bin", 0))),
                "angle_bin": float(c.get("angle_bin", c.get("angle_raw_mean", 0))),
                "set": c.get("set"),
                "source": "new",
            }
        elif c.get("choice") == "none":
            out[f] = {"status": "none_good", "set": c.get("set")}
    return out


def find_heat(phase1: Path, set_name: str | None, filename: str) -> tuple[Path, dict] | None:
    safe = re.sub(r"[^A-Za-z0-9._-]", "_", filename)
    work = phase1 / "work"
    if not work.is_dir():
        return None
    for d in work.iterdir():
        if not d.is_dir():
            continue
        if safe not in d.name:
            continue
        if set_name and set_name not in d.name:
            continue
        hf = d / "heat.f32"
        meta = d / "heat.f32.json"
        if hf.is_file() and meta.is_file():
            return hf, json.loads(meta.read_text())
    # fallback without set filter
    for d in work.iterdir():
        if d.is_dir() and safe in d.name:
            hf = d / "heat.f32"
            meta = d / "heat.f32.json"
            if hf.is_file() and meta.is_file():
                return hf, json.loads(meta.read_text())
    return None


def summarize(errs: list[float], times: list[float]) -> dict:
    e = np.array(errs, dtype=np.float64)
    grades = [grade(x) for x in e]
    n = len(e)
    def pct(label):
        return 100.0 * grades.count(label) / n if n else 0.0
    return {
        "n": n,
        "MAE": float(np.mean(e)),
        "MedAE": float(np.median(e)),
        "P90": float(np.percentile(e, 90)) if n else 0.0,
        "P95": float(np.percentile(e, 95)) if n else 0.0,
        "max": float(np.max(e)) if n else 0.0,
        "perfect_pct": pct("perfect"),
        "excellent_or_better_pct": 100.0 * sum(1 for g in grades if g in ("perfect", "excellent")) / n if n else 0,
        "good_or_better_pct": 100.0 * sum(1 for g in grades if g in ("perfect", "excellent", "good")) / n if n else 0,
        "acceptable_or_better_pct": 100.0 * sum(
            1 for g in grades if g in ("perfect", "excellent", "good", "acceptable")
        ) / n if n else 0,
        "failure_pct": pct("failure"),
        "failure_n": grades.count("failure"),
        "poor_n": grades.count("poor"),
        "grade_counts": {k: grades.count(k) for k in ("perfect", "excellent", "good", "acceptable", "poor", "failure")},
        "time_ms_mean": float(np.mean(times)) if times else 0.0,
        "time_ms_median": float(np.median(times)) if times else 0.0,
        "time_ms_sum": float(np.sum(times)) if times else 0.0,
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--gt", type=Path, required=True)
    ap.add_argument("--phase1-out", type=Path, required=True, help="dir with work/*/heat.f32")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--heat-tag", default="fp32_host", help="label for this heat source")
    args = ap.parse_args()
    out: Path = args.out
    out.mkdir(parents=True, exist_ok=True)

    gt = load_gt(args.gt)
    evalable = {f: v for f, v in gt.items() if v.get("status") == "human_selected"}
    print(f"GT human_selected={len(evalable)} none_good={sum(1 for v in gt.values() if v.get('status')=='none_good')}")

    # method -> list of abs errs / times
    errs: dict[str, list[float]] = defaultdict(list)
    times: dict[str, list[float]] = defaultdict(list)
    # also bin-level agreement with human bin
    bin_hit: dict[str, list[int]] = defaultdict(list)
    per_image = []
    missing = 0

    for i, (filename, g) in enumerate(sorted(evalable.items()), 1):
        found = find_heat(args.phase1_out, g.get("set"), filename)
        if not found:
            missing += 1
            continue
        hf, meta = found
        hw = int(meta.get("heat_w") or meta.get("w") or 0)
        hh = int(meta.get("heat_h") or meta.get("h") or 0)
        if hw <= 0 or hh <= 0:
            # try shape from host json
            hj = meta.get("host_json") or {}
            hw = int(hj.get("heat_w") or hj.get("w") or 0)
            hh = int(hj.get("heat_h") or hj.get("h") or 0)
        if hw <= 0 or hh <= 0:
            # infer from file size
            n = hf.stat().st_size // 4
            # common long-edge 2048 → try factors
            missing += 1
            continue
        heat = load_heat(hf, hh, hw)
        results = run_experiment_suite(heat)
        gt_ang = float(g["gt_angle"])
        gt_bin = float(g.get("angle_bin", bin_half_deg(gt_ang)))
        row = {"file": filename, "set": g.get("set"), "gt_angle": gt_ang, "gt_bin": gt_bin, "methods": {}}
        for r in results:
            e = abs(float(r.angle_raw) - gt_ang)
            errs[r.method].append(e)
            times[r.method].append(float(r.time_ms))
            bin_hit[r.method].append(1 if abs(bin_half_deg(r.angle_raw) - gt_bin) < 0.01 else 0)
            row["methods"][r.method] = {
                "angle": r.angle_raw,
                "err": e,
                "grade": grade(e),
                "time_ms": r.time_ms,
            }
        per_image.append(row)
        if i % 50 == 0:
            print(f"  {i}/{len(evalable)} …")

    print(f"scored {len(per_image)} missing_heat={missing}")

    table = []
    for method, e_list in errs.items():
        s = summarize(e_list, times[method])
        s["method"] = method
        s["bin_exact_pct"] = 100.0 * sum(bin_hit[method]) / len(bin_hit[method]) if bin_hit[method] else 0
        s["is_baseline"] = method.startswith("BASELINE_")
        table.append(s)

    # sort: best MAE, then fewer failures, then faster
    table.sort(key=lambda r: (r["MAE"], r["failure_n"], r["time_ms_median"]))

    baseline = next((t for t in table if t["is_baseline"]), None)
    report = {
        "generated": datetime.now().isoformat(),
        "heat_tag": args.heat_tag,
        "gt": str(args.gt),
        "phase1_heat": str(args.phase1_out),
        "n_scored": len(per_image),
        "n_missing_heat": missing,
        "grade_bands": {
            "perfect": "≤0.5°",
            "excellent": "≤1.0°",
            "good": "≤1.5°",
            "acceptable": "≤2.0°",
            "poor": "≤2.5°",
            "failure": ">2.5°",
        },
        "baseline": baseline,
        "ranking": table,
    }
    (out / "score_summary.json").write_text(json.dumps(report, indent=2))
    (out / "per_image.json").write_text(json.dumps(per_image, indent=2))

    # Markdown table
    lines = [
        f"# Deskew algorithm score vs phase2 GT ({args.heat_tag})",
        "",
        f"Generated: {report['generated']}",
        f"N scored: **{len(per_image)}** (human_selected). Missing heat: {missing}.",
        "",
        "Grade bands (abs error °): ≤0.5 perfect · ≤1 excellent · ≤1.5 good · ≤2 acceptable · ≤2.5 poor · >2.5 failure",
        "",
        f"**Baseline (current production):** `{baseline['method'] if baseline else '?'}` "
        f"— app `heatmapToAngle` thr=0.20 returns winning 0.5° bucket.",
        "",
        "| Rank | Method | MAE° | MedAE° | ≤0.5% | ≤1% | ≤2% | Fail% | Fail n | ms med | vs base MAE |",
        "|-----:|--------|-----:|-------:|------:|----:|----:|------:|-------:|-------:|------------:|",
    ]
    for i, t in enumerate(table, 1):
        dmae = (t["MAE"] - baseline["MAE"]) if baseline else 0.0
        mark = " **BASE**" if t["is_baseline"] else ""
        lines.append(
            f"| {i} | `{t['method']}`{mark} | {t['MAE']:.3f} | {t['MedAE']:.3f} | "
            f"{t['perfect_pct']:.1f} | {t['excellent_or_better_pct']:.1f} | "
            f"{t['acceptable_or_better_pct']:.1f} | {t['failure_pct']:.1f} | {t['failure_n']} | "
            f"{t['time_ms_median']:.2f} | {dmae:+.3f} |"
        )

    # recommendation block
    best = table[0]
    faster_better = [
        t for t in table
        if baseline
        and t["MAE"] <= baseline["MAE"] + 0.02
        and t["failure_n"] <= baseline["failure_n"]
        and t["time_ms_median"] < baseline["time_ms_median"] * 0.85
        and not t["is_baseline"]
    ]
    more_acc = [
        t for t in table
        if baseline and (t["MAE"] < baseline["MAE"] - 0.02 or t["failure_n"] < baseline["failure_n"])
    ][:8]

    lines += [
        "",
        "## Takeaways",
        "",
        f"- **Best MAE:** `{best['method']}` MAE={best['MAE']:.3f}° fail_n={best['failure_n']} "
        f"med_ms={best['time_ms_median']:.2f}",
    ]
    if baseline:
        lines.append(
            f"- **Baseline:** MAE={baseline['MAE']:.3f}° fail_n={baseline['failure_n']} "
            f"med_ms={baseline['time_ms_median']:.2f} · perfect={baseline['perfect_pct']:.1f}% · "
            f"≤2°={baseline['acceptable_or_better_pct']:.1f}%"
        )
    if more_acc:
        lines.append("- **More accurate than baseline (top):**")
        for t in more_acc[:5]:
            lines.append(
                f"  - `{t['method']}` MAE={t['MAE']:.3f} (Δ{t['MAE']-baseline['MAE']:+.3f}) "
                f"fail={t['failure_n']} ms={t['time_ms_median']:.2f}"
            )
    if faster_better:
        lines.append("- **Faster with accuracy ≈ baseline:**")
        for t in faster_better[:5]:
            lines.append(
                f"  - `{t['method']}` MAE={t['MAE']:.3f} ms={t['time_ms_median']:.2f} "
                f"(base {baseline['time_ms_median']:.2f})"
            )
    else:
        lines.append("- No clear **faster-and-equal** winner under strict MAE/fail gates (see table).")

    lines += [
        "",
        "### Notes",
        "",
        "- Angle method time only (det heat already computed; det ~0.5–2.5s dominates production).",
        "- Ensemble `time_ms` = sum of members (serial). Parallelize on device if adopted.",
        "- `cpp_style_*` uses mean of angles in winning bucket; **BASELINE** uses bucket center only (matches C++).",
        "",
    ]
    (out / "REPORT.md").write_text("\n".join(lines))
    print("\n".join(lines[:40]))
    print(f"\n… full report → {out / 'REPORT.md'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
