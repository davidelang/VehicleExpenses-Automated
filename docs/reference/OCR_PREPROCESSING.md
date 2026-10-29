---
type: implementation-reference
status: dynamic
ai_directive: "This is a downstream reference. It MUST be updated continuously to reflect the current state of the codebase. If you change a function or architecture described here, update this document in the same commit."
---

# OCR preprocessing

How a crop is prepared before the recognizer runs. The Quick Fill stages that call this are in [QUICK_FILL_CAMERA.md](QUICK_FILL_CAMERA.md).

## Odometer crop

Implemented in `OcrHarness.runAutoFillPipeline` via `PaddleSeedOdo.readSeedRec` (paddle seed). Quick Fill and batch dash do not use Set J; that pipeline is removed. Set J remains an obsolete alignment experiment only.

The red odometer quad is the crop. Paddle seeds that overlap it are padded at `PaddleSeedOdo.SEED_VERT_PRODUCTION` (0.40), unioned, clipped to the quad, and fed with `ocrPumpFeedOriented40` (40-in-48 rec window).

**Raw.** `recognizeNumericDecimal` on that feed. Digits only.

**Bin trials.** A histogram of the fed grayscale supplies valley midpoints (`findValleyMidpoints`). Each midpoint is a binary threshold on the same feed; each binary image is recognized the same way. Empty digit text is skipped. Among trials whose minimum character probability is at least 0.40, the highest sum of probabilities wins; otherwise the highest sum overall.

`OdometerOcrUtils.pickBestOdometer` then chooses among the Raw string and the bin-trial string. It keeps digit-only texts, prefers the vehicle’s digit count (plus one when a rollover is allowed), and prefers a string that came from a bin trial when both lengths match.

## Pump rectangles

Implemented in `PumpCostVolUtils.ocrPumpRectsAsisAndDigits`. Each rectangle is copied into the rec buffer with `RecBufferFeed.feedSourceBorderHeightStrip` at height 48 and width at most 320.

The same pixels are read twice:

- `recognize` — general text (as-is), with per-character probabilities
- `recognizeNumericDecimal` — digits and a decimal point, with its own probabilities

Rectangles smaller than 2×2 pixels are recorded as `?` and not recognized. Cleaning of those strings is `pumpOcrCleanAndProbs`.

Classification of the pair of strings is [PUMP_CLASSIFICATION.md](PUMP_CLASSIFICATION.md).
