# Vehicle Expenses Automated User Manual

## Overview
This app tracks vehicle fuel entries and expenses with photo-based entry, OCR, and Google Sheets sync.

### Main Screens
- **Quick Fuel Entry** (default): Take dashboard photo → auto vehicle + odometer → pump photo → save entry
- **Vehicle List**: View all vehicles
- **Vehicle Reports**: See fuel/expense history per vehicle
- **Dashboard**: Export and overview

### Settings
- Sheet ID
- Fuel photo save toggle

### Data Flow
1. Take photos → OCR extracts data
2. Save to local Room DB (permanent)
3. Background sync pushes to Google Sheets

All names updated: FuelEntry, ExpenseEntry, etc.
