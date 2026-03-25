# Vehicle Expenses Automated — TODO List
## High Priority (next)
## Medium Priority
## Low Priority / Future
- ODB-II integration for live odometer.
- unit selection in setup
  - include ability to override to different units for fill, normalize when saving/syncing
- add ability to enter missed fillup record
  - make sure reporting ignores a final partial fill with not full fill
  - make sure reporting ignores period between full fills where there is a missed fill
- add additional image backup backup options
  - google photos
  - amazon photos
  - dropbox
  - ssh to personal server
  - http to personal server (including cgi file to receive, save, and retrieve files)
  - others (is there a library that exposes all the various cloud storage options?)
## Completed (as of this build)
- Automatic OCR on every photo capture (no extra clicks) — now includes gallons + cost from pump/receipt photos
- Camera-first flow for new photos
- Gallery-only Import Old Pictures screen with auto OCR + auto vehicle match
- Reference dash photo auto-matching using perceptual hash
- VehicleViewModel + Room migration for referenceDashPhotoUrl
- CAMERA permission requested on launch in MainActivity.kt (app continues if denied)
- Updated user-manual.md and developer-guide.md to match current functionality
- Make Import Old Pictures screen use the real FuelViewModel.saveFuel() call instead of placeholder Toast.
- Add settings toggle for OCR confidence threshold.
- Full ExpenseEntry screen with receipt OCR.
- Advanced reports and charts.
- UI polish for dark mode / tablet / ensure it works on all size/resolution screens.
- Preferences screen (OCR thresholds, storage backends, sync settings, etc.) available from every screen via hamburger menu.
Last updated: 2026-03-24
