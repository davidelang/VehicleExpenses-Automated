# Vehicle Expenses Automated — Developer Guide

## App Launcher Icon (new design)
The official launcher icon is the new design provided by the user (two cars + camera lens + green $ + symbol).

**Correct way to add it:**
Use Android Studio → New → Image Asset → Launcher Icons.

## Current Architecture (matches repo head v0.9.2-14-g0e4be30)

### Models
- Vehicle (with referenceDashPhotoUrl)
- FuelEntry

### Key Components
- **MainActivity.kt**: Now requests CAMERA permission on launch and continues even if denied.
- **PhotoPicker.kt**: Camera-first flow.
- **OdometerOcrUtils.kt**: Automatic ML Kit OCR on every photo capture.
- **PhotoStorageManager.kt**: Handles both camera and gallery imports.
- **ImportOldPicturesScreen.kt**: Gallery-only with full automatic OCR.
- **QuickFillupScreen.kt**: Main screen with camera-first PhotoPicker + link to Import Old Pictures.

### Permission Handling
- CAMERA requested in MainActivity onCreate.
- App continues to run if denied (UI will grey out photo features).

### OCR Flow
- On any photo capture: LaunchedEffect triggers OdometerOcrUtils.extractFromPhoto().
- Full extraction: odometer, gallons, cost.

### Build & Sync
- Use `./gradlew clean build` after every change.
- SyncWorker handles Google Sheets.

See CONTRIBUTING.md for full contribution process.
