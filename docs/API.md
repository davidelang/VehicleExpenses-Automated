# Vehicle Expenses Automated — API Reference

## Data Models (Room Entities)

### `Vehicle.kt`
- `Vehicle(id, name, make, model, year, licensePlate, vin, notes, referenceDashPhotoUrl, cleanedReferenceDashPhotoUrl, odometerCropLeft, odometerCropTop, odometerCropRight, odometerCropBottom, otherTextCropLeft, otherTextCropTop, otherTextCropRight, otherTextCropBottom, landmarkTextBlocksJson)`
  - Core entity for vehicle management. Includes fields for OCR alignment and cropping.

### `FuelEntry.kt`
- `FuelEntry(id, vehicleId, odometer, gallons, cost, timestamp, photoUrl, isPartialFill)`
  - Represents a single fuel fill-up. `isPartialFill` is used to skip fuel economy calculations between partial fills.

### `ExpenseEntry.kt`
- `ExpenseEntry(id, vehicleId, amount, description, date, photoUrl, category, receiptImagePath)`
  - Generic expense tracking. `category` defaults to "Other".

## Repositories & DAOs

### `VehicleRepository.kt` / `VehicleDao.kt`
- `getAllVehicles()`: Flow of all vehicles.
- `getVehicleById(id)`: Single vehicle lookup.
- `insertVehicle(vehicle)`, `updateVehicle(vehicle)`, `deleteVehicle(vehicle)`.

### `FuelEntryRepository.kt` / `FuelEntryDao.kt`
- `getAllFuelEntries()`: All history.
- `getEntriesForVehicle(vehicleId)`: Filtered history.
- `insertFuelEntry(entry)`, `updateFuelEntry(entry)`, `deleteFuelEntry(entry)`.

### `ExpenseEntryRepository.kt` / `ExpenseEntryDao.kt`
- `getAllExpenseEntries()`: All history.
- `getEntriesForVehicle(vehicleId)`: Filtered history.
- `insertExpenseEntry(entry)`, `updateExpenseEntry(entry)`, `deleteExpenseEntry(entry)`.

## OCR & Image Processing

### `OdometerOcrUtils.kt`
- `extractFromPhoto(photoPath: String, cropRect: RectF? = null)`
  - Orchestrates OCR using ML Kit and Tesseract with OpenCV preprocessing.

### `ImageAlignmentUtils.kt`
- `alignImages(reference: Bitmap, query: Bitmap, minInliers: Int = 15)`
  - Uses ORB features and Homography to align a dashboard photo to the stored reference.

### `PhotoAlignmentUtils.kt`
- `alignToReference(fillupBitmap: Bitmap, referenceCrop: Rect?)`
  - High-level stage-1 alignment logic for odometer extraction.

### `ImageHashUtils.kt`
- `computeAverageHash(bitmap: Bitmap)`: 64-bit dhash for duplicate detection.
- `similarity(hash1: Long, hash2: Long)`: Normalized Hamming distance (0.0–1.0).

## Synchronization & Storage

### `SyncManager.kt`
- `triggerSync()`: Initiates WorkManager-based background sync.
- `isSyncInProgress()`: State tracking for UI feedback.

### `SyncWorker.kt`
- `doWork()`: WorkManager entry point. Coordinates CSV export/import with Google Sheets/Drive.

### `GoogleSheetsClient.kt`
- `syncData(fuelEntries, expenseEntries)`: Appends or updates rows in specified Google Sheets.

### `GoogleDriveProvider.kt`
- `uploadPhoto(file)`, `downloadPhoto(fileId)`: Manages photo backups in a dedicated app folder on Google Drive.

### `PhotoStorageManager.kt`
- Manages local photo storage, cleanup, and rotation.

## UI Components

### `MainActivity.kt`
- Root Activity containing the `NavHost` and `ModalNavigationDrawer`.

### `QuickFillupScreen.kt`
- Main UI for fuel entry. Connects `FillupViewModel` to the camera and OCR pipeline.

### `ExperimentAlignmentScreen.kt`
- `runExperiment(...)`: Runs automated alignment tests on a set of local photos and generates an HTML report.
