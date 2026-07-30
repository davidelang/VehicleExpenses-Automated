---
type: implementation-reference
status: dynamic
ai_directive: "This is a downstream reference. It MUST be updated continuously to reflect the current state of the codebase. If you change a function or architecture described here, update this document in the same commit."
---

# Vehicle Expenses Automated — Navigation Map

## Menu → Pages
- **Quick Fill-up**: Main entry point for recording fuel fill-ups via camera/OCR (app start destination).
- **Trip Tracking**: Open-only trip segments (Business / Personal / …) as fuel rows with **Trip Type**; camera or manual odo; immediately after Quick Fill-up in the drawer.
- **Manage Vehicles**: Add, edit, or remove vehicles. Set up OCR reference photos and crop regions.
- **New Expense Entry**: Record non-fuel expenses (repairs, insurance, etc.).
- **Expense List**: View historical non-fuel expenses.
- **Import Old Pictures**: Batch import odometer photos for manual fuel entry.
- **Reports & Charts**: View fuel economy, cost trends, and summaries (production).
- **Reports Lab**: Experimental reports hub and child sets including **Trip miles** (`reports_lab/trips`; always in drawer).
- **Fuel History**: Per-vehicle fill list with edit + on-demand photo fetch (includes trip-start rows).
- **Settings**: Configure units, storage, debug, experiment gates (not the primary sync summary).
- **Syncing**: Sync summary / Sync now / spreadsheet + photo destination entry points.
- **Help**: User manual and troubleshooting.
- **About**: Version information and credits.
- **Alignment Experiment**: Debugging tool for testing OCR alignment logic (not linked from production flows; experiment toggle).
- **Pump Experiment**: Debugging tool for gas-pump cost/volume extraction (not linked from production flows; experiment toggle).

## Page Flows
- **Quick Fill-up** → **Reports** (after successful save)
- **Trip Tracking** → (pop back / drawer; saves trip-start fuel rows in place)
- **Manage Vehicles** → (pop back to previous screen)
- **New Expense Entry** → **Reports** (after successful save)
- **Expense List** → (pop back to previous screen)
- **Import Old Pictures** → (pop back to previous screen)
- **Reports & Charts** → **Quick Fill-up** (via menu or back button)
- **Reports Lab** → child lab routes / drawer
- **Fuel History** → **Edit Fill** (`fuel/{fuelId}`)
- **Settings** → (pop back to previous screen)
- **Syncing** → **Spreadsheet Sync** / **Photo Backup** (sub-routes)
- **Settings** → **Spreadsheet Sync** / **Photo Backup** (sub-routes still reachable)
- **Help** → (pop back to previous screen)
- **About** → (pop back to previous screen)
- **Alignment Experiment** → (pop back to previous screen)
- **Pump Experiment** → (pop back to previous screen)

## Unwired / future
- **Conflict Resolution** (`ConflictResolutionScreen.kt`): Implemented but not in the navigation drawer; reserved for future multi-device sync conflict UI.

## Notes
- **Start destination**: Quick Fill-up (`quickfill`)
- **Trip Tracking route**: `triptracking` (drawer item immediately after Quick Fill-up)
- **Other top-level routes**: `fuelhistory`, `syncing`, `reports_lab` (+ `reports_lab/*` children including `trips`), `fuel/{fuelId}`
- **Navigation Drawer**: Accessible from all top-level screens.
- **Deep Linking**: Not currently implemented.