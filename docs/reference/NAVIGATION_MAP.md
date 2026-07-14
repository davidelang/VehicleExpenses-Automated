---
type: implementation-reference
status: dynamic
ai_directive: "This is a downstream reference. It MUST be updated continuously to reflect the current state of the codebase. If you change a function or architecture described here, update this document in the same commit."
---

# Vehicle Expenses Automated — Navigation Map

## Menu → Pages
- **Quick Fill-up**: Main entry point for recording fuel fill-ups via camera/OCR (app start destination).
- **Manage Vehicles**: Add, edit, or remove vehicles. Set up OCR reference photos and crop regions.
- **New Expense Entry**: Record non-fuel expenses (repairs, insurance, etc.).
- **Expense List**: View historical non-fuel expenses.
- **Import Old Pictures**: Batch import odometer photos for manual fuel entry.
- **Reports & Charts**: View fuel economy, cost trends, and summaries.
- **Settings**: Configure sync, units, and storage (sub-routes: Spreadsheet Sync, Photo Backup).
- **Help**: User manual and troubleshooting.
- **About**: Version information and credits.
- **Alignment Experiment**: Debugging tool for testing OCR alignment logic (not linked from production flows).
- **Pump Experiment**: Debugging tool for gas-pump cost/volume extraction (not linked from production flows).

## Page Flows
- **Quick Fill-up** → **Reports** (after successful save)
- **Manage Vehicles** → (pop back to previous screen)
- **New Expense Entry** → **Reports** (after successful save)
- **Expense List** → (pop back to previous screen)
- **Import Old Pictures** → (pop back to previous screen)
- **Reports & Charts** → **Quick Fill-up** (via menu or back button)
- **Settings** → (pop back to previous screen)
- **Settings** → **Spreadsheet Sync** / **Photo Backup** (sub-routes)
- **Help** → (pop back to previous screen)
- **About** → (pop back to previous screen)
- **Alignment Experiment** → (pop back to previous screen)
- **Pump Experiment** → (pop back to previous screen)

## Unwired / future
- **Conflict Resolution** (`ConflictResolutionScreen.kt`): Implemented but not in the navigation drawer; reserved for future multi-device sync conflict UI.

## Notes
- **Start destination**: Quick Fill-up (`quickfill`)
- **Navigation Drawer**: Accessible from all top-level screens.
- **Deep Linking**: Not currently implemented.