# Vehicle Expenses Automated — User Manual

## App Icon
The official launcher icon features two cars, a camera lens, and a green dollar sign with a plus symbol. It represents the "camera-first" approach to tracking vehicle expenses.

## Overview
Vehicle Expenses Automated is designed to track fuel entries and expenses with minimal manual data entry. By leveraging advanced OCR and image alignment, the app automatically extracts odometer readings and fuel costs from your dashboard and pump photos.

## Core Workflow (Camera-First)

### Quick Fill-up (Main Screen)
- **Capture**: Tap **Take Photo** to open the camera. Aim at your dashboard (odometer) or fuel pump.
- **Automatic OCR**: The app automatically detects the vehicle based on the visual alignment of the dashboard and extracts:
  - Odometer reading
  - Gallons filled
  - Total cost
- **Save**: Review the extracted data and tap **Save**. The entry is stored in the local database and queued for sync.

### Import Old Pictures
- Use this feature to batch-process existing photos from your gallery.
- Select one or more photos; the app will run the same OCR pipeline to extract data and match them to your vehicles.

## Reference Dash Photos & OCR Alignment
To achieve high OCR accuracy, the app uses **Reference Dash Photos**:
1. **Set Reference**: In the **Manage Vehicles** screen, capture or select a clear photo of your vehicle's dashboard.
2. **Define Crops**: You can specify exact "crop rectangles" for the odometer and other important text areas on your dashboard.
3. **Alignment**: Every time you take a new "Quick Fill-up" photo, the app aligns it to your reference photo. This ensures that the OCR engine always looks at the exact same spot for the odometer, even if you hold the camera at a slightly different angle.

## Managing Vehicles
- Add your vehicles with details like Make, Model, and Year.
- Set up the **Reference Dash Photo** and **Crop Rectangles** to enable the most accurate automatic OCR.
- View and manage existing entries for each vehicle.

## Expenses
- Use the **New Expense Entry** screen to record non-fuel costs like repairs, insurance, or maintenance.
- You can attach a photo of the receipt for each expense.

## Reports & Charts
- View your fuel economy (MPG/L/100km) over time.
- Track your total spending on fuel and maintenance.
- Analyze cost trends through interactive charts.

## Synchronization & Backup
- **Google Sheets**: Optionally sync your entries to a Google Sheet for easy access and reporting on your PC.
- **Google Drive**: Back up your photos to a private folder in your Google Drive.
- **Local First**: All your data is stored securely on your device first. Sync is optional and happens in the background.

## Permissions
- **Camera**: Required for the "camera-first" tracking flow.
- **Storage/Media**: Required for gallery imports and local photo management.

## Settings
- Configure your preferred units (Miles/Gallons vs. Kilometers/Liters).
- Set up Google account integration for sync and backup.
- Manage local storage and photo quality.

For more technical details, see the `developer-guide.md`.
