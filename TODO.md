# Vehicle Expenses Automated — TODO List

## High Priority (next)
- Make Import Old Pictures screen use the real FuelViewModel.saveFuel() call instead of placeholder Toast.

## Medium Priority
- Full ExpenseEntry screen with receipt OCR.
- Improve reference dash photo setup UI (add odometer confirmation dialog).
- Add settings toggle for OCR confidence threshold.

## Low Priority / Future
- ODB-II integration for live odometer.
- Advanced reports and charts.
- Cloud backup options beyond Google Sheets.
- UI polish for dark mode / tablet.

## Completed (as of this build)
- Automatic OCR on every photo capture (no extra clicks) — now includes gallons + cost from pump/receipt photos
- Camera-first flow for new photos
- Gallery-only Import Old Pictures screen with auto OCR + auto vehicle match
- Reference dash photo auto-matching using perceptual hash
- VehicleViewModel + Room migration for referenceDashPhotoUrl
- CAMERA permission requested on launch in MainActivity.kt (app continues if denied)
- Updated user-manual.md, developer-guide.md and TODO.md

Last updated: $(date +%Y-%m-%d)
