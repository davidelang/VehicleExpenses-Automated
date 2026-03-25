# Vehicle Expenses Automated — User Manual

## App Icon
The official launcher icon is the new design provided (two cars, camera lens, green dollar sign with +). It represents the camera-first expense tracking experience.

## Overview
This app tracks vehicle fuel entries and expenses using **camera-first photo capture** with fully automatic OCR.

## Core Workflow (Camera-First)
- **Quick Fill-up** (main screen):
  - Tap **Take Photo** → camera opens immediately for dashboard/odometer photo.
  - OCR runs automatically (odometer + gallons + cost when available) and auto-matches the vehicle using reference dash photos.
  - Save to local Room database.
- **Import Old Pictures** (gallery-only):
  - Tap the button → pick an old photo from gallery.
  - OCR runs automatically (odometer + gallons + cost).
  - Auto-matches vehicle if possible.
  - Save as Fuel Entry.

## Reference Dash Photos
- When creating a new vehicle or saving a fill-up photo, you can set it as the vehicle's reference dashboard photo.
- Future dash photos are automatically matched against these references using perceptual hash.

## Automatic OCR
- Runs instantly on every photo capture (no extra button clicks).
- Extracts odometer from dash photos and gallons + cost from pump/receipt photos.

## Permissions
- The app requests CAMERA permission on first launch.
- If denied, it continues to operate (photo features are disabled or greyed out).

## Settings
- Google Sheets sync (optional two-way)
- Photo storage location and backup options

## Data Flow
1. Photo captured → automatic OCR → optional auto vehicle match
2. Data saved to local Room database
3. Background SyncWorker pushes to Google Sheets (if configured)

All data is stored locally first — sync is optional.
