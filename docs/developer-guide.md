# Vehicle Expenses Automated — Developer Guide

## Current Architecture (matches repo head v0.9.2-14-g0e4be30)

### Models
- Vehicle (with referenceDashPhotoUrl)
- FuelEntry
- ExpenseEntry (planned full support)

### Key Components
- **PhotoPicker.kt**: Camera-first flow (Take Photo button launches camera immediately). Gallery is secondary (used only in Import Old Pictures).
- **OdometerOcrUtils.kt**: Automatic ML Kit OCR on every photo capture. No extra clicks.
- **PhotoStorageManager.kt**: Handles both camera URIs and gallery imports.
- **VehicleViewModel.kt**: Loads vehicles + reference photo logic.
- **ImportOldPicturesScreen.kt**: Gallery-only screen with automatic OCR + auto vehicle match.
- **QuickFillupScreen.kt**: Main screen with camera-first PhotoPicker + link to Import Old Pictures.

### OCR Flow
- On any photo capture (camera or gallery import): `LaunchedEffect` triggers OCR automatically.
- Current extraction: odometer only (extended regex support for gallons/price is in the import screen).

### Build & Sync
- Use `./gradlew clean build` after every change.
- SyncWorker handles Google Sheets (bidirectional when configured).
- Room migrations are in AppDatabase.kt (current version 2 for referenceDashPhotoUrl).

### Important Rules (from CONTRIBUTORS.md)
- Camera-first for new photos, gallery-only for "import old pictures".
- Automatic OCR on every photo capture — no extra buttons.
- Do not add new features until build succeeds with zero fixable warnings.
- Prefer upgrading dependencies over downgrading.

See CONTRIBUTING.md for full contribution process.
