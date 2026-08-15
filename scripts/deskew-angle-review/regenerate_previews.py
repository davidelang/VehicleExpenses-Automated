#!/usr/bin/env python3
"""Re-render deskew previews with corrected rotation sign (no re-det)."""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

import cv2
import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from run_phase1 import make_preview, rotate_bgr  # noqa: E402


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, required=True, help="deskew-angle-review output dir")
    args = ap.parse_args()
    out = args.out
    data = json.loads((out / "results.json").read_text())
    rows = data["rows"]
    n = 0
    for r in rows:
        if r.get("error") or not r.get("bin_previews"):
            continue
        src = Path(r["path"])
        bgr = cv2.imread(str(src), cv2.IMREAD_COLOR)
        if bgr is None:
            mono = cv2.imread(str(src), cv2.IMREAD_GRAYSCALE)
            if mono is None:
                print("skip load", r["file"])
                continue
            bgr = cv2.cvtColor(mono, cv2.COLOR_GRAY2BGR)
        pid = r["id"]
        prev_sub = out / "previews" / pid
        prev_sub.mkdir(parents=True, exist_ok=True)
        # refresh orig too
        cv2.imwrite(
            str(prev_sub / "orig.jpg"),
            make_preview(bgr),
            [int(cv2.IMWRITE_JPEG_QUALITY), 85],
        )
        for b in r["bin_previews"]:
            raw_mean = float(b.get("angle_raw_mean", b["angle_bin"]))
            desk = rotate_bgr(bgr, raw_mean)
            # path may be previews/id/file.jpg
            fn = Path(b["path"]).name
            cv2.imwrite(
                str(prev_sub / fn),
                make_preview(desk),
                [int(cv2.IMWRITE_JPEG_QUALITY), 85],
            )
        n += 1
        if n % 50 == 0:
            print(f"… {n}/{len(rows)}")
    print(f"Regenerated previews for {n} rows → {out / 'previews'}")
    print("Reload index.html in the browser (hard refresh).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
