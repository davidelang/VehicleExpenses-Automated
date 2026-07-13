---
type: implementation-reference
status: dynamic
ai_directive: "This is a downstream reference. It MUST be updated continuously to reflect the current state of the codebase. If you change a function or architecture described here, update this document in the same commit."
---

# Vehicle Expenses Automated — User Manual

## App Icon
The official launcher icon features two cars, a camera lens, and a green dollar sign with a plus symbol. It represents the "camera-first" approach to tracking vehicle expenses.

## Overview
Vehicle Expenses Automated is designed to track fuel entries and expenses with minimal manual data entry. By leveraging advanced multi-engine OCR and image alignment, the app automatically extracts odometer readings and fuel costs from your dashboard and pump photos.

## Core Workflow (Camera-First)

### Quick Fill-up (Main Screen)
- **Capture**: Tap **Take Photo** to aim at your dashboard (odometer) or fuel pump.
- **Automatic Identity**: The app detects the vehicle based on the visual alignment of the dashboard using Veto algorithms.
- **Extraction**: Reads the Odometer, Gallons, and Cost.
- **Location**: If enabled, the app automatically tags the entry with your current GPS coordinates.
- **Save**: Review the extracted data and tap **Save**. The entry is queued for background sync.

### Dashboard & Recent Activity
- The **Dashboard** provides a high-level summary of your most recent fill-ups, average fuel economy, and pending sync items.

## Managing Vehicles
To achieve high OCR accuracy, the app uses **Reference Dash Photos**:
1. **Set Reference**: In the **Manage Vehicles** screen, capture a clear photo of your vehicle's dashboard.
2. **Define Crops**: Specify exact "crop rectangles" for the odometer and other important text areas.
3. **Run Discovery**: Tap **Run Discovery** to initiate a multi-engine OCR scan. The app will extract "Golden Anchors" (landmarks) to identify your car in the future.
4. **Show Landmarks / Edit OCR**: Tap **Show Landmarks** to instantly view the saved anchors. If the engine misread a critical word, tap **Edit OCR** to manually correct the text. The app uses these exact terms as a "Veto Pool" to differentiate your vehicles.

## Expenses & Reports
- Use the **New Expense Entry** screen to record non-fuel costs.
- Track fuel economy (MPG/L/100km) and spending over time using the built-in charts in the **Reports** section.

## Synchronization & Conflict Resolution
- **Google Sheets & Drive**: Optionally sync entries to a Google Sheet and back up photos to Google Drive.
- **Self-hosted sync**: For WebDAV, SFTP, MinIO/S3-compatible, EtherCalc, Baserow, and other self-hosted targets, see the setup index at [self-host/INDEX.md](self-host/INDEX.md).
- **Conflict Resolution**: If the app detects a mismatch between your local data and the remote Google Sheet, it will present the **Conflict Resolution** screen to let you choose which version to keep.
- **Last-write-wins (LWW)**: Sync merges rows by **Sync ID** using each row’s **Updated** timestamp. Deletes are rows marked **Deleted**; a newer edit on another device can restore (undelete) a row if its timestamp is newer.
- **After an app upgrade**: You may briefly see **“Updating database after upgrade…”** while the app assigns sync IDs to existing local data. This is local-only (no cloud wait) and usually finishes in a few seconds.
- **If sync is interrupted**: If the app stops mid-sync, the remote sheet may look wrong until the **next successful sync**, which re-merges and fixes the tab. On **Google Sheets**, you can also use **File → Version history** to restore an older sheet revision. Self-hosted targets may not offer the same history — see [self-host/README.md](self-host/README.md).
- **Same fill on two devices**: Entering the same fill manually on two phones, or using Quick Fill twice for one real fill, creates **two rows**. That is expected; delete or merge the extra row in the app or sheet when you notice it.
- **Sync problems**: When a destination fails, Settings shows a **red error** on the spreadsheet or photo summary, and a **problem icon** appears in the app title bar — open **Settings** to fix the destination (bad URL, sign-in, etc.). The app retries on the next scheduled sync interval.
