# Pump Cost/Volume Classifier Specification

**Authority:** `docs/specs/PUMP_COST_VOLUME_CLASSIFIER_SPEC.md`  
**Implementation:** `ExperimentPumpScreen.kt` — `classifyCostVolFromBoxOcr`, `buildRedBoxCandidates`, `getFinal`  
**Related plan:** `complete-real-4box-per-column-wiring-20260619-plan.md`

## Purpose

Classify pump display **cost** and **volume** from per-column top-4 red-box OCR candidates. Each experiment column (Sets A–G, Paddle and ML where applicable) runs classification **independently** using only that column's pruned top-4 box crops and OCR texts.

## Per-Column Rule (Mandatory)

1. After cross-scale filter and prune-to-4, each proc (`procA`…`procG`) has at most **4** red boxes in `pdHunksRawTotal`.
2. Per-box OCR (as-is + digits-only) is computed **before** `getFinal` for that proc only.
3. `buildRedBoxCandidates(pdHunksRawTotal, asisList, digitsList)` attaches each box's rect and OCR texts.
4. `getFinal(..., candidates)` calls `classifyCostVolFromBoxOcr(candidates)` when non-empty; legacy geometric pairing is fallback only.
5. No cross-column data mixing: each of ~8 column/engine invocations uses its local lists only.

## OCR Data Sources by Set

| Set | OCR source rects | Notes |
|-----|------------------|-------|
| A   | Raw red boxes    | Direct red-box OCR; Paddle + ML `getFinal` both use same `aCands` |
| B,C,F | Retracted blue (from expanded reds) | `computeRetractedBluePixelRects()` |
| D,E,G | Custom blue (20% vert, 50% horiz) | `createBlueAndOrangeHunksFromReds()` blue hunk rects |

OCR pipeline per rect: crop → resize to 48px tall, width multiple of 32 (max 320) → `recognize` (as-is) and `recognizeNumericDecimal` (digits).

## Classification Algorithm

Input: `List<RedBoxOcrCandidate>` where each has `asis`, `digits`, and `rect`.

### Parse

For each candidate, parse `digits` if non-empty else `asis`:
- Extract digits and `.` only
- `value` = float or 0
- `decimalPlaces` = chars after `.` or 0

### Scoring (per candidate)

**Cost score:**
- +12 if decimalPlaces == 2
- +8 if value > 20
- +2 if decimalPlaces > 0

**Volume score:**
- +12 if decimalPlaces == 3
- +6 if 0 < value < 60
- +2 if decimalPlaces > 0

Select highest-scoring candidate for cost and separately for volume.

### Output strings

- `cost` = chosen cost candidate's digits (or asis if digits empty)
- `vol` = chosen volume candidate's digits (or asis if digits empty)

### Decimal repair (volume)

When cost candidate has exactly 2 decimal places and volume digits string length ≥ 4:
- Insert decimal: `digits[0..n-4] + "." + digits[n-3..]` (repair missing decimal in volume)

### Crops

- `costB64` = snapshot of **cost candidate's** rect
- `volB64` = snapshot of **volume candidate's** rect (may differ from cost)

## Verification

- Grep: no `List(pdHunksRawTotal.size) { "" }` for `*Asis`/`*Digits` at candidate build sites
- Real `ocrPumpRectsAsisAndDigits` or equivalent assigned before every `getFinal` call
- `pathResults` populated via classify path when candidates non-empty
- Spec file present at this path

## Non-Goals

Red detection, pruning logic, HTML report structure, native engines, alignment experiments.