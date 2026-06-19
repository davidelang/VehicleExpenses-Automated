# Pump Cost/Volume Classifier Specification

**Authority:** `docs/specs/PUMP_COST_VOLUME_CLASSIFIER_SPEC.md`  
**Implementation:** `ExperimentPumpScreen.kt` — `classifyCostVolFromBoxOcr`, `buildRedBoxCandidates`, `getFinal`  
**Related plans:** `complete-real-4box-per-column-wiring-20260619-plan.md`, `fix-4box-report-issues-20260619-plan.md`, `fix-remaining-report-issues-20260619-plan.md`, `fix-classifier-numeric-only-values-asis-golden-yband-20260619-plan.md`, `fix-pump-distinct-cost-volume-candidates-and-clean-values-20260619-plan.md`

## Purpose

Classify pump display **cost** and **volume** from per-column top-4 red-box OCR candidates. Each experiment column (Sets A–G, Paddle and ML where applicable) runs classification **independently** using only that column's pruned top-4 box crops and OCR texts.

## Per-Column Rule (Mandatory)

1. After cross-scale filter and prune-to-4, each proc (`procA`…`procG`) has at most **4** red boxes in `pdHunksRawTotal`.
2. Per-box OCR (as-is + digits-only) is computed **before** `getFinal` for that proc only.
3. `buildRedBoxCandidates(boxRects, asisList, digitsList)` attaches each box's **OCR rect** (the rect list passed to `ocrPumpRectsAsisAndDigits`) and OCR texts.
4. `getFinal(..., candidates)` calls `classifyCostVolFromBoxOcr(candidates)` when non-empty; legacy geometric pairing is fallback only.
5. No cross-column data mixing: each of ~8 column/engine invocations uses its local lists only.

## OCR Data Sources by Set

| Set | OCR source rects | Candidate rect source (`buildRedBoxCandidates`) |
|-----|------------------|------------------------------------------------|
| A   | Raw red boxes (`aRedPixel`) | Same `aRedPixel` |
| B,C,F | Retracted blue (from expanded reds) | `bRetractedPixel` / `cRetractedPixel` / `fRetractedPixel` |
| D,E,G | Custom blue (20% vert, 50% horiz) | `customBluePixelD` / `customBluePixelE` / `customBluePixelG` |

**Important:** Candidate `rect` and B64 snapshot crops must come from the **OCR rect list** (blue/orange/retracted), not from raw `pdHunksRawTotal` red detection rects.

OCR pipeline per rect: crop → resize to 48px tall, width multiple of 32 (max 320) → `recognize` (as-is) and `recognizeNumericDecimal` (digits).

## Classification Algorithm

Input: `List<RedBoxOcrCandidate>` where each has `asis`, `digits`, and `rect`.

### Candidate filter (mandatory)

Only candidates with **≥ 2 digit characters in `digits`** (from `recognizeNumericDecimal`) are eligible for cost or volume selection. Full-text `asis` results are **never** used as value candidates. Label-only or single-digit numeric OCR is excluded automatically.

### Golden word Y-band handling

Full-text `asis` is consulted **only** to detect golden words (`$`, `/gal`, `gal`, etc.) and obtain their `rect.top` Y coordinate as a **band indicator** for nearby numeric results.

- `$` and similar golden words are **not** field values; they mark the Y row of the associated numeric-only result.
- When golden words are present, their Y positions boost the **cost score** of the closest eligible **digits** candidate (existing Y-distance bonus).
- No special "lone $ never a value" or "$ combined with digits" value rules — values come exclusively from `digits`.

### Parse

For each eligible candidate, parse `digits` only:
- Extract digits and `.` only
- `value` = float or 0
- `decimalPlaces` = chars after `.` or 0

### Scoring (per eligible candidate)

**Cost score:**
- +12 if decimalPlaces == 2
- +8 if value > 20
- +2 if decimalPlaces > 0
- +Y-row bonus when golden `$` present: up to +20 for closest Y match

**Volume score:**
- +12 if decimalPlaces == 3
- +6 if 0 < value < 60
- +1 if 3.0 ≤ value ≤ 30.0 (light bias for typical gallon range)
- +2 if decimalPlaces > 0

**Selection:** highest cost-score candidate for cost; highest volume-score among **remaining candidates ≠ cost candidate** for volume (fallback to sole candidate when only one valid).

### Distinct cost/volume (mandatory)

When ≥2 eligible candidates exist:
1. Cost and volume must come from **different** `RedBoxOcrCandidate` objects (different OCR rects).
2. Volume is chosen from `valids.filter { it != costCand }`, not independent argmax over all valids.
3. **Post-formation guard:** after decimal repair, if `cost` and `vol` strings are equal, re-pick volume from a different candidate; if still equal, set `vol` to `"N/A"`.

### Output strings

- `cost` and `vol` = **clean pure digit strings** from chosen candidates' `digits` (strip any ` [probs:...]` debug suffix before return)
- Full OCR text with probs remains in `pd_ocr_html` / debug dumps only — not in `PathResult` cost/vol
- **Post-selection enforcement:** if final `cost` or `vol` string has fewer than 2 digits, set that field to `"N/A"`

### Decimal repair (volume)

When cost candidate has exactly 2 decimal places and volume digits string length ≥ 4:
- Insert decimal: `digits[0..n-4] + "." + digits[n-3..]` (repair missing decimal in volume)

### Crops

- `costB64` = snapshot of **cost candidate's** OCR rect
- `volB64` = snapshot of **volume candidate's** OCR rect (may differ from cost)

## HTML Report (first column)

- Metadata in the photo-info column (first `td`) is **whitelist-only**: `t_total_flow_ms`, `img_w`, `img_h` (values ≤ 100 chars).
- All other timing (`t_*`), counts (`n_*`), tilt, redbox data, and diagnostics remain in `branch.metadata` for JSON export only.
- Per-red C/E column HTML shows summary text only (h/w/area); full base64 and JSON remain in metadata/JSON export.

## Verification

- Grep: `digitCount` / `valids` filter with `>= 2` in `classifyCostVolFromBoxOcr`
- Grep: `buildRedBoxCandidates` called with ocr rect lists (not `pdHunksRawTotal`)
- Grep: `remainingForVol` / different-cand vol selection; `cst == vlm` post-formation guard
- Grep: `substringBefore(" [")` for clean cost/vol output (no probs suffix in PathResult)
- Grep: `htmlMetaWhitelist` / `t_total_flow_ms` whitelist in `pBuildHtmlRowDynamic`
- Grep: post-selection `digitCount(cst) < 2` → `"N/A"` in `classifyCostVolFromBoxOcr`
- `pathResults` populated via classify path when candidates non-empty
- Spec file present at this path

## Non-Goals

Red detection, pruning logic, native engines, alignment experiments.