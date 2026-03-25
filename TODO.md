# Vehicle Expenses Automated — TODO List
## High Priority (next)
## Medium Priority
- Full ExpenseEntry screen with receipt OCR.
- Add settings toggle for OCR confidence threshold.
## Low Priority / Future
- ODB-II integration for live odometer.
- Advanced reports and charts.
- add additional image backup backup options
  - google photos
  - amazon photos
  - dropbox
  - ssh to personal server
  - http to personal server (including cgi file to receive, save, and retrieve files)
  - others (is there a library that exposes all the various cloud storage options?)
- UI polish for dark mode / tablet / ensure it works on all size/resolution screens.
## Completed (as of this build)
- Automatic OCR on every photo capture (no extra clicks) — now includes gallons + cost from pump/receipt photos
- Camera-first flow for new photos
- Gallery-only Import Old Pictures screen with auto OCR + auto vehicle match
- Reference dash photo auto-matching using perceptual hash
- VehicleViewModel + Room migration for referenceDashPhotoUrl
- CAMERA permission requested on launch in MainActivity.kt (app continues if denied)
- Updated user-manual.md and developer-guide.md to match current functionality
- Make Import Old Pictures screen use the real FuelViewModel.saveFuel() call instead of placeholder Toast.
Last updated: 2026-03-24
