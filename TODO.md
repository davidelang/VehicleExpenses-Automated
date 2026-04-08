# Vehicle Expenses Automated — TODO List

## High Priority (next)
- Improve GlobalWordCounts (IDF) calculation:
  - Extract text and compute local word counts per-image as they are loaded/updated in the Manage Vehicles page.
  - When a vehicle is saved or created, re-process the aggregate global word counts across all vehicles.
  - Store results in GlobalMetadata or similar for efficient access during matching.


## Medium Priority

## Low Priority / Future
- Landmark crop box UI improvements:
  - Change button text to "Highlight other parts of the image that may change"
  - Allow clearing all boxes, clearing the last box, saving the current box (which starts a new box)
  - When finished, the landmark area needs to be saved
- ODB-II integration for live odometer.
- Unit selection in setup (override units for fill, normalize when saving/syncing)
- Additional image backup options (Google Photos, Amazon Photos, Dropbox, SSH, HTTP, etc.)
  - Full Amazon Photos API integration (authenticated two-way: download shared album for testing + upload new photos to album)
- Preferences screen (OCR thresholds, storage backends, sync settings)
- Advanced reports and charts
- UI polish for dark mode / tablet

## Completed
- Automatic OCR on every photo capture
- Camera-first flow + Gallery-only import
- Reference dash photo auto-matching
- Full crop editing UI for odometer (drag any corner, clear/replace, live preview)
- OCR debug + confirmation dialogs
- New-vehicle creation flow
- Alignment Experiment screen with live progress and ZIP extraction

Last updated: 2026-03-31
