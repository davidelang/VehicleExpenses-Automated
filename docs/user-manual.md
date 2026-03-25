# Vehicle Expenses Automated — User Manual

## Overview
This app tracks vehicle fuel entries and expenses using **camera-first photo capture** with fully automatic OCR.

## Core Workflow (Camera-First)
- **Quick Fill-up** (main screen):
  - Tap **Take Photo** → camera opens immediately for dashboard/odometer photo.
  - OCR runs automatically and auto-matches the vehicle using reference dash photos.
  - Fill in gallons/cost (or use future pump-photo OCR).
  - Save to local Room database.
- **Import Old Pictures** (gallery-only):
  - Tap the button → pick an old photo from gallery.
  - OCR runs automatically (odometer + gallons + cost where possible).
  - Auto-matches vehicle if possible.
  - Save as Fuel Entry.

## Reference Dash Photos
- When creating a new vehicle or saving a fill-up photo, you can set it as the vehicle's reference dashboard photo.
- Future dash photos are automatically matched against these references using perceptual hash.

## Automatic OCR
- Runs instantly on every new photo capture (no extra button clicks).
- Currently extracts odometer from dash photos.
- Pump/receipt photos will have full gallons + price OCR in a future update.

## Settings
- Google Sheets sync (optional two-way)
- Photo storage location and backup options

## Data Flow
1. Photo captured → automatic OCR → optional auto vehicle match
2. Data saved to local Room database
3. Background SyncWorker pushes to Google Sheets (if configured)

All data is stored locally first — sync is optional.

## Planned Features (see TODO.md)
- Full pump-photo OCR for gallons + price
- Expense receipt OCR
- Improved import flow for historical photos
