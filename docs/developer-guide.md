# Vehicle Expenses Automated — Developer Guide

## Current Architecture (matches repo head da3a2b4f1829bf4dda99090b07105d3ba7134ce9)

### Permission Handling
- CAMERA is now requested in MainActivity onCreate.
- App continues to run even if permission is denied (photo features will be greyed out in future UI updates).

### Root Composable
- VehicleExpensesApp.kt: Simple NavHost root that launches QuickFillupScreen.

### Models
- Vehicle (with referenceDashPhotoUrl)
- FuelEntry

### Key Components
- **MainActivity.kt**: Permission request + VehicleExpensesApp root.
- **PhotoPicker.kt**: Camera-first flow.
- **OdometerOcrUtils.kt**: Automatic ML Kit OCR on every photo capture.
- **PhotoStorageManager.kt**: Handles both camera and gallery imports.
- **ImportOldPicturesScreen.kt**: Gallery-only with full automatic OCR.
- **QuickFillupScreen.kt**: Main screen with camera-first PhotoPicker + link to Import Old Pictures.

### OCR Flow
- On any photo capture: LaunchedEffect triggers OdometerOcrUtils.extractFromPhoto().
- Full extraction: odometer, gallons, cost.

### Build & Sync
- Use `./gradlew clean build` after every change.
- SyncWorker handles Google Sheets.

See CONTRIBUTING.md for full contribution process.
