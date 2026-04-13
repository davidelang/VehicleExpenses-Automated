# Vehicle Expenses Automated — Architecture

## Overview
Vehicle Expenses Automated is an Android application built with Kotlin, Jetpack Compose, and Room. It follows a "camera-first" design philosophy, focusing on automated data entry through OCR and image alignment.

## High-Level Data Flow

## Dashboard Processing Workflow

The application follows a strict 4-stage pipeline for processing dashboard photos:

1. **Identity Phase:**
   - **Discovery:** Run a high-speed OCR pass (currently ML Kit) to discover landmark text strings and their fine-grained angles.
   - **Identification:** Compare discovered landmarks against vehicle-specific reference manifests using **Tiered Identity** logic (Veto, Histogram, Text Agreement).
   - **Deskewing:** Calculate the median text angle from the discovery pass and rotate the query photo to perfectly horizontal (0°).

2. **Alignment Phase:**
   - **Strategy Execution:** Map the deskewed query photo into the reference photo's coordinate space.
   - **Methods:** Supports multiple concurrent strategies including **ORB (Feature) Alignment** and **Anchor Alignment** (Scale/Pan based on unique text landmarks).

3. **Extraction Phase:**
   - **Cropping:** Using the transformation matrix from the alignment phase, extract the specific odometer crop box defined in the vehicle's reference profile.

4. **Crop OCR Phase (Refinement):**
   - **Preprocessing:** Generate 5 variations of the extracted crop (Raw, Grayscale, Bilateral, CLAHE, OTSU).
   - **Multi-Engine Execution:** Run the full suite of OCR engines (Tesseract, ML Kit, Native TFLite, etc.) against all 5 variations.
   - **Scoring:** Select the best odometer reading based on engine consensus and pattern matching.

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
