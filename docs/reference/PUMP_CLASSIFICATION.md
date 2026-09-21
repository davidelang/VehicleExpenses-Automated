---
type: implementation-reference
status: dynamic
ai_directive: "This is a downstream reference. It MUST be updated continuously to reflect the current state of the codebase. If you change a function or architecture described here, update this document in the same commit."
---

# Pump cost and volume classification

How Quick Fill turns OCR boxes into a cost and a volume. This is a description of the current code, not a locked spec. Implementation: `PumpRoleBandClassifier.classify`, called from `PumpCostVolUtils.classifyCostVolFromBoxOcr`.

The older per-column A–G rules are in [../obsolete/PUMP_COST_VOLUME_CLASSIFIER_SPEC.md](../obsolete/PUMP_COST_VOLUME_CLASSIFIER_SPEC.md).

## Inputs

Each candidate (`RedBoxOcrCandidate`) has a rectangle, an as-is string, a digits string, and per-character probability strings for both. Quick Fill builds those from the expanded ink boxes. See [OCR_PREPROCESSING.md](OCR_PREPROCESSING.md).

Settings (`PumpOcrSettings`, overridable in preferences):

| Setting | Default |
|---------|---------|
| Cost/volume ratio band low | 2.0 |
| Cost/volume ratio band high | 30.0 |
| Extra vertical fraction when a text label joins a Y band | 0.15 |

Volume values above 250 (`PUMP_VOL_VALUE_MAX`) are not used as a volume.

## Steps

1. **Y bands.** Candidates whose vertical centers are close are one band (`rbClusterYBands`). Bands can merge when the gap is at most 140 px and the merged height is at most 720 px.
2. **Label lock.** A band is locked as cost or volume from the text in and near it. Cost words include this sale, sale, total, price, amount, wholesale, and `$`. Volume words include gallon, gal, volume, litre, and liter. A `$` or `/gal` is a strong lock. A lock stronger than priority 3 is kept.
3. **Role pools.** If both a cost pool and a volume pool are locked, search only inside those pools.
4. **Pair score.** Among enriched readings (cleaned digits, decimal places, probability score, cost-word score, volume-word score), pick the best cost/volume pair. The ratio of cost to volume is scored inside the configured band. A tighter band, 2.8–4.2, applies when volume is at least 12. An implied-volume penalty uses a divisor of 4.5. Decimals are repaired for the role (`repairDecimalForRole`).
5. **Fallbacks.** If the bands do not lock both roles, or either pool is empty, the same pair search runs on all candidates (`classifyImprovedCoreResult`). One leftover candidate is emitted as cost or volume from which word score is higher, and the other field is `N/A`. If that still fails, `classifyBaselineResult` runs.

Quick Fill treats `N/A` and blank as unread. If both fields are unread, the screen reports that the pump display could not be read.
