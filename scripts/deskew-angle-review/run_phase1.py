#!/usr/bin/env python3
"""Phase 1: multi-method deskew angles on dash+pump grid + HTML review UI.

Requires:
  - paddle_env_v3 (cv2)
  - heatmap_stage_host + prod det_x86_64.nb (host linux SO)

Usage:
  ~/miniconda3/envs/paddle_env_v3/bin/python \\
    scripts/deskew-angle-review/run_phase1.py \\
    [--limit N] [--sets dash,pump] [--resume]

Outputs under:
  dev-ai-interaction/scratch/deskew-angle-review-<timestamp>/
    results.jsonl
    results.json          # full array + method list
    previews/<id>/...
    index.html            # open in browser
    selections_export.json  # written by UI "Export GT"
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np  # used in process_one / estimators

HERE = Path(__file__).resolve().parent
VE = HERE.parents[1]  # agent-4
SCRATCH_ROOT = Path("/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/scratch")
PHOTOS = Path("/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/research/photos")
HOST_OUT = VE / "third_party/paddle/tests/heatmap_stage_host/out"
HOST_BIN = HOST_OUT / "heatmap_stage_host"
DET_NB = VE / "app/src/main/assets/paddle/prod_u8fp32_u8/det_x86_64.nb"
EXISTING_GT = Path(
    "/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/ground_truth_angles.json"
)

sys.path.insert(0, str(HERE))
from estimators import bin_half_deg, run_all_estimators  # noqa: E402


def load_mono(path: Path) -> np.ndarray:
    """Load image as uint8 mono (OpenCV). DNG via imread may fail — try unchanged."""
    img = cv2.imread(str(path), cv2.IMREAD_GRAYSCALE)
    if img is not None:
        return img
    # fallback color
    bgr = cv2.imread(str(path), cv2.IMREAD_COLOR)
    if bgr is None:
        raise RuntimeError(f"failed to load {path}")
    return cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)


def write_pgm(path: Path, mono: np.ndarray) -> None:
    h, w = mono.shape
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as f:
        f.write(f"P5\n{w} {h}\n255\n".encode("ascii"))
        f.write(np.ascontiguousarray(mono).tobytes())


def run_det_heat(mono_pgm: Path, heat_f32: Path, threads: int = 2) -> dict:
    env = os.environ.copy()
    env["LD_LIBRARY_PATH"] = f"{HOST_OUT}:{env.get('LD_LIBRARY_PATH', '')}"
    cmd = [
        str(HOST_BIN),
        "--det",
        str(DET_NB),
        "--image",
        str(mono_pgm),
        "--scales",
        "2048",
        "--dump-heat",
        str(heat_f32),
        "--threads",
        str(threads),
        "--product-path",
        "uint8_fp32_u8",
    ]
    t0 = time.perf_counter()
    p = subprocess.run(cmd, capture_output=True, text=True, env=env, timeout=300)
    det_ms = (time.perf_counter() - t0) * 1000
    if p.returncode != 0:
        raise RuntimeError(f"det failed rc={p.returncode}: {p.stderr[-500:]}")
    meta_path = Path(str(heat_f32) + ".json")
    meta = json.loads(meta_path.read_text()) if meta_path.exists() else {}
    meta["det_ms"] = det_ms
    # parse last stdout line for mass
    for line in reversed(p.stdout.strip().splitlines()):
        if line.startswith("{"):
            try:
                meta["host_json"] = json.loads(line)
            except json.JSONDecodeError:
                pass
            break
    return meta


def load_heat(path: Path, h: int, w: int) -> np.ndarray:
    arr = np.frombuffer(path.read_bytes(), dtype=np.float32)
    return arr[: h * w].reshape(h, w).copy()


def rotate_bgr(bgr: np.ndarray, angle_deg: float) -> np.ndarray:
    """Deskew warp matching the app.

    App: Android Matrix.postRotate(-deskewAngle) — Android positive = clockwise.
    OpenCV getRotationMatrix2D positive = counter-clockwise.
    So OpenCV must use +deskewAngle to match postRotate(-deskewAngle).
    """
    h, w = bgr.shape[:2]
    M = cv2.getRotationMatrix2D((w / 2.0, h / 2.0), float(angle_deg), 1.0)
    return cv2.warpAffine(bgr, M, (w, h), flags=cv2.INTER_LINEAR, borderValue=(0, 0, 0))


def make_preview(bgr: np.ndarray, max_w: int = 480) -> np.ndarray:
    h, w = bgr.shape[:2]
    if w <= max_w:
        return bgr
    nh = int(h * (max_w / w))
    return cv2.resize(bgr, (max_w, nh), interpolation=cv2.INTER_AREA)


def collect_photos(sets: list[str], limit: int | None) -> list[tuple[str, Path]]:
    out: list[tuple[str, Path]] = []
    for s in sets:
        d = PHOTOS / s
        if not d.is_dir():
            continue
        for p in sorted(d.iterdir()):
            if p.suffix.lower() in {".jpg", ".jpeg", ".png", ".dng", ".webp"}:
                out.append((s, p))
    if limit:
        out = out[:limit]
    return out


def build_html(out_dir: Path, rows: list[dict], methods: list[str]) -> None:
    html_path = out_dir / "index.html"
    # Embed data as JSON
    data_js = json.dumps({"rows": rows, "methods": methods}, separators=(",", ":"))
    # existing GT hints
    gt = {}
    if EXISTING_GT.exists():
        try:
            gt = json.loads(EXISTING_GT.read_text())
        except Exception:
            gt = {}
    gt_js = json.dumps(gt, separators=(",", ":"))

    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<title>Deskew angle review — phase 1</title>
<style>
  :root {{ font-family: system-ui, sans-serif; }}
  body {{ margin: 0; background: #111; color: #eee; }}
  header {{ position: sticky; top: 0; background: #1a1a1a; padding: 12px 16px;
    border-bottom: 1px solid #333; z-index: 10; display: flex; gap: 16px; flex-wrap: wrap; align-items: center; }}
  header button {{ padding: 8px 14px; cursor: pointer; }}
  #progress {{ color: #8cf; }}
  .row {{ border-bottom: 1px solid #333; padding: 16px; }}
  .row.done-best {{ background: #0a2010; }}
  .row.done-none {{ background: #20100a; }}
  .meta {{ font-size: 13px; color: #aaa; margin-bottom: 8px; }}
  .angles {{ font-family: ui-monospace, monospace; font-size: 12px; margin: 8px 0;
    white-space: pre-wrap; background: #1e1e1e; padding: 8px; border-radius: 4px; }}
  .previews {{ display: flex; flex-wrap: wrap; gap: 10px; align-items: flex-start; }}
  .card {{ background: #1e1e1e; border: 1px solid #444; border-radius: 6px; padding: 6px;
    max-width: 500px; }}
  .card img {{ display: block; max-width: 480px; height: auto; background: #000; }}
  .card .lab {{ font-size: 12px; margin-top: 4px; color: #ccc; }}
  /* 1 CSS-px guides; difference blend so they stay visible on white LCD / dark bg */
  .deskew-wrap {{ position: relative; display: block; max-width: 480px; line-height: 0; }}
  .deskew-wrap > img {{ width: 100%; max-width: 480px; height: auto; display: block; }}
  .deskew-guides {{ pointer-events: none; position: absolute; left: 0; top: 0; right: 0; bottom: 0; z-index: 2; }}
  .deskew-guides i {{ position: absolute; left: 0; right: 0; height: 1px; margin: 0; padding: 0;
    background: #fff; mix-blend-mode: difference; display: block; }}
  .card.selected {{ border-color: #4c8; box-shadow: 0 0 0 2px #4c8; }}
  .actions {{ margin-top: 10px; display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }}
  .actions button {{ padding: 6px 12px; cursor: pointer; }}
  .hint {{ color: #888; font-size: 12px; }}
  a {{ color: #8cf; }}
</style>
</head>
<body>
<header>
  <strong>Deskew angle review (phase 1)</strong>
  <span id="progress">0 / 0 reviewed</span>
  <button type="button" id="btn-export">Export selections JSON</button>
  <button type="button" id="btn-skip-done">Jump to next unreviewed</button>
  <span class="hint">Click a deskewed preview to pick that angle as best, or mark None good.
    1px horizontal guides (difference blend) on deskew cards. Prefer phase 2 at :8766 for slim methods + odo.</span>
</header>
<div id="root"></div>
<script>
const DATA = {data_js};
const EXISTING_GT = {gt_js};
const STORAGE_KEY = "deskew-angle-review-v1-" + location.pathname;

function loadSel() {{
  try {{ return JSON.parse(localStorage.getItem(STORAGE_KEY) || "{{}}"); }}
  catch (e) {{ return {{}}; }}
}}
function saveSel(s) {{
  localStorage.setItem(STORAGE_KEY, JSON.stringify(s));
  updateProgress();
}}
function updateProgress() {{
  const s = loadSel();
  const n = DATA.rows.length;
  let done = 0;
  for (const r of DATA.rows) if (s[r.id]) done++;
  document.getElementById("progress").textContent = done + " / " + n + " reviewed";
}}

function render() {{
  const root = document.getElementById("root");
  root.innerHTML = "";
  const sel = loadSel();
  for (const r of DATA.rows) {{
    const div = document.createElement("div");
    div.className = "row";
    div.id = "row-" + r.id;
    const choice = sel[r.id];
    if (choice && choice.choice === "best") div.classList.add("done-best");
    if (choice && choice.choice === "none") div.classList.add("done-none");

    const gtHint = EXISTING_GT[r.file];
    let gtLine = "";
    if (gtHint && typeof gtHint.gt_angle === "number") {{
      gtLine = ` | existing GT angle=${{gtHint.gt_angle.toFixed(3)}}° (${{gtHint.status||"?"}})`;
    }}

    const meta = document.createElement("div");
    meta.className = "meta";
    meta.innerHTML = `<b>#${{r.index}}</b> <code>${{r.set}}/${{r.file}}</code>
      ${{r.source_w}}x${{r.source_h}} det=${{(r.det_ms||0).toFixed(0)}}ms heat=${{r.heat_w}}x${{r.heat_h}}${{gtLine}}
      ${{choice ? " | <b>selected: " + (choice.choice==="none"?"NONE":choice.angle_bin+"°") + "</b>" : ""}}`;
    div.appendChild(meta);

    const ang = document.createElement("div");
    ang.className = "angles";
    let lines = "methods (raw °, ms):\\n";
    for (const m of r.methods) {{
      lines += `  ${{m.method.padEnd(22)}} raw=${{m.angle_raw.toFixed(4).padStart(9)}}  bin=${{m.angle_bin.toFixed(1).padStart(5)}}  ${{m.time_ms.toFixed(1).padStart(7)}}ms  ${{m.notes||""}}\\n`;
    }}
    lines += "unique bins for previews: " + r.unique_bins.join(", ");
    ang.textContent = lines;
    div.appendChild(ang);

    const prev = document.createElement("div");
    prev.className = "previews";
    // original
    const c0 = document.createElement("div");
    c0.className = "card";
    c0.innerHTML = `<img src="${{r.preview_orig}}" loading="lazy"/><div class="lab">original</div>`;
    prev.appendChild(c0);
    for (const b of r.bin_previews) {{
      const c = document.createElement("div");
      c.className = "card";
      if (choice && choice.choice === "best" && Number(choice.angle_bin) === Number(b.angle_bin))
        c.classList.add("selected");
      let guideHtml = "";
      for (let gi = 1; gi <= 10; gi++) {{
        const pct = (gi * 100 / 11).toFixed(4);
        guideHtml += `<i style="top:${{pct}}%"></i>`;
      }}
      c.innerHTML = `<div class="deskew-wrap"><img src="${{b.path}}" loading="lazy"/><div class="deskew-guides">${{guideHtml}}</div></div>
        <div class="lab">deskew bin ${{b.angle_bin}}°
        <br/><span class="hint">methods: ${{b.methods.join(", ")}}</span></div>`;
      c.style.cursor = "pointer";
      c.onclick = () => {{
        const s = loadSel();
        s[r.id] = {{
          choice: "best",
          angle_bin: b.angle_bin,
          angle_raw_mean: b.angle_raw_mean,
          file: r.file,
          set: r.set,
          methods_agree: b.methods,
          ts: new Date().toISOString()
        }};
        saveSel(s);
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
      const s = loadSel();
      s[r.id] = {{ choice: "none", file: r.file, set: r.set, ts: new Date().toISOString() }};
      saveSel(s);
      render();
    }};
    const clear = document.createElement("button");
    clear.textContent = "Clear selection";
    clear.onclick = () => {{
      const s = loadSel();
      delete s[r.id];
      saveSel(s);
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
  const s = loadSel();
  const out = {{
    exported_at: new Date().toISOString(),
    source: "deskew-angle-review phase1",
    selections: s,
    // compact GT map: filename -> angle or null
    ground_truth_angles: {{}}
  }};
  for (const r of DATA.rows) {{
    const c = s[r.id];
    if (!c) continue;
    if (c.choice === "best")
      out.ground_truth_angles[r.file] = {{
        status: "human_selected",
        gt_angle: c.angle_raw_mean != null ? c.angle_raw_mean : c.angle_bin,
        angle_bin: c.angle_bin,
        methods_agree: c.methods_agree || [],
        set: r.set
      }};
    else if (c.choice === "none")
      out.ground_truth_angles[r.file] = {{ status: "none_good", set: r.set }};
  }}
  const blob = new Blob([JSON.stringify(out, null, 2)], {{type: "application/json"}});
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = "deskew_angle_selections.json";
  a.click();
}};

document.getElementById("btn-skip-done").onclick = () => {{
  const s = loadSel();
  for (const r of DATA.rows) {{
    if (!s[r.id]) {{
      const el = document.getElementById("row-" + r.id);
      if (el) el.scrollIntoView({{behavior: "smooth", block: "start"}});
      return;
    }}
  }}
  alert("All rows reviewed");
}};

render();
</script>
</body>
</html>
"""
    html_path.write_text(html)
    print(f"Wrote {html_path}")


