# Vehicle Expenses Automated — Developer Guide

## App Launcher Icon
The official launcher icon (two cars + camera lens + green $ + symbol) should be updated using Android Studio → New → Image Asset → Launcher Icons.

## Current Architecture

### Permission Handling
- **CAMERA**: Requested in `MainActivity.onCreate()`. If denied, photo features are disabled.
- **MEDIA**: Requested for gallery imports (`READ_MEDIA_IMAGES` or `READ_EXTERNAL_STORAGE`).

### Root Navigation
- **MainActivity.kt**: The primary entry point. It contains the `ModalNavigationDrawer` and `NavHost` for the entire application.
- Routes: `quickfill`, `managevehicles`, `expense`, `expenselist`, `import`, `reports`, `settings`, `help`, `about`, `experiment`.

### Core Logic & Components
- **OCR Pipeline**: 
  - `OdometerOcrUtils.kt`: Orchestrates OCR using ML Kit and Tesseract.
  - `ImageAlignmentUtils.kt`: Aligns fill-up photos to a stored vehicle reference photo using ORB and Homography.
  - `PhotoAlignmentUtils.kt`: Handles the initial cropping and transformation based on vehicle-specific crop rectangles.
- **Data Persistence**: 
  - Room-based database (`AppDatabase.kt`).
  - Repositories (`VehicleRepository`, `FuelEntryRepository`, `ExpenseEntryRepository`) act as a clean API for ViewModels.
- **Sync & Storage**:
  - `SyncManager.kt` and `SyncWorker.kt`: Orchestrate background synchronization using `WorkManager`.
  - `GoogleSheetsClient.kt`: Synchronizes entry data to specific Google Sheets.
  - `GoogleDriveProvider.kt`: Backs up photos to a dedicated application folder on Google Drive.
  - `PhotoStorageManager.kt`: Manages local storage, including caching and cleanup.

### OCR & Alignment Flow
1. **Photo Capture**: `PhotoPicker.kt` captures a photo.
2. **Alignment**: If a reference photo exists for the vehicle, `ImageAlignmentUtils` aligns the new photo to the reference.
3. **Cropping**: The aligned photo is cropped using the rectangles stored in the `Vehicle` entity (`odometerCrop`, `otherTextCrop`).
4. **Extraction**: `OdometerOcrUtils` runs OCR on the cropped segments to extract odometer, gallons, and cost.

## Sync Logic
The sync process is triggered by `SyncManager` and executed by `SyncWorker`:
1. **Export**: Local Room data is converted to CSV via `CsvManager`.
2. **Sheets Sync**: `GoogleSheetsClient` uploads CSV data to Google Sheets.
3. **Drive Sync**: `GoogleDriveProvider` uploads new local photos and downloads missing ones from Drive.
4. **Conflict Resolution**: (Future) Simple "last-write-wins" or manual resolution via `ConflictResolutionScreen`.

## Debugging

### ADB Logcat
For multiple devices:
```bash
adb -s <serial-number> logcat
```
To filter for the app:
```bash
adb -s <serial-number> logcat | grep "com.davidlang.vehicleexpensesautomated"
```

### Emulator Management
List AVDs:
```bash
emulator -list-avds
```
Start AVD:
```bash
emulator -avd <AVD_NAME>
```

## Build Process
- Use `./gradlew clean build` to verify changes.
- This project uses **KSP** (Kotlin Symbol Processing) exclusively. Do not reintroduce `kapt`.

See `CONTRIBUTING.md` for the full contribution process and AI agent operating rules.
