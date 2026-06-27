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

### `FuelFillup.kt`
- `FuelFillup(id, vehicleId, odometer, gallons, cost, timestamp)`
  - Simplified fuel record, possibly used for internal calculations or legacy support.

### `ExpenseEntry.kt`
- `ExpenseEntry(id, vehicleId, amount, description, date, photoUrl, category, receiptImagePath)`
  - Entity for general expenses (repairs, insurance, etc.).

## OCR & Image Processing

### `OcrHarness.kt`
- `OcrHarness` (Object): Orchestrates the multi-engine OCR process.
- `interface OcrEngine`: Common interface for all OCR engines.
- `interface OcrEngineStrategy`: Strategy for choosing engines.
- `interface ReportCollector`: Collects results from multiple engines.

### `OcrEngine.kt`
- `MlKitEngine`: Implementation of `OcrEngine` using Google's ML Kit.
- `object OcrUtils`: General utilities for OCR results.

### `NativePaddleEngine.kt`
- `NativePaddleEngine`: High-performance C++ based PaddleOCR implementation. Supports "V3" variants and "Mono" mode for odometer-specific optimizations.

### `HybridOcrEngine.kt`
- `HybridOcrEngine`: Combines ML Kit and PaddleOCR results for maximum accuracy.

### `OdometerOcrUtils.kt` & `DiscoveryOcrUtils.kt`
- `OdometerOcrUtils` (Object): Odometer-specific sanitization and multi-step image variation processing (Raw, Grayscale, Bilateral, etc.).
- `DiscoveryOcrUtils` (Object): Logic for identifying "Golden Anchors" during the vehicle discovery phase.

### `ImageAlignmentUtils.kt` & `NativeImageUtils.kt`
- `ImageAlignmentUtils` (Object): Anchors-based triangulation (Zoom, Rotation, Pan) and Tier-1 Veto logic.
- `NativeImageUtils` (Object): JNI-accelerated image operations (e.g., Grayscale, Bilateral filtering, Deskewing).

## Localization & Image Analysis
- `LocationUtils.kt`: Handles GPS coordinate retrieval for fuel entries.
- `ImageHashUtils.kt`: Generates perceptual hashes (pHash) to identify duplicate or similar images.

## Synchronization & Storage
- `SyncManager.kt` / `SyncWorker.kt` / `GoogleSheetsClient.kt` / `GoogleDriveProvider.kt` / `PhotoStorageManager.kt`: Manages the local-first synchronization cycle to Google Sheets and Drive.

## UI Components
- `ExperimentAlignmentScreen.kt`: Advanced debug tool for comparing OCR engines against a ground-truth dataset.
- `DashboardScreen.kt`: Summary view of vehicle status and recent activity.
- `ManageVehiclesScreen.kt`: Interface for configuring vehicle metadata and OCR crop regions.
- `ConflictResolutionScreen.kt`: UI for resolving data discrepancies between local and remote (Google Sheets) state.