def process_one(
    set_name: str,
    path: Path,
    work: Path,
    prev_dir: Path,
    index: int,
) -> dict:
    safe = re.sub(r"[^A-Za-z0-9._-]", "_", path.name)
    pid = f"{index:04d}_{set_name}_{safe}"
    sub = work / "work" / pid
    sub.mkdir(parents=True, exist_ok=True)
    prev_sub = prev_dir / pid
    prev_sub.mkdir(parents=True, exist_ok=True)

    mono = load_mono(path)
    h0, w0 = mono.shape
    # full-res mono for det feed (host scales long-edge to 2048)
    pgm = sub / "source.pgm"
    write_pgm(pgm, mono)

    heat_path = sub / "heat.f32"
    meta = run_det_heat(pgm, heat_path)
    hw = int(meta.get("heat_w", 2048))
    hh = int(meta.get("heat_h", 2048))
    heat = load_heat(heat_path, hh, hw)

    t_est0 = time.perf_counter()
    results = run_all_estimators(heat)
    est_total_ms = (time.perf_counter() - t_est0) * 1000

    # Load color (or gray→bgr) for previews — may fail on some DNG
    bgr = cv2.imread(str(path), cv2.IMREAD_COLOR)
    if bgr is None:
        bgr = cv2.cvtColor(mono, cv2.COLOR_GRAY2BGR)

    # Group by 0.5° bin
    by_bin: dict[float, list] = {}
    methods_out = []
    for r in results:
        b = bin_half_deg(r.angle_raw)
        methods_out.append(
            {
                "method": r.method,
                "angle_raw": r.angle_raw,
                "angle_bin": b,
                "time_ms": r.time_ms,
                "notes": r.notes,
            }
        )
        by_bin.setdefault(b, []).append(r)

    orig_path = prev_sub / "orig.jpg"
    cv2.imwrite(str(orig_path), make_preview(bgr), [int(cv2.IMWRITE_JPEG_QUALITY), 85])

    bin_previews = []
    for b in sorted(by_bin.keys(), key=lambda x: abs(x)):
        # mean raw among methods in this bin
        raws = [r.angle_raw for r in by_bin[b]]
        raw_mean = float(np.mean(raws))
        desk = rotate_bgr(bgr, raw_mean)
        rel = f"previews/{pid}/deskew_{b:+.1f}.jpg".replace("+", "p").replace("-", "m")
        # safer filename
        fn = f"deskew_bin_{b:.1f}.jpg".replace("-", "m")
        outp = prev_sub / fn
        cv2.imwrite(str(outp), make_preview(desk), [int(cv2.IMWRITE_JPEG_QUALITY), 85])
        bin_previews.append(
            {
                "angle_bin": b,
                "angle_raw_mean": raw_mean,
                "path": f"previews/{pid}/{fn}",
                "methods": [r.method for r in by_bin[b]],
            }
        )

    return {
        "id": pid,
        "index": index,
        "set": set_name,
        "file": path.name,
        "path": str(path),
        "source_w": w0,
        "source_h": h0,
        "heat_w": hw,
        "heat_h": hh,
        "det_ms": meta.get("det_ms"),
        "est_total_ms": est_total_ms,
        "heat_mass": int(
            meta.get("host_json", {}).get("scales", [{}])[0].get("heatmap_mass_bins1_99", -1)
        )
        if meta.get("host_json")
        else -1,
        "methods": methods_out,
        "unique_bins": sorted({m["angle_bin"] for m in methods_out}, key=lambda x: abs(x)),
        "preview_orig": f"previews/{pid}/orig.jpg",
        "bin_previews": bin_previews,
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--sets", default="dash,pump", help="comma list dash,pump")
    ap.add_argument("--limit", type=int, default=None)
    ap.add_argument("--threads", type=int, default=2)
    ap.add_argument("--out", type=Path, default=None)
    ap.add_argument("--resume", action="store_true")
    args = ap.parse_args()

    if not HOST_BIN.is_file():
        print("Missing heatmap_stage_host — run scripts/build-heatmap-stage-host.sh", file=sys.stderr)
        return 2
    if not DET_NB.is_file():
        print(f"Missing det model {DET_NB}", file=sys.stderr)
        return 2

    sets = [s.strip() for s in args.sets.split(",") if s.strip()]
    photos = collect_photos(sets, args.limit)
    if not photos:
        print("No photos found", file=sys.stderr)
        return 2

    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    out_dir = args.out or (SCRATCH_ROOT / f"deskew-angle-review-{stamp}")
    out_dir.mkdir(parents=True, exist_ok=True)
    prev_dir = out_dir / "previews"
    prev_dir.mkdir(exist_ok=True)
    jsonl_path = out_dir / "results.jsonl"

    done_files = set()
    rows: list[dict] = []
    if args.resume and jsonl_path.exists():
        for line in jsonl_path.open():
            o = json.loads(line)
            rows.append(o)
            done_files.add((o["set"], o["file"]))
        print(f"Resume: {len(rows)} already done")

    methods_order: list[str] = []
    t_all = time.perf_counter()
    with jsonl_path.open("a") as jf:
        for i, (set_name, path) in enumerate(photos, 1):
            if (set_name, path.name) in done_files:
                continue
            print(f"[{i}/{len(photos)}] {set_name}/{path.name} …", flush=True)
            try:
                row = process_one(set_name, path, out_dir, prev_dir, i)
                if not methods_order:
                    methods_order = [m["method"] for m in row["methods"]]
                rows.append(row)
                jf.write(json.dumps(row) + "\n")
                jf.flush()
                print(
                    f"  det={row['det_ms']:.0f}ms est={row['est_total_ms']:.0f}ms "
                    f"bins={row['unique_bins']}",
                    flush=True,
                )
            except Exception as e:
                err = {
                    "id": f"{i:04d}_{set_name}_{path.name}",
                    "index": i,
                    "set": set_name,
                    "file": path.name,
                    "error": str(e),
                    "methods": [],
                    "unique_bins": [],
                    "bin_previews": [],
                    "preview_orig": "",
                }
                rows.append(err)
                jf.write(json.dumps(err) + "\n")
                jf.flush()
                print(f"  ERROR {e}", flush=True)

    # stable sort by index
    rows.sort(key=lambda r: r.get("index", 0))
    (out_dir / "results.json").write_text(
        json.dumps(
            {
                "generated": datetime.now().isoformat(),
                "deskew_scale": 2048,
                "det_model": str(DET_NB),
                "photos_n": len(photos),
                "methods": methods_order,
                "notes": (
                    "Deskew long-edge 2048 host det (prod_u8fp32_u8). "
                    "Most text assumed horizontal; some components far from horizontal. "
                    "angle_raw unbinned; previews grouped by 0.5° bins."
                ),
                "rows": rows,
            },
            indent=2,
        )
    )
    build_html(out_dir, rows, methods_order)
    (out_dir / "README.md").write_text(
        f"""# Deskew angle review (phase 1)

Generated: {datetime.now().isoformat()}

## Open the UI

```bash
# from this directory:
python3 -m http.server 8765
# then browse: http://127.0.0.1:8765/index.html
```

Or open `index.html` directly (file://) — export still works.

## Files

- `results.json` / `results.jsonl` — full per-image method angles + timings
- `previews/` — original + deskewed JPEGs per 0.5° bin
- `index.html` — interactive selection
- Export from UI → `deskew_angle_selections.json` (ground-truth seed)

## Existing GT

`dev-ai-interaction/ground_truth_angles.json` (140 dash, May 2026) is shown as a hint when present.
"""
    )
    elapsed = time.perf_counter() - t_all
    print(f"\nDone {len(rows)} rows in {elapsed/60:.1f} min → {out_dir}")
    print(f"Open: {out_dir / 'index.html'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
