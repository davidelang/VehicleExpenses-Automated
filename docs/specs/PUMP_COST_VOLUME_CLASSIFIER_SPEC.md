# Pump Cost/Volume Classifier Specification

**Authority:** `docs/specs/PUMP_COST_VOLUME_CLASSIFIER_SPEC.md`  
**Implementation:** `ExperimentPumpScreen.kt` — `classifyCostVolFromBoxOcr`, `buildRedBoxCandidates`, `getFinal`, `ocrPumpRectsAsisAndDigits`  
**Related plans:** `complete-real-4box-per-column-wiring-20260619-plan.md`, `fix-4box-report-issues-20260619-plan.md`, `fix-remaining-report-issues-20260619-plan.md`, `fix-classifier-numeric-only-values-asis-golden-yband-20260619-plan.md`, `fix-pump-distinct-cost-volume-candidates-and-clean-values-20260619-plan.md`, `fix-pump-probs-decimal-cleaning-overlap-grouping-v2-20260619-plan.md`

## Purpose

Classify pump display **cost** and **volume** from per-column top-4 red-box OCR candidates. Each experiment column (Sets A–G, Paddle and ML where applicable) runs classification **independently** using only that column's pruned top-4 box crops and OCR texts.

## Per-Column Rule (Mandatory)

1. After cross-scale filter and prune-to-4, each proc (`procA`…`procG`) has at most **4** red boxes in `pdHunksRawTotal`.
2. Per-box OCR (as-is + digits-only) is computed **before** `getFinal` for that proc only.
3. `buildRedBoxCandidates(boxRects, asisList, digitsList, asisProbsList, digitsProbsList)` attaches each box's **OCR rect**, clean OCR texts, and separate per-char prob strings.
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

### OCR text vs probs (mandatory split)

Immediately after each `recognize` / `recognizeNumericDecimal`:
- `cleanText = res.debugText` — stored in candidate `asis` / `digits` and used for all parse, validity, scoring, repair, and `PathResult`.
- `probStr = res.perCharProbs` — stored in `asisProbs` / `digitsProbs`; used only for correctness likelihood scoring.
- **Probs never appear in strings used for parse, digitCount, decimal repair, or string equality.**
- `pd_ocr_html` diagnostic dumps reconstruct `text [probs:xxx]` via `pumpOcrDumpText` for analysis only.

## Classification Algorithm

Input: `List<RedBoxOcrCandidate>` where each has `asis`, `digits`, `asisProbs`, `digitsProbs`, and `rect`.

### Candidate filter (mandatory)

1. Apply `cleanDecimal(digits)` — silently strip leading/trailing `.` (noise).
2. **Exclude** candidates where cleaned string has **≥ 2 internal `.`** (strong bad-OCR signal, e.g. `.13.004`).
3. Only candidates with **≥ 2 digit characters** in cleaned `digits` are eligible.

Full-text `asis` results are **never** used as value candidates.

### Golden word Y-band handling

Full-text `asis` is consulted **only** to detect golden words (`$`, `/gal`, `gal`, etc.) and obtain their `rect.top` Y coordinate as a **band indicator** for nearby numeric results.

- `$` and similar golden words are **not** field values; they mark the Y row of the associated numeric-only result.
- When golden words are present, their Y positions boost the **cost score** of the closest eligible **digits** candidate (existing Y-distance bonus).

### Parse

For each eligible candidate, parse **cleaned** `digits` only:
- Extract digits and `.` only
- `value` = float or 0
- `decimalPlaces` = chars after `.` or 0

### Scoring (per eligible candidate)

**Cost score:**
- +12 if decimalPlaces == 2
- +8 if value > 20
- +2 if decimalPlaces > 0
- +5 if clean value already contains a decimal
- +`(probCorrectness(digitsProbs) * 20)` — probs indicate correctness, not role
- +Y-row bonus when golden `$` present: up to +20 for closest Y match

**Volume score:**
- +12 if decimalPlaces == 3
- +6 if 0 < value < 60
- +1 if 3.0 ≤ value ≤ 30.0 (light bias for typical gallon range)
- +2 if decimalPlaces > 0
- +5 if clean value already contains a decimal
- +`(probCorrectness(digitsProbs) * 20)`

### Overlap-based clustering for role assignment

When candidates have rects:
1. Pick seed (highest combined base score).
2. Form **cluster** = all boxes with **significant Y-overlap** to seed (> 50% of preferred/seed rect height).
3. Within cluster: pick best value using `probCorrectness` + decimal presence bonus (decimal worth more).
4. Assign cluster best to **one role** (cost or vol) using base scores + prob-weighted cost-vs-vol likelihood.
5. **Second role** from boxes that do **not** significantly overlap the first cluster's preferred rect.
6. A box overlapping cost heavily but volume lightly joins the cost cluster.

Fallback to simple argmax when no rects available.

### Distinct cost/volume (mandatory)

When ≥2 eligible candidates exist:
1. Cost and volume must come from **different** `RedBoxOcrCandidate` objects when possible.
2. **Post-formation guard:** after role-conditional decimal repair, if `cost` and `vol` strings are equal, re-pick from non-overlapping pool; if still equal, set the second role to `"N/A"`.

### Decimal repair (role-conditional, post-assignment)

Only **after** a value has been assigned to a specific role:
- If cleaned value already has a good decimal, use as-is.
- Else repair using role-specific places: **2** for cost, **3** for volume.
- Strongly prefer (via scoring bonus) versions that already have a clean decimal.

**Removed:** unconditional vol repair based on cost's `decimalPlaces == 2`.

### Output strings

- `cost` and `vol` = **clean pure digit strings** — no probs suffix ever in `PathResult`
- Full OCR text with probs remains in `pd_ocr_html` / debug dumps only
- **Post-selection enforcement:** if final `cost` or `vol` string has fewer than 2 digits, set that field to `"N/A"`

### Crops

- `costB64` = snapshot of **cost candidate's** OCR rect
- `volB64` = snapshot of **volume candidate's** OCR rect (may differ from cost)

## HTML Report (first column)

- Metadata in the photo-info column (first `td`) is **whitelist-only**: `t_total_flow_ms`, `img_w`, `img_h` (values ≤ 100 chars).
- All other timing (`t_*`), counts (`n_*`), tilt, redbox data, and diagnostics remain in `branch.metadata` for JSON export only.
- Per-red C/E column HTML shows summary text only (h/w/area); full base64 and JSON remain in metadata/JSON export.

## Verification

- Grep: `pumpOcrCleanAndProbs` — probs split immediately after OCR
- Grep: `asisProbs` / `digitsProbs` on `RedBoxOcrCandidate`
- Grep: `cleanDecimal` / `hasBadInternalDecimals` in classifier path
- Grep: `probCorrectness` in scoring
- Grep: `significantYOverlap` / `clusterOf` for overlap grouping
- Grep: `repairDecimalForRole` — role-conditional repair only
- Grep: `pumpOcrDumpText` in `pd_ocr_html` builders
- Grep: no `substringBefore(" [")` needed in PathResult (clean text at source)
- Grep: `buildRedBoxCandidates` called with ocr rect lists (not `pdHunksRawTotal`)
- `pathResults` populated via classify path when candidates non-empty
- Spec file present at this path

## Non-Goals

Red detection, pruning logic, native engines, alignment experiments.