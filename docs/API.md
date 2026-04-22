# Vehicle Expenses Automated — API Reference

## Data Models (Room Entities)

### `Vehicle.kt`
- `Vehicle(id, name, make, model, year, licensePlate, vin, notes, referenceDashPhotoUrl, cleanedReferenceDashPhotoUrl, odometerCropLeft, odometerCropTop, odometerCropRight, odometerCropBottom, otherTextCropLeft, otherTextCropTop, otherTextCropRight, otherTextCropBottom, landmarkTextBlocksJson)`
  - Core entity for vehicle management. Crop boxes are stored as normalized bounds (0.0 to 1.0). `landmarkTextBlocksJson` holds the multi-engine JSON manifest.

### `FuelEntry.kt`
- `FuelEntry(id, vehicleId, odometer, gallons, cost, timestamp, photoUrl, isPartialFill)`

### `ExpenseEntry.kt`
- `ExpenseEntry(id, vehicleId, amount, description, date, photoUrl, category, receiptImagePath)`

## OCR & Image Processing

### `OcrHarness.kt`
- `runDiscovery(bitmap: Bitmap, context: Context): Map<String, OcrResult>`: Orchestrates multi-engine discovery pass (ML Kit, Paddle-Lite, Paddle-ML-Hybrid) using global Bilateral and Grayscale filters.
- `runRefinement(bitmap: Bitmap, context: Context): Map<String, OcrResult>`: Evaluates odometer crops for digits.
- `getDiscoveryEngineNames(context: Context)`: Returns active engines for discovery.

### `OdometerOcrUtils.kt`
- `cleanLandmarkString(text: String)`: Phase 34/40 robust sanitization. Globally filters non-ASCII (32-126) and surgically trims leading/trailing punctuation (` `, `-`, `.`, `_`, `,`, `*`).
- `serializeMultiEngineLandmarks(results: Map<String, OcrResult>)`: Consolidates normalized (cx, cy, w, h) landmarks from all engines into a JSON string.
- `deserializeMultiEngineLandmarks(json: String?, imgW: Int, imgH: Int)`: Parses the manifest to reconstruct `OcrResult` objects mapped to full-resolution pixels.
- `calculateAverageTextAngle(bitmap: Bitmap)`: Median angle calculation for auto-deskewing.
- `runMultiStepOcr(bitmap: Bitmap, context: Context)`: Generates 5 variations of a crop (Raw, Grayscale, Bilateral, CLAHE, Otsu).

### `ImageAlignmentUtils.kt`
- `performTier1Veto(queryLandmarks, allVehicles, engineName)`: Disqualifies vehicles using an engine-specific Veto Pool and applies the 1-vs-3+ Least-Vetoed Rescue Algorithm.
- `anchorAlign(refBmp, queryBmp, refLandmarks, queryLandmarks, vehicle)`: Triangulates geometric transformation (Zoom, Rotation, Pan) based on landmark matching.
- `alignImages(reference, query, refLandmarks, queryLandmarks, vehicle)`: Feature-based ORB alignment.

### `AlignmentEngine.kt` & `IdentityEngine.kt`
- Interfaces for `OrbAffineEngine`, `AnchorTriangulationEngine`, `HubEngine`, `FeatureIdentityEngine`, `ArgIdentityEngine`, `EmbeddingIdentityEngine`, `ConsensusIdentityEngine`, `TieredIdentityEngine`, `VetoIdentityEngine`, and `HardcodedIdentityEngine`.

## Synchronization & Storage
- `SyncManager.kt` / `SyncWorker.kt` / `GoogleSheetsClient.kt` / `GoogleDriveProvider.kt` / `PhotoStorageManager.kt`: Handles WorkManager periodic syncs to Google APIs.

## UI Components
- `ExperimentAlignmentScreen.kt`: Advanced test harness evaluating a local folder of pictures against `Vehicle` manifests. Outputs `alignment_results.json` and split HTML tables containing multi-engine alignment trace details. Includes dynamic `ReferenceCache`.
- `ManageVehiclesScreen.kt`: Handles user editing of OCR crop regions. Includes split "Run Discovery" vs "Show Landmarks" UI for manifest hydration and manual overrides.