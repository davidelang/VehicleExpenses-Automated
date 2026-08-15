#!/usr/bin/env python3
"""Phase 2 deskew review: slim methods, reuse phase1 heat, improved HTML.

- Drop proj / soft_pca / aspect filters
- Keep hough, cpp(1/255 & 0.20), median_rect, vote_median(hough,cpp_lsb,med)
- Order images by prior angle (most negative → most positive)
- Yellow lines on deskew previews
- Prior human selection as yellow border; new picks distinct; no-click keeps prior
- Emit dash_bins_for_device.json for Android odo-crop export

Usage:
  ~/miniconda3/envs/paddle_env_v3/bin/python scripts/deskew-angle-review/run_phase2.py \\
    --phase1-out …/scratch/deskew-angle-review-20260807 \\
    --selections …/deskew_angle_selections.json \\
    --out …/scratch/deskew-angle-review-phase2-…
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from estimators import bin_half_deg, run_all_estimators  # noqa: E402
from run_phase1 import make_preview, rotate_bgr  # noqa: E402

PHOTOS = Path("/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/research/photos")
EXISTING_GT = Path(
    "/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/ground_truth_angles.json"
)


def load_prior(selections_path: Path) -> dict[str, dict]:
    """file -> {choice, angle_bin, angle_raw_mean, ...}"""
    if not selections_path.is_file():
        return {}
    raw = json.loads(selections_path.read_text())
    out: dict[str, dict] = {}
    for f, v in (raw.get("ground_truth_angles") or {}).items():
        if not isinstance(v, dict):
            continue
        if v.get("status") == "none_good":
            out[f] = {"choice": "none", "set": v.get("set")}
        elif v.get("status") in ("human_selected", "selection") or "gt_angle" in v:
            out[f] = {
                "choice": "best",
                "angle_bin": v.get("angle_bin", round(float(v["gt_angle"]) * 2) / 2),
                "angle_raw_mean": float(v.get("gt_angle", v.get("angle_bin", 0))),
                "set": v.get("set"),
            }
    for _id, c in (raw.get("selections") or {}).items():
        if not isinstance(c, dict) or not c.get("file"):
            continue
        f = c["file"]
        if c.get("choice") == "best":
            out[f] = {
                "choice": "best",
                "angle_bin": c.get("angle_bin"),
                "angle_raw_mean": c.get("angle_raw_mean", c.get("angle_bin")),
                "set": c.get("set"),
            }
        elif c.get("choice") == "none":
            out[f] = {"choice": "none", "set": c.get("set")}
    return out


def draw_yellow_guides(bgr: np.ndarray, n: int = 10) -> np.ndarray:
    """Horizontal yellow guides at 1px thickness on *this* image.

    Call **after** ``make_preview`` so lines stay 1 display-pixel high when the
    JPEG is shown (drawing on full-res then INTER_AREA shrink erases them).
    """
    out = bgr.copy()
    h, w = out.shape[:2]
    for i in range(1, n + 1):
        y = int(round(i * h / (n + 1)))
        # LINE_8 (not AA): hard 1px row so JPEG + CSS downscale still reads
        cv2.line(out, (0, y), (w - 1, y), (0, 255, 255), 1, cv2.LINE_8)
    return out


def find_heat(phase1: Path, set_name: str, filename: str) -> tuple[Path, dict] | None:
    safe = re.sub(r"[^A-Za-z0-9._-]", "_", filename)
    # work/0001_dash_NAME/
    for d in (phase1 / "work").iterdir() if (phase1 / "work").is_dir() else []:
        if not d.is_dir():
            continue
        if safe in d.name and set_name in d.name:
            hf = d / "heat.f32"
            meta = d / "heat.f32.json"
            if hf.is_file() and meta.is_file():
                return hf, json.loads(meta.read_text())
    return None


def build_html(out_dir: Path, rows: list[dict], methods: list[str], priors: dict) -> None:
    data = {"rows": rows, "methods": methods, "priors": priors}
    data_js = json.dumps(data, separators=(",", ":"))
    gt_js = "{}"
    if EXISTING_GT.exists():
        try:
            gt_js = json.dumps(json.loads(EXISTING_GT.read_text()), separators=(",", ":"))
        except Exception:
            pass

    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<title>Deskew angle review — phase 2</title>
<style>
  :root {{ font-family: system-ui, sans-serif; }}
  body {{ margin: 0; background: #111; color: #eee; }}
  header {{ position: sticky; top: 0; background: #1a1a1a; padding: 12px 16px;
    border-bottom: 1px solid #333; z-index: 10; display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }}
  header button {{ padding: 8px 14px; cursor: pointer; }}
  #progress {{ color: #8cf; }}
  .row {{ border-bottom: 1px solid #333; padding: 16px; }}
  .row.done-best {{ background: #0a2010; }}
  .row.done-none {{ background: #20100a; }}
  .meta {{ font-size: 13px; color: #aaa; margin-bottom: 8px; }}
  .angles {{ font-family: ui-monospace, monospace; font-size: 12px; margin: 8px 0;
    white-space: pre-wrap; background: #1e1e1e; padding: 8px; border-radius: 4px; }}
  .previews {{ display: flex; flex-wrap: wrap; gap: 10px; align-items: flex-start; }}
  .card {{ background: #1e1e1e; border: 2px solid #444; border-radius: 6px; padding: 6px; max-width: 520px; }}
  .card img {{ display: block; max-width: 480px; width: 100%; height: auto; background: #000; }}
  .card .lab {{ font-size: 12px; margin-top: 4px; color: #ccc; }}
  /* 1 CSS-px guides over deskew — difference blend stays visible on white LCD / dark bg */
  .deskew-wrap {{ position: relative; display: block; max-width: 480px; width: 100%; line-height: 0; }}
  .deskew-wrap > img {{ max-width: 100%; width: 100%; height: auto; display: block; }}
  .deskew-guides {{ pointer-events: none; position: absolute; left: 0; top: 0; right: 0; bottom: 0; z-index: 2; }}
  .deskew-guides i {{ position: absolute; left: 0; right: 0; height: 1px; margin: 0; padding: 0;
    background: #fff; mix-blend-mode: difference; display: block; }}
  /* prior selection from last review (unchanged if not clicked) */
  .card.prior {{ border-color: #ff0; box-shadow: 0 0 0 2px #cc0; }}
  /* new selection this session */
  .card.selected {{ border-color: #4c8; box-shadow: 0 0 0 2px #4c8; }}
  .card.prior.selected {{ border-color: #4c8; box-shadow: 0 0 0 2px #4c8, 0 0 0 5px #cc0; }}
  .actions {{ margin-top: 10px; display: flex; gap: 8px; flex-wrap: wrap; }}
  .actions button {{ padding: 6px 12px; cursor: pointer; }}
  .hint {{ color: #888; font-size: 12px; }}
  .odo {{ border: 1px solid #666; margin-top: 6px; }}
  .odo img {{ max-width: 360px; }}
</style>
</head>
<body>
<header>
  <strong>Deskew review phase 2</strong>
  <span id="progress">0 / 0</span>
  <button type="button" id="btn-export">Export selections JSON</button>
  <button type="button" id="btn-next">Next unreviewed</button>
  <span class="hint">Yellow border = prior selection (kept if you don't click). Green = new pick this session.
    1px horizontal guides on deskew (CSS difference-blend + baked PNG). Dash odo crops when present.
    Production uses one ensemble result (vote), not multi-try OCR.</span>
</header>
<div id="root"></div>
<script>
const DATA = {data_js};
const EXISTING_GT = {gt_js};
const STORAGE_KEY = "deskew-angle-review-v2-" + location.pathname;

function loadNew() {{
  try {{ return JSON.parse(localStorage.getItem(STORAGE_KEY) || "{{}}"); }}
  catch (e) {{ return {{}}; }}
}}
function saveNew(s) {{
  localStorage.setItem(STORAGE_KEY, JSON.stringify(s));
  updateProgress();
}}

/** Effective selection: new override else prior */
function effective(r) {{
  const neu = loadNew()[r.id];
  if (neu) return {{...neu, source: "new"}};
  const p = DATA.priors[r.file];
  if (p) return {{...p, source: "prior", file: r.file, set: r.set}};
  return null;
}}

function updateProgress() {{
  let done = 0;
  for (const r of DATA.rows) if (effective(r)) done++;
  document.getElementById("progress").textContent = done + " / " + DATA.rows.length + " have a selection (prior or new)";
}}

function render() {{
  const root = document.getElementById("root");
  root.innerHTML = "";
  const neu = loadNew();
  for (const r of DATA.rows) {{
    const div = document.createElement("div");
    div.className = "row";
    div.id = "row-" + r.id;
    const eff = effective(r);
    if (eff && eff.choice === "best") div.classList.add("done-best");
    if (eff && eff.choice === "none") div.classList.add("done-none");

    const gtHint = EXISTING_GT[r.file];
    let gtLine = "";
    if (gtHint && typeof gtHint.gt_angle === "number")
      gtLine = ` | archive GT ${{gtHint.gt_angle.toFixed(3)}}°`;

    const meta = document.createElement("div");
    meta.className = "meta";
    meta.innerHTML = `<b>#${{r.index}}</b> <code>${{r.set}}/${{r.file}}</code>
      ${{r.source_w}}x${{r.source_h}} det_ms=${{(r.det_ms||0).toFixed?.(0) ?? r.det_ms ?? "?"}}
      heat=${{r.heat_w}}x${{r.heat_h}}${{gtLine}}
      ${{eff ? " | <b>" + (eff.source||"") + ": " + (eff.choice==="none"?"NONE":(eff.angle_bin+"°")) + "</b>" : ""}}`;
    div.appendChild(meta);

    const ang = document.createElement("div");
    ang.className = "angles";
    let lines = "methods (raw °, ms) — production candidate is vote_median_*:\\n";
    for (const m of r.methods) {{
      const mark = m.method.startsWith("vote_") ? " ★" : "";
      lines += `  ${{m.method.padEnd(28)}}${{mark}} raw=${{Number(m.angle_raw).toFixed(4).padStart(9)}}  bin=${{Number(m.angle_bin).toFixed(1).padStart(5)}}  ${{Number(m.time_ms).toFixed(1).padStart(7)}}ms  ${{m.notes||""}}\\n`;
    }}
    lines += "preview bins: " + (r.unique_bins||[]).join(", ");
    ang.textContent = lines;
    div.appendChild(ang);

    const prev = document.createElement("div");
    prev.className = "previews";
    const c0 = document.createElement("div");
    c0.className = "card";
    c0.innerHTML = `<img src="${{r.preview_orig}}" loading="lazy"/><div class="lab">original</div>`;
    prev.appendChild(c0);

    const priorBin = (eff && eff.choice === "best") ? Number(eff.angle_bin) : null;
    const newSel = neu[r.id];

    for (const b of (r.bin_previews || [])) {{
      const c = document.createElement("div");
      c.className = "card";
      const isPrior = priorBin != null && Math.abs(Number(b.angle_bin) - priorBin) < 0.01 && !(newSel && newSel.choice === "best" && Math.abs(Number(newSel.angle_bin)-Number(b.angle_bin))>0.01);
      // yellow if this bin is the prior and user hasn't changed to a different best
      if (DATA.priors[r.file] && DATA.priors[r.file].choice === "best" &&
          Math.abs(Number(DATA.priors[r.file].angle_bin) - Number(b.angle_bin)) < 0.01 &&
          !(newSel && newSel.choice === "best" && Math.abs(Number(newSel.angle_bin) - Number(b.angle_bin)) > 0.01)) {{
        c.classList.add("prior");
      }}
      if (newSel && newSel.choice === "best" && Math.abs(Number(newSel.angle_bin) - Number(b.angle_bin)) < 0.01) {{
        c.classList.add("selected");
      }}
      let odoHtml = "";
      if (b.odo_crop) {{
        odoHtml = `<div class="odo"><img src="${{b.odo_crop}}" loading="lazy"/><div class="lab">odo crop @ ${{b.angle_bin}}°</div></div>`;
      }}
      // 10 guide lines at 1/11..10/11 height — always 1 CSS px, not source-res thickness
      let guideHtml = "";
      for (let gi = 1; gi <= 10; gi++) {{
        const pct = (gi * 100 / 11).toFixed(4);
        guideHtml += `<i style="top:${{pct}}%"></i>`;
      }}
      c.innerHTML = `<div class="deskew-wrap"><img src="${{b.path}}" loading="lazy"/><div class="deskew-guides">${{guideHtml}}</div></div>${{odoHtml}}
        <div class="lab">deskew bin ${{b.angle_bin}}° (raw mean ${{Number(b.angle_raw_mean).toFixed(3)}})
        <br/><span class="hint">${{(b.methods||[]).join(", ")}}</span></div>`;
      c.style.cursor = "pointer";
      c.onclick = () => {{
        const s = loadNew();
        s[r.id] = {{
          choice: "best",
          angle_bin: b.angle_bin,
          angle_raw_mean: b.angle_raw_mean,
          file: r.file,
          set: r.set,
          methods_agree: b.methods,
          ts: new Date().toISOString()
        }};
        saveNew(s);
        render();
      }};
      prev.appendChild(c);
    }}
    div.appendChild(prev);

    const act = document.createElement("div");
    act.className = "actions";
    const none = document.createElement("button");
    none.textContent = "None of these are good";
    none.onclick = () => {{
      const s = loadNew();
      s[r.id] = {{ choice: "none", file: r.file, set: r.set, ts: new Date().toISOString() }};
      saveNew(s);
      render();
    }};
    const clear = document.createElement("button");
    clear.textContent = "Clear new pick (revert to prior)";
    clear.onclick = () => {{
      const s = loadNew();
      delete s[r.id];
      saveNew(s);
      render();
    }};
    act.appendChild(none);
    act.appendChild(clear);
    div.appendChild(act);
    root.appendChild(div);
  }}
  updateProgress();
}}

document.getElementById("btn-export").onclick = () => {{
  const neu = loadNew();
  const out = {{
    exported_at: new Date().toISOString(),
    source: "deskew-angle-review phase2",
    selections_new: neu,
    ground_truth_angles: {{}}
  }};
  for (const r of DATA.rows) {{
    const eff = effective(r);
    if (!eff) continue;
    if (eff.choice === "best") {{
      out.ground_truth_angles[r.file] = {{
        status: "human_selected",
        gt_angle: eff.angle_raw_mean != null ? Number(eff.angle_raw_mean) : Number(eff.angle_bin),
        angle_bin: Number(eff.angle_bin),
        set: r.set,
        selection_source: eff.source || "new"
      }};
    }} else if (eff.choice === "none") {{
      out.ground_truth_angles[r.file] = {{ status: "none_good", set: r.set, selection_source: eff.source || "new" }};
    }}
  }}
  const blob = new Blob([JSON.stringify(out, null, 2)], {{type: "application/json"}});
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = "deskew_angle_selections_phase2.json";
  a.click();
}};

document.getElementById("btn-next").onclick = () => {{
  for (const r of DATA.rows) {{
    if (!effective(r)) {{
      document.getElementById("row-" + r.id)?.scrollIntoView({{behavior:"smooth", block:"start"}});
      return;
    }}
  }}
  alert("All rows have a selection (prior or new)");
}};

render();
</script>
</body>
</html>
"""
    (out_dir / "index.html").write_text(html)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--phase1-out", type=Path, required=True)
    ap.add_argument(
        "--selections",
        type=Path,
        default=Path(
            "/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/deskew_angle_selections.json"
        ),
    )
    ap.add_argument("--out", type=Path, default=None)
    args = ap.parse_args()

    phase1 = args.phase1_out
    p1 = json.loads((phase1 / "results.json").read_text())
    priors = load_prior(args.selections)

    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    out = args.out or phase1.parent / f"deskew-angle-review-phase2-{stamp}"
    out.mkdir(parents=True, exist_ok=True)
    prev_dir = out / "previews"
    prev_dir.mkdir(exist_ok=True)

    rows_out: list[dict] = []
    methods_order: list[str] = []
    dash_bins: dict[str, list] = {}

    # Sort key: prior angle (or vote from recompute later) — first pass compute all then sort
    computed: list[dict] = []

    for r0 in p1["rows"]:
        if r0.get("error"):
            computed.append({**r0, "methods": [], "bin_previews": [], "unique_bins": []})
            continue
        set_name, filename = r0["set"], r0["file"]
        found = find_heat(phase1, set_name, filename)
        if not found:
            print("missing heat", set_name, filename)
            continue
        heat_path, meta = found
        hw, hh = int(meta["heat_w"]), int(meta["heat_h"])
        heat = np.frombuffer(heat_path.read_bytes(), dtype=np.float32)[: hw * hh].reshape(hh, hw)

        t0 = time.perf_counter()
        results = run_all_estimators(heat)
        est_ms = (time.perf_counter() - t0) * 1000
        if not methods_order:
            methods_order = [x.method for x in results]

        # load image
        src = Path(r0["path"]) if r0.get("path") else PHOTOS / set_name / filename
        if not src.is_file():
            src = PHOTOS / set_name / filename
        bgr = cv2.imread(str(src), cv2.IMREAD_COLOR)
        if bgr is None:
            mono = cv2.imread(str(src), cv2.IMREAD_GRAYSCALE)
            if mono is None:
                print("load fail", src)
                continue
            bgr = cv2.cvtColor(mono, cv2.COLOR_GRAY2BGR)

        pid = r0["id"]
        prev_sub = prev_dir / pid
        prev_sub.mkdir(parents=True, exist_ok=True)
        cv2.imwrite(str(prev_sub / "orig.jpg"), make_preview(bgr), [int(cv2.IMWRITE_JPEG_QUALITY), 85])

        by_bin: dict[float, list] = {}
        methods_out = []
        for res in results:
            b = bin_half_deg(res.angle_raw)
            methods_out.append(
                {
                    "method": res.method,
                    "angle_raw": res.angle_raw,
                    "angle_bin": b,
                    "time_ms": res.time_ms,
                    "notes": res.notes,
                }
            )
            by_bin.setdefault(b, []).append(res)

        bin_previews = []
        for b in sorted(by_bin.keys()):
            raws = [x.angle_raw for x in by_bin[b]]
            raw_mean = float(np.mean(raws))
            # Shrink first, then 1px guides on the preview canvas.
            # PNG: JPEG destroys pure 1px yellow (even at quality 100).
            desk = draw_yellow_guides(make_preview(rotate_bgr(bgr, raw_mean)))
            fn = f"deskew_bin_{b:.1f}.png".replace("-", "m")
            cv2.imwrite(str(prev_sub / fn), desk)
            # drop stale JPEG previews if any
            stale = prev_sub / fn.replace(".png", ".jpg")
            if stale.is_file():
                stale.unlink()
            # odo crop path placeholder (filled after device pull)
            odo_rel = f"previews/{pid}/odo_bin_{b:.1f}.jpg".replace("-", "m")
            odo_path = prev_sub / f"odo_bin_{b:.1f}.jpg".replace("-", "m")
            bp = {
                "angle_bin": b,
                "angle_raw_mean": raw_mean,
                "path": f"previews/{pid}/{fn}",
                "methods": [x.method for x in by_bin[b]],
            }
            if odo_path.is_file():
                bp["odo_crop"] = odo_rel
            bin_previews.append(bp)

        vote = next((m for m in methods_out if m["method"].startswith("vote_")), None)
        sort_angle = None
        pr = priors.get(filename)
        if pr and pr.get("choice") == "best" and pr.get("angle_bin") is not None:
            sort_angle = float(pr["angle_bin"])
        elif vote:
            sort_angle = float(vote["angle_raw"])
        else:
            sort_angle = 0.0

        row = {
            "id": pid,
            "index": r0.get("index"),
            "set": set_name,
            "file": filename,
            "path": str(src),
            "source_w": r0.get("source_w"),
            "source_h": r0.get("source_h"),
            "heat_w": hw,
            "heat_h": hh,
            "det_ms": r0.get("det_ms"),
            "est_total_ms": est_ms,
            "methods": methods_out,
            "unique_bins": sorted(by_bin.keys()),
            "preview_orig": f"previews/{pid}/orig.jpg",
            "bin_previews": bin_previews,
            "sort_angle": sort_angle,
            "production_candidate": vote,
        }
        computed.append(row)

        if set_name == "dash":
            dash_bins[filename] = [
                {
                    "angle_bin": bp["angle_bin"],
                    "angle_raw_mean": bp["angle_raw_mean"],
                    "methods": bp["methods"],
                    "preview_id": pid,
                }
                for bp in bin_previews
            ]

        print(f"{set_name}/{filename} bins={row['unique_bins']} vote={vote and round(vote['angle_raw'],2)}")

    # Order: most negative → most positive
    computed.sort(key=lambda r: (r.get("sort_angle") is None, r.get("sort_angle") or 0.0, r.get("file") or ""))
    for i, r in enumerate(computed, 1):
        r["index"] = i

    rows_out = computed
    (out / "results.json").write_text(
        json.dumps(
            {
                "generated": datetime.now().isoformat(),
                "phase": 2,
                "phase1_source": str(phase1),
                "methods": methods_order,
                "notes": (
                    "Dropped soft_pca and proj. No aspect-ratio filters. "
                    "vote_median_hough_cpp_med is the production-style single output (cross-check). "
                    "Ordered by prior/vote angle most-negative to most-positive."
                ),
                "rows": rows_out,
            },
            indent=2,
        )
    )
    with (out / "results.jsonl").open("w") as f:
        for r in rows_out:
            f.write(json.dumps(r) + "\n")

    (out / "dash_bins_for_device.json").write_text(
        json.dumps(
            {
                "generated": datetime.now().isoformat(),
                "deskew_scale": 2048,
                "instruction": "For each file, for each angle: deskew by angle_raw_mean, align, export odo crop JPEG",
                "files": dash_bins,
            },
            indent=2,
        )
    )
    (out / "priors.json").write_text(json.dumps(priors, indent=2))

    build_html(out, rows_out, methods_order, priors)
    (out / "README.md").write_text(
        f"""# Deskew angle review phase 2

{datetime.now().isoformat()}

## Open

```bash
cd {out}
python3 -m http.server 8765
# http://127.0.0.1:8765/index.html
```

## Device odo crops (dash)

1. Push manifest:
   `adb push dash_bins_for_device.json /sdcard/Android/data/com.davidlang.vehicleexpensesautomated/files/deskew_odo_export/`
2. Push dash photos to `…/files/dash_photos/` (same basenames as manifest keys).
3. Deep link (auto-run):
   `adb shell am start -a android.intent.action.VIEW -d 'vehicleexpenses://experiment/odo-export?auto=1'`
   Or button: **Deskew odo crop export (dash bins)** on Heatmap Stage screen.
4. Pull crops:
   `adb pull …/files/deskew_odo_export/crops/ /tmp/crops/`
   Merge into `previews/<preview_id>/odo_bin_<bin>.jpg` (bin label uses `m` for minus).
5. Re-run phase2 (or a merge script) so `results.json` gets `odo_crop` paths; refresh HTML.

## Prior selections

Loaded from `{args.selections}` — yellow border. No new click keeps prior on export.
"""
    )
    print(f"\nDone → {out}")
    print(f"HTML {out / 'index.html'}")
    print(f"Device manifest {out / 'dash_bins_for_device.json'} ({len(dash_bins)} dash files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
