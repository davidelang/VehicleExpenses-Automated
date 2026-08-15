#!/usr/bin/env python3
"""Merge device-exported odo crops into phase2 previews/ and refresh results + HTML."""
from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path

from run_phase2 import build_html, load_prior  # type: ignore


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True, type=Path, help="phase2 output dir")
    ap.add_argument(
        "--crops",
        required=True,
        type=Path,
        help="pulled crops root (…/crops or parent containing preview_id dirs)",
    )
    ap.add_argument("--selections", type=Path, default=None)
    args = ap.parse_args()
    out: Path = args.out
    crops = args.crops
    if (crops / "crops").is_dir():
        crops = crops / "crops"
    prev = out / "previews"
    n_copy = 0
    for sub in sorted(crops.iterdir() if crops.is_dir() else []):
        if not sub.is_dir():
            continue
        dest = prev / sub.name
        dest.mkdir(parents=True, exist_ok=True)
        for f in sub.glob("odo_bin_*.jpg"):
            shutil.copy2(f, dest / f.name)
            n_copy += 1
    print(f"copied {n_copy} odo crops into {prev}")

    results_path = out / "results.json"
    data = json.loads(results_path.read_text())
    rows = data["rows"]
    n_linked = 0
    for r in rows:
        pid = r["id"]
        for bp in r.get("bin_previews") or []:
            b = bp["angle_bin"]
            name = f"odo_bin_{b:.1f}.jpg".replace("-", "m")
            p = prev / pid / name
            if p.is_file():
                bp["odo_crop"] = f"previews/{pid}/{name}"
                n_linked += 1
            else:
                bp.pop("odo_crop", None)
    results_path.write_text(json.dumps(data, indent=2))
    with (out / "results.jsonl").open("w") as f:
        for r in rows:
            f.write(json.dumps(r) + "\n")
    priors = {}
    if args.selections and args.selections.is_file():
        priors = load_prior(args.selections)
    elif (out / "priors.json").is_file():
        priors = json.loads((out / "priors.json").read_text())
    methods = data.get("methods") or []
    build_html(out, rows, methods, priors)
    print(f"linked {n_linked} bin previews with odo_crop; HTML refreshed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
