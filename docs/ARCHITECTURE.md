# Vehicle Expenses Automated — Architecture

## Overview
Vehicle Expenses Automated is an Android application built with Kotlin, Jetpack Compose, and Room. It follows a "camera-first" design philosophy, focusing on automated data entry through multi-engine OCR and image alignment.

## High-Level Data Flow

## Dashboard Processing Workflow

The application follows a strict 4-stage pipeline for processing dashboard photos:

1. **Identity Phase (Vehicle Matching):**
   - **Multi-Engine Discovery:** Runs a high-speed OCR pass using multiple engines (ML Kit, Paddle-Lite, Paddle-ML-Hybrid) on the full-resolution image to discover landmark text strings and their angles.
   - **Tier 1 Veto Selection:** Compares discovered landmarks against global, engine-specific `ReferenceCache` manifests. 
     - Disqualifies vehicles if they hit "Veto Triggers" (landmarks owned by other vehicles but not them).
     - Incorporates a **1-vs-3+ Least-Vetoed Rescue Algorithm** to recover from Mutual Veto scenarios.
   - **Tiered Identity:** Further verifies using Histogram, Embedding, and Spatial Feature (ORB) consensus matching.
   - **Deskewing:** Calculates the median text angle from the discovery pass and rotates the query photo to perfectly horizontal (0°).

2. **Alignment Phase:**
   - **Strategy Execution:** Maps the deskewed query photo into the reference photo's coordinate space.
   - **Methods:** Supports dynamic, multi-engine concurrent strategies:
     - **ORB (Feature) Alignment** (4-DOF affine transform).
     - **Anchor-Triangulation Engine**: Calculates exact scale (Zoom), rotation, and Pan (tx, ty) based on unique unique text anchor vectors (Strategy A) or triangle similarity (Strategy B). It operates independently per OCR engine.

3. **Extraction Phase:**
   - **Cropping:** Using the transformation matrix from the alignment phase, it extracts specific odometer and "other text" crop boxes defined by the user in normalized coordinates (0.0 to 1.0).

4. **Crop OCR Phase (Refinement):**
   - **Preprocessing:** Generates 5 variations of the extracted crop (Raw, Grayscale, Bilateral, CLAHE, OTSU) to counteract variable lighting.
   - **Execution:** Runs the refinement OCR suite against all variations.
   - **Scoring:** Selects the best numeric reading via consensus filtering and 7-segment display logic.

### Data Persistence (Room)
- **Entities**: `Vehicle`, `FuelEntry`, `ExpenseEntry`.
- **Repositories**: Abstract local SQLite APIs.

### Background Synchronization
- **SyncWorker**: Periodically syncs data to Google Sheets via `GoogleSheetsClient` and backs up photos via `GoogleDriveProvider`.

## Component Interaction Diagram
```text
[ UI (Compose Screens) ] 
       |
[ ViewModels (Hilt) ]
       |
[ Repositories ]
       |------------------------|
[ Room DB (Local) ]       [ SyncManager ]
                                |
                        [ SyncWorker ]
                                |------------------------|
                        [ Google Sheets ]       [ Google Drive ]
```