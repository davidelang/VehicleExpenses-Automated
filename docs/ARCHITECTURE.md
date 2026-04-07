# Vehicle Expenses Automated — Architecture

## Overview
Vehicle Expenses Automated is an Android application built with Kotlin, Jetpack Compose, and Room. It follows a "camera-first" design philosophy, focusing on automated data entry through OCR and image alignment.

## High-Level Data Flow

### 1. Photo Capture & OCR
- **Input**: User takes a photo of the dashboard or a fuel pump.
- **Process**:
  1. **Image Alignment**: `ImageAlignmentUtils` aligns the photo to a vehicle-specific reference photo.
  2. **Cropping**: `PhotoAlignmentUtils` extracts relevant segments (odometer, pump display) based on stored crop rectangles.
  3. **OCR**: `OdometerOcrUtils` (using ML Kit and Tesseract) extracts numerical data from these segments.
- **Output**: Extracted odometer, gallons, and cost are populated in the UI for review.

### 2. Data Persistence (Room)
- **Entities**: `Vehicle`, `FuelEntry`, `ExpenseEntry`.
- **DAOs**: Interface with the SQLite database via Room.
- **Repositories**: Provide a clean API to ViewModels, abstracting data source details.

### 3. Background Synchronization
- **Trigger**: `SyncManager` schedules periodic or manual syncs via `WorkManager`.
- **Execution**: `SyncWorker` performs:
  1. **CSV Export**: Converts local Room data to CSV format.
  2. **Sheets Sync**: Appends or updates rows in Google Sheets via `GoogleSheetsClient`.
  3. **Drive Sync**: Backs up photos to Google Drive via `GoogleDriveProvider`.

## Component Interaction Diagram (Conceptual)
```
[ UI (Compose Screens) ] 
       |
[ ViewModels (Hilt) ]
       |
[ Repositories ]
       |------------------------|
[ Room DB (Local) ]       [ SyncManager (WorkManager) ]
                                |
                        [ SyncWorker ]
                                |------------------------|
                        [ Google Sheets ]       [ Google Drive ]
```

## Key Technologies
- **UI**: Jetpack Compose, Material3.
- **Dependency Injection**: Hilt.
- **Database**: Room.
- **Background Tasks**: WorkManager.
- **OCR**: ML Kit (Google), Tesseract (OpenCV preprocessing).
- **Networking**: Google Drive & Sheets APIs.
- **Alignment**: ORB Features & Homography (OpenCV/Android SDK).
