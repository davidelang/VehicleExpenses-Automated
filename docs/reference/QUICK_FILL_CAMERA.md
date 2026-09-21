---
type: implementation-reference
status: dynamic
ai_directive: "This is a downstream reference. It MUST be updated continuously to reflect the current state of the codebase. If you change a function or architecture described here, update this document in the same commit."
---

# Quick Fill camera

What the Quick Fill screen does with a dash photo, a pump photo, and the phone’s location. Entry point: `QuickFillupScreen.kt`. Capture borrows the camera YUV into `NativePaddleEngine.bufferSetA`, calls `normalizeYUV()`, and applies the camera rotation as part of deskew.

The screen has two shutter modes, switched with ↕: **odo** (dash) and **pump**. Fields can be typed when a read fails. Save writes one fuel row. Photos are held in memory until Save.

## Location

One device fix per visit, not per shutter (`CaptureLocation.captureLocationOrNull`). That fix is the row latitude and longitude and is stamped on the JPEGs.

Place name, in order:

1. Match the known-station table (`matchKnownStation`). A unique hit fills the name and address.
2. An ambiguous hit asks the user to pick a station.
3. Offline with no nearby station stops there.
4. Otherwise `LocationLookup.lookup` with `LocationLookupKind.FUEL_STATION` (network place lookup). The user can confirm or edit the result.

## Dash

`OcrHarness.runAutoFillPipeline`.

1. **Deskew.** Paddle detection heatmap angle (`calculatePaddleAngleOptimized`), then rotate by camera rotation minus that angle.
2. **Landmarks.** ML Kit on the deskewed primary (`performLandmarkDiscovery`). Short strings are dropped. Text is cleaned with `cleanLandmarkString`.
3. **Which vehicle.** `ImageAlignmentUtils.performTier1Veto` against each vehicle’s ML Kit landmark list. A vehicle is out when the photo contains a landmark that belongs to another vehicle and not to it. The remaining vehicle wins. No winner returns “Vehicle not identified”. Batch import can force a vehicle id and skip this step.
4. **Odometer crop.** The winner’s reference dash size and stored landmark JSON are loaded. `disambiguateLandmarks` then `anchorAlign` map the photo onto that reference (uniform scale and translation, `warpAffine`). The vehicle’s ICRS odometer crop is taken from the aligned primary. If the vehicle has no crop, the crop is the full frame.
5. **Read the digits.** Default expansion is valley (`OdoExpandKind.VALLEY`). Detection boxes on the crop are expanded, clustered, and recognized as digits. A second binarized pass (bin trials) runs the same idea. `pickBestOdometer` prefers a bin-trial string whose length matches the vehicle’s digit count. If the last stored reading’s face started with 9, one extra digit is allowed so a rollover can land. `OdometerTracking.resolveFromOcr` turns the face digits into the stored tracking reading and may raise `odometerRolloverCount`.

Preprocessing of that crop is [OCR_PREPROCESSING.md](OCR_PREPROCESSING.md). Alignment math is [../specs/ISOTROPIC_COORDINATE_SPEC.md](../specs/ISOTROPIC_COORDINATE_SPEC.md).

## Pump

`OcrHarness.runPumpCostVolPipeline`. This is the Quick Fill path. Batch import of old pump photos uses `runPumpCostVolPipelineSetI` and is not this screen.

1. **Deskew.** Same Paddle heatmap angle and camera rotation as the dash.
2. **Find ink.** Detection heatmap at long edges 224, 608, and 1024. Quick Fill uses the G4 det asset when that ABI pack is present, otherwise the production det. Boxes from the scales are merged and pruned to the top N red boxes.
3. **Cover the ink.** Each kept box is grown vertically and a little horizontally, then those rectangles are what OCR sees. The factors and the other expand attempts live in [PUMP_EXPERIMENT_FLOWS.md](PUMP_EXPERIMENT_FLOWS.md). This page does not describe them.
4. **OCR.** Each rectangle is read twice: general text and numeric-with-decimal. How the crop is prepared is [OCR_PREPROCESSING.md](OCR_PREPROCESSING.md).
5. **Classify.** `PumpRoleBandClassifier` picks cost and volume from those readings. The rules are [PUMP_CLASSIFICATION.md](PUMP_CLASSIFICATION.md).

Both empty results surface as “Could not read pump display”. The user can swap cost and volume (↔) and can type either field.

## What this page is not

Alignment Experiment and Pump Experiment are separate screens. Their column-by-column reports are [PUMP_EXPERIMENT_FLOWS.md](PUMP_EXPERIMENT_FLOWS.md) and the notes under [../obsolete/](../obsolete/).
