# Vehicle Expenses Automated — TODO List

## High Priority (next)
- Extend automatic OCR (OdometerOcrUtils) to reliably extract gallons and price from pump/receipt photos (full visionText already available).
- Make Import Old Pictures screen use the real FuelViewModel.saveFuel() call instead of placeholder Toast.
- Add dedicated "Add New Vehicle" screen navigation from Quick Fill-up when no match is found (already partially wired).

## Medium Priority
- Full ExpenseEntry screen with receipt OCR (gallons/price/date).
- Improve reference dash photo setup UI (add odometer confirmation dialog).
- Add settings toggle for OCR confidence threshold.

## Low Priority / Future
- ODB-II integration for live odometer.
- Advanced reports and charts.
- Cloud backup options beyond Google Sheets.
- UI polish for dark mode / tablet.

## Done (recent)
- Automatic OCR on every photo capture (no extra clicks)
- Camera-first flow for new photos
- Gallery-only Import Old Pictures screen with auto OCR
- Reference dash photo auto-matching using perceptual hash
- VehicleViewModel + Room migration for referenceDashPhotoUrl

Last updated: $(date +%Y-%m-%d)
