# Obsolete: drop PP-OCRv4 / v5 det; QF = G--; prod-only columns

**Status:** Decision recorded at tag `obsolete-p4-p5-det` (tree still has v4 in APK). Following commits on this branch move nbs out of APK, drop p4 columns / O/N/W, and switch QF to G--.

**Annotated tag (peel this tree — last with v4 in APK + QF G4-vjump + 18-col p4):** `obsolete-p4-p5-det`  
**Commit (tag peel):** `4e9b0f5c35316619744062db37fdbbb62f0863af`  
**Subject:** `CODE LANDED: pump-det-discover-dump-button`

## Why

Start-seed on discover dumps (center in GT **or** ≥50% of box inside GT; H/GT ≤ 1.25; conf ≥ 0.20; **no** H_MIN) on 332 scored fields: p4 311 / prod 311. Unique p4 wins that were still trusted are rows **67** and **78** — user: horrid anyway → **fewer unique losses if prod-only**. Remaining p4-only extra is already-`?` dng cost. Prod-only keeps five trusted unique fields p4 never starts. Shipping a second det (~5 MB/ABI) and 224+1024 for those is not worth it.

| Model | Why gone |
|-------|----------|
| **v5 mobile** | Already unscheduled: too many 0-box cells (`EXPERIMENT_DET_MODELS.md`). Never in APK. |
| **v4/v5 server** | Never scheduled (~1/10 speed). Host-only unscheduled. |
| **v4 mobile (p4)** | Was in APK + QF + 11 pump columns + alignment O/N/W. Start-seed parity with product after H_MIN drop; unique p4 wins are 67/78 (horrid) + already-`?`. |

## Dumps (start-seed, 2026-08-24)

| Device | Path |
|--------|------|
| Phone (fp16) | `latest-report/pump_det_boxes_2026-08-24_20-50-04_10.2.0.213_37185` |
| Tablet (fp32) | `latest-report/pump_det_boxes_2026-08-24_20-49-36_emulator-5554` |

168 photos each. Four recipes at dump time: p4-deskew / p4-rot / prod-deskew / prod-rot.

## p4-only vs prod-only

**p4-only (6)** — prod never starts; p4 does somewhere:

| # | file | field | GT | trusted? |
|---|------|-------|-----|----------|
| 1 | `PXL_20221128_172956178.dng` | cost | `90.03?` | already `?` |
| 2 | `PXL_20231008_001628193.dng` | cost | `bad ignore?` | already `?` |
| 3 | `PXL_20231008_001628193.dng` | vol | `bad ignore?` | already `?` |
| 4 | `PXL_20240928_000604781.jpg` | vol | `13.017` | yes — user **67, horrid** |
| 5 | `PXL_20241230_191439866.jpg` | cost | `19.86` | yes — user **78, horrid** |
| 6 | `PXL_20250802_220227685.jpg` | vol | `bad ignore?` | already `?` |

**prod-only (6)** — p4 never starts; prod does:

| # | file | field | GT |
|---|------|-------|-----|
| 1 | `PXL_20221029_003255537.dng` | cost | `66.37` |
| 2 | `PXL_20230902_175948030.jpg` | vol | `5.059` |
| 3 | `PXL_20241230_191439866.jpg` | vol | `3.010` |
| 4 | `PXL_20250703_032207597.jpg` | cost | `149.75` |
| 5 | `PXL_20250703_032207597.jpg` | vol | `33.286` |
| 6 | `PXL_20260702_220704854.jpg` | cost | `47.47?` (already untrusted) |

Trusted unique after dropping 67/78: prod-only keeps **5**; p4-only remaining is dng `90.03?`.

GT `?` applied on this drop: `13.017` → `13.017?`, `19.86` → `19.86?`. dng `90.03?` kept. jpg sibling `PXL_20221128_172956178.jpg` cost `90.03` kept (prod **does** seed that file). Do not mark `fuel_1787094571952.jpg` (no box GT).

## Live QF after this drop

Experiment **G--**: product **224+608**, verts **0.1/0.3/0.4/1.1**, horiz **0.5**, `createBlueAndOrangeHunksFromReds`. Not G4-vjump.

## Recovery

```text
git rev-parse obsolete-p4-p5-det^{}
# 4e9b0f5c35316619744062db37fdbbb62f0863af

git show obsolete-p4-p5-det:app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/util/OcrHarness.kt
git show obsolete-p4-p5-det:app/src/arm64/assets/paddle/exp_det_ab/PP-OCRv4_mobile_det_armv8.nb > /tmp/PP-OCRv4_mobile_det_armv8.nb
git show obsolete-p4-p5-det:app/src/x86_64/assets/paddle/exp_det_ab/PP-OCRv4_mobile_det_x86_64.nb > /tmp/PP-OCRv4_mobile_det_x86_64.nb
git show obsolete-p4-p5-det:app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/experiment/ExperimentPumpScreen.kt | grep -n 'PP-OCRv4_mobile_det\|Set G4-vjump\|Set chi2-p4\|Set ink-p4\|Set jump-p4\|Set rot-ink-p4\|Set xycut-p4'
```

Unscheduled host copies (do **not** delete): `third_party/paddle/exp_det_ab_unscheduled/` (v5 mobile, v4/v5 server; v4 mobile nbs moved here in the drop commit after this tag).

See also: `EXPERIMENT_DET_MODELS.md`, `EXPERIMENT_ALIGNMENT_SETS.md` (O/N/W dropped with this tag).
