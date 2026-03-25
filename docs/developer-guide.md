# Vehicle Expenses Automated — Developer Guide

## Current Architecture (matches repo head v0.9.2-14-g0e4be30)

### App Icon
The launcher icon is now the new design provided by the user (two cars, camera lens, green $ + symbol). Place the attached image as `ic_launcher.png` in all mipmap folders (hdpi, mdpi, xhdpi, xxhdpi, xxxhdpi) and run `./gradlew clean build` to update the APK.

### Models
- Vehicle (with referenceDashPhotoUrl)
- FuelEntry

### Key Components
- **PhotoPicker.kt**: Camera-first flow.
- **OdometerOcrUtils.kt**: Automatic ML Kit OCR on every photo capture (odometer + gallons + cost).
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
