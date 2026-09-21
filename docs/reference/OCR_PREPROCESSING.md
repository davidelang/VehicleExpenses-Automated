---
type: implementation-reference
status: dynamic
ai_directive: "This is a downstream reference. It MUST be updated continuously to reflect the current state of the codebase. If you change a function or architecture described here, update this document in the same commit."
---

# OCR preprocessing

How a crop is prepared before the recognizer runs. The Quick Fill stages that call this are in [QUICK_FILL_CAMERA.md](QUICK_FILL_CAMERA.md).

## Odometer crop

Implemented in `OcrHarness.runSetJPipeline`. The crop is resized into the vehicle’s odometer buffer. Detection runs on a letterbox that fits inside 512×128.

**Raw.** Detection boxes are expanded with `expandByValleyDiagnostic` (default; threshold factor 0.40). The expanded rectangles are clustered into lines (`clusterRects`) and each piece is fed to `recognizeNumeric` through `RecBufferFeed.feedSourceBorderLetterbox`.

**Bin trials.** A histogram of the grayscale crop supplies valley midpoints (`findValleyMidpoints`). Each midpoint is a binary threshold. After threshold, boxes are filtered so a box fully inside another is dropped (`nestFilter`). The largest remaining box is recognized the same way. A trial with no stroke histogram is skipped.

`OdometerOcrUtils.pickBestOdometer` then chooses among the Raw string and the bin-trial strings. It keeps digit-only texts, prefers the vehicle’s digit count (plus one when a rollover is allowed), and prefers a string that came from a bin trial when both lengths match.

## Pump rectangles

Implemented in `PumpCostVolUtils.ocrPumpRectsAsisAndDigits`. Each rectangle is copied into the rec buffer with `RecBufferFeed.feedSourceBorderHeightStrip` at height 48 and width at most 320.

The same pixels are read twice:

- `recognize` — general text (as-is), with per-character probabilities
- `recognizeNumericDecimal` — digits and a decimal point, with its own probabilities

Rectangles smaller than 2×2 pixels are recorded as `?` and not recognized. Cleaning of those strings is `pumpOcrCleanAndProbs`.

Classification of the pair of strings is [PUMP_CLASSIFICATION.md](PUMP_CLASSIFICATION.md).
