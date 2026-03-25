# Vehicle Expenses Automated — Developer Guide

## App Launcher Icon (new design)
The official launcher icon is the new design provided by the user (two cars + camera lens + green $ + symbol).

**Correct way to add it (avoids AAPT errors):**
1. Open the project in Android Studio.
2. Right-click on the `res` folder → New → Image Asset.
3. Choose "Launcher Icons (Adaptive and Legacy)".
4. Select the image file you saved from this chat.
5. Generate all densities (Android Studio will create proper ic_launcher.png files).
6. Click Next → Finish.
7. Run `./gradlew clean build`.

This is the standard, safe method. Do not manually copy raw PNGs into mipmap folders.

## Current Architecture (matches repo head v0.9.2-14-g0e4be30)

### Models
- Vehicle (with referenceDashPhotoUrl)
- FuelEntry

### Key Components
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
