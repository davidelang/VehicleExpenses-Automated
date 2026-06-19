# Pump Cost/Volume Classifier Specification

**Authority:** `docs/specs/PUMP_COST_VOLUME_CLASSIFIER_SPEC.md`  
**Implementation:** `ExperimentPumpScreen.kt` — `classifyCostVolFromBoxOcr`, `buildRedBoxCandidates`, `getFinal`  
**Related plans:** `complete-real-4box-per-column-wiring-20260619-plan.md`, `fix-4box-report-issues-20260619-plan.md`

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

Only candidates with **≥ 2 digits** total across `digits` and `asis` are eligible for selection as cost or volume values. Label-only or single-digit candidates are excluded.

### Golden `$` label handling

- A candidate whose `asis` is exactly `"$"` (or otherwise has no digits) is a **golden label**, not a field value.
- Pure `$` must **never** be selected as cost or volume.
- When a golden `$` label is present, its Y position (`rect.top`) boosts the **cost score** of the numeric candidate on the closest row (same Y band).
- If `$` appears combined with digits in the same OCR text (e.g. `"$12.34"`), it is treated as a normal numeric candidate (≥ 2 digits).

### Parse

For each eligible candidate, parse `digits` if non-empty else `asis`:
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
- +2 if decimalPlaces > 0

Select highest-scoring eligible candidate for cost and separately for volume.

### Distinct cost/volume (mandatory)

If the same candidate wins both cost and volume and more than one eligible candidate exists, volume is reassigned to the next-best **different** candidate.

### Output strings

- `cost` = chosen cost candidate's digits (or asis if digits empty)
- `vol` = chosen volume candidate's digits (or asis if digits empty)

### Decimal repair (volume)

When cost candidate has exactly 2 decimal places and volume digits string length ≥ 4:
- Insert decimal: `digits[0..n-4] + "." + digits[n-3..]` (repair missing decimal in volume)

### Crops

- `costB64` = snapshot of **cost candidate's** OCR rect
- `volB64` = snapshot of **volume candidate's** OCR rect (may differ from cost)

## HTML Report (first column)

- Metadata in the photo-info column must exclude keys containing `redbox`, `Data`, or `json`, and values longer than 100 chars.
- Per-red C/E column HTML shows summary text only (h/w/area); full base64 and JSON remain in metadata/JSON export.

## Verification

- Grep: `digitCount` / `valids` filter with `>= 2` in `classifyCostVolFromBoxOcr`
- Grep: `buildRedBoxCandidates` called with ocr rect lists (not `pdHunksRawTotal`)
- Grep: distinct cost/vol enforcement when same candidate wins both
- Grep: strict `metaHtml` filter in `pBuildHtmlRowDynamic`
- `pathResults` populated via classify path when candidates non-empty
- Spec file present at this path

## Non-Goals

Red detection, pruning logic, native engines, alignment experiments.