---
type: implementation-reference
status: dynamic
ai_directive: "This is a downstream reference. It MUST be updated continuously to reflect the current state of the codebase. If you change a function or architecture described here, update this document in the same commit."
---

# Vehicle Expenses Automated — API Reference

## Data Models (Room Entities)

### `Vehicle.kt`
- `Vehicle(id, name, make, model, year, licensePlate, vin, notes, referenceDashPhotoUrl, cleanedReferenceDashPhotoUrl, odometerCropLeft, odometerCropTop, odometerCropRight, odometerCropBottom, otherTextCropLeft, otherTextCropTop, otherTextCropRight, otherTextCropBottom, landmarkTextBlocksJson)`
  - Core entity for vehicle management. Crop boxes are stored as ICRS (Isotropic Center-Relative Space) or raw pixel bounds. (Normalized 0.0–1.0 per-axis is obsolete.) `landmarkTextBlocksJson` holds the multi-engine JSON manifest. See `docs/specs/ISOTROPIC_COORDINATE_SPEC.md`.

### `FuelEntry.kt`
- `FuelEntry(id, vehicleId, odometer, gallons, cost, timestamp, photoUrl, isPartialFill, latitude, longitude, location, cloudManifest)`
  - Primary record for fuel fill-ups, including location and cloud sync metadata.

### `ExpenseEntry.kt`
- `ExpenseEntry(id, vehicleId, amount, description, date, photoUrl, category, receiptImagePath, …)`
  - Entity for general expenses (repairs, insurance, etc.). `photoUrl` is the active receipt field; `receiptImagePath` is a deprecated column retained for DB migration only.

## OCR & Image Processing

### `OcrHarness.kt`
- `OcrHarness` (Object): Production OCR orchestration (`runDiscovery`, `runAutoFillPipeline`, `runPumpCostVolPipeline`).
- `OcrHarnessResult`: Structured harness output for experiments and Quick Fill debug.

### `OcrEngine.kt`
- `MlKitEngine`: Implementation of `OcrEngine` using Google's ML Kit.
- `OcrResult`, `TextBlock`: Shared OCR result types.

### `NativePaddleEngine.kt`
- `NativePaddleEngine`: High-performance C++ based PaddleOCR implementation. Production path `uint8_fp16_u8`.

### `OdometerOcrUtils.kt`
- `OdometerOcrUtils` (Object): Odometer-specific sanitization, landmark serialize/deserialize, and photo OCR helpers.

### `PumpCostVolUtils.kt`
- `PumpCostVolUtils` (Object): Quick Fill pump panel cost/volume classification (production path).

### `ImageAlignmentUtils.kt` & `NativeImageUtils.kt`
- `ImageAlignmentUtils` (Object): Anchors-based triangulation and veto logic.
- `NativeImageUtils` (Object): JNI-accelerated image operations (grayscale, bilateral, deskew, histograms).

## Synchronization & Storage
- `SpreadsheetSyncCoordinator.kt` / `SyncWorker.kt` / `TabularShareApi` / `GoogleSheetsClient.kt`: Multi-destination spreadsheet sync.
- `PhotoBackupCoordinator.kt` / `PhotoBackupWorker.kt` / `PhotoStorageManager.kt`: Multi-destination photo backup (Google Drive, OneDrive, S3, rclone Other).

## UI Components (production)
- `QuickFillupScreen.kt`: Primary fuel fill-up capture and OCR.
- `ManageVehiclesScreen.kt`: Vehicle metadata and OCR crop regions.
- `ExpenseEntryScreen.kt` / `ExpenseListScreen.kt`: Expense capture and history.
- `ReportsScreen.kt`, `SettingsScreen.kt`, `SpreadsheetSyncScreen.kt`, `PhotoBackupScreen.kt`.

## Debug / experiment (temporary)
- `ExperimentAlignmentScreen.kt`, `ExperimentPumpScreen.kt`: Research harnesses; scheduled for removal.

## Unwired (future)
- `ConflictResolutionScreen.kt`: Sync conflict UI — not yet routed in `MainActivity`.