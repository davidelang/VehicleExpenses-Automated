#!/usr/bin/env python3
"""Compare First 10 experiment JSON to a golden baseline (per-device).

Gate for paddle SO promotes: current run must match golden cost/vol/tilt/deskew
(and optionally heatmap mass within a small absolute delta).

Usage:
  scripts/first10-golden-compare.py \\
    --current path/to/pump_results.json \\
    --golden  path/to/pin_emu_pump.json \\
    [--kind pump|align] [--mass-tol 200]

Exit 0 on full match, 1 on any outcome mismatch.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def load(p: Path) -> dict:
    with p.open() as f:
        return json.load(f)


def pump_row(r: dict) -> dict:
    branches = (r.get("tree") or {}).get("branches") or {}
    node = branches.get("Set G-- (4 pass, none, calculated)")
    if not node:
        for k, v in branches.items():
            if "G--" in k:
                node = v
                break
    node = node or {}
    res = (node.get("results") or {}).get("Paddle") or {}
    meta = node.get("metadata") or {}
    hist = None
    if "heatmap_hist_1024" in meta:
        h = meta["heatmap_hist_1024"]
        hist = json.loads(h) if isinstance(h, str) else h
    deskew = r.get("deskew") or {}
    return {
        "file": r.get("file"),
        "deskew": deskew.get("angle_a") if isinstance(deskew, dict) else deskew,
        "cost": res.get("cost"),
        "vol": res.get("vol"),
        "tilt": meta.get("tilt"),
        "mass": sum(hist[1:]) if hist else None,
    }


def align_row(r: dict) -> dict:
    d = r.get("deskew") or {}
    return {
        "file": r.get("file"),
        "winner": r.get("winner"),
        "angle_a": d.get("angle_a") if isinstance(d, dict) else None,
        "paddle_kt": d.get("paddle_kt_angle") if isinstance(d, dict) else None,
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--current", required=True, type=Path)
    ap.add_argument("--golden", required=True, type=Path)
    ap.add_argument("--kind", choices=("pump", "align"), default="pump")
    ap.add_argument("--mass-tol", type=int, default=200, help="allowed |mass| delta for pump")
    ap.add_argument("--require-mass", action="store_true", help="fail if mass delta > tol")
    args = ap.parse_args()

    cur = load(args.current)
    gold = load(args.golden)
    print(
        f"current: {cur.get('version')} {cur.get('device')} {cur.get('timestamp')} n={len(cur.get('results', []))}"
    )
    print(
        f"golden:  {gold.get('version')} {gold.get('device')} {gold.get('timestamp')} n={len(gold.get('results', []))}"
    )

    row_fn = pump_row if args.kind == "pump" else align_row
    keys = (
        ("cost", "vol", "tilt", "deskew")
        if args.kind == "pump"
        else ("winner", "angle_a", "paddle_kt")
    )

    cm = {row_fn(r)["file"]: row_fn(r) for r in cur.get("results", [])}
    gm = {row_fn(r)["file"]: row_fn(r) for r in gold.get("results", [])}
    common = sorted(set(cm) & set(gm))
    if len(common) < 10 and len(cm) == 10 and len(gm) >= 10:
        # golden may be full suite: restrict to current files
        pass
    if not common:
        print("FAIL: no common files")
        return 1

    bad = []
    for f in common:
        c, g = cm[f], gm[f]
        outcome_ok = all(c.get(k) == g.get(k) for k in keys)
        mass_ok = True
        if args.kind == "pump" and args.require_mass:
            mc, mg = c.get("mass"), g.get("mass")
            if mc is None or mg is None:
                mass_ok = False
            elif abs(mc - mg) > args.mass_tol:
                mass_ok = False
        if not outcome_ok or not mass_ok:
            bad.append((f, c, g, outcome_ok, mass_ok))

    print(f"outcome match: {len(common) - len(bad)}/{len(common)}")
    for f, c, g, o_ok, m_ok in bad:
        print(f"  DIFF {f} outcome_ok={o_ok} mass_ok={m_ok}")
        print(f"    current {c}")
        print(f"    golden  {g}")

    if bad:
        print("FAIL")
        return 1
    print("PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
