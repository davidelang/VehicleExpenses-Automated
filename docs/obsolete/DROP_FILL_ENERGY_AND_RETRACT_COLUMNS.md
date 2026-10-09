# Obsolete: drop fill, energy, and retract-vsp columns

**Status:** Decision recorded with the default pump schedule cut to 3 columns. Native fill, energy, and retract stay compiled. The processors are parked (not deleted).

**Evidence:** emulator `pump_results_2026-10-01_23-36-54` (168 photos). Good OCR: every candidate `asis` and `digits`, field label ignored. Relaxed match is `pump_deep_analysis.py` `relaxed_match`. Trusted ground truth only (315 fields).

## Fill

Fill columns paint salt-and-pepper into the look-bin (`fillSaltPepperSeed` when `virtSp` is false). The vsp twins do not.

At ink k=0, on the primary `candidates[]` pool, vsp is never the worse grade. Three fields are exact on the vsp twin and one digit short on fill:

| Row | Field | Fill column | Fill | Vsp |
|-----|-------|-------------|------|-----|
| 35 | cost | ink-color-retract and rot-color-retract | `76.51` | `76.5` |
| 51 | cost | ink-color-retract | `47.09` | `47.0` |

Across k=0..4 together, fill and vsp reach the same grade on every trusted field. A single taller crop can be softer. Each of these is already exact on vsp at k=0:

| Scale | Row | Read | Fill | Vsp |
|-------|-----|------|------|-----|
| k=1 | 35 | rot-retract cost | `7651` | `76.5` |
| k=1 | 51 | ink-retract cost | `47.09` | `47.0` |
| k=3 | 28 | rot-tight volume | `12.567` | `12.56` |
| k=3 | 51 | ink-retract cost | `470` | `4305` |
| k=4 | 27 | ink-tight volume | `20.000` | `2.000` |
| k=4 | 28 | rot-tight volume | `12.567` | `12.56` |
| k=4 | 144 | rot-retract volume | `3.565` | `565` |

**Claim:** at the official crop, and on the union of k=0..4, vsp is as good or better on every trusted field. “Always” is the column, not every single tall crop.

Parked fill processors (a later schedule would call these wrappers):

| Processor | Wrapper |
|-----------|---------|
| `procInkColorTight` | `expandColorAabbTight` |
| `procInkColorRetract` | `expandColorAabbRetract` |
| `procRotColorTight` | `expandColorOrientTight` |
| `procRotColorRetract` | `expandColorOrientRetract` |

## Energy

Best of the four vsp columns at ink k=0 against the best energy official read: same grade on 301 fields, vsp better on 12, energy better on 2. The energy-only official reads are row 37 cost `29.24` (`ink-energy-tight`) and row 137 cost `97.66` (`ink-energy-tight`).

On the 301 fields vsp already reads exactly, energy is exact on 289, one digit short on 3 (rows 3, 15, 93 cost), and a miss on 9 (rows 56, 58, 81, 90, 97, 109, 114, 124, 159 cost).

Across every candidate string on every scale, the two sides reach the same grade on all 315 trusted fields, so the vsp columns already contain the row 37 and row 137 reads at another height.

**Claim:** on the official crop energy is the worse reader on the fields where vsp is already exact, and the union of heights does not lose a trusted field.

Parked energy processors (a later schedule would call these wrappers):

| Processor | Wrapper |
|-----------|---------|
| `procInkEnergyTight` | `expandEnergyAabbTight` |
| `procInkEnergyRetract` | `expandEnergyAabbRetract` |
| `procRotEnergyTight` | `expandEnergyOrientTight` |
| `procRotEnergyRetract` | `expandEnergyOrientRetract` |

## Retract

Left and right are the side-search plan `dev-ai-interaction/plans/blue-box-side-search-20261002-1353-plan.md`: `jumpRetractH` on the ink look path uses the walked blue top and bottom. Rot already does that in `jumpOrientedOne`.

On this report, where the boxes differ by more than 8px, 19 glyphs have retract holding the stroke and tight cutting it, and those cuts are a left or right edge (largest: row 117, 145px through the 1 of 14.157). The enclosure case that side search does not fix is row 133, the dollar-sign ticks: tight clips 48px off the top and 24px off the bottom, retract holds them. The dollar sign is not a reason to keep retract. The other vertical disagreements are retract cutting ink that tight holds (34 pairs). Row 74 is that class: tight holds 50.69, retract cuts 109px off the top on ink and 108px on rot.

**Claim:** retract-vsp is retired because the left/right misses are the side-search fix, and the remaining retract-only glyph is the dollar sign.

Parked retract processors (a later schedule would call these wrappers):

| Column | Processor | Wrapper |
|--------|-----------|---------|
| `Set ink-color-retract-vsp` | `procInkColorRetractVsp` | `expandColorAabbRetractVsp` |
| `Set rot-color-retract-vsp` | `procRotColorRetractVsp` | `expandColorOrientRetractVsp` |

The ten parked processors are the four fill procs, the four energy procs, `procInkColorRetractVsp`, and `procRotColorRetractVsp`. `fillSaltPepper`, `fillSaltPepperSeed`, and the Sobel expand path stay. Tight vsp stays scheduled: `procInkColorTightVsp` / `expandColorAabbTightVsp`, `procRotColorTightVsp` / `expandColorOrientTightVsp`, plus `Set G-- (4 pass, none, calculated)`.
