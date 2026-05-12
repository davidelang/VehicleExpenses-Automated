# MLKitEngine Iterative OCR Algorithm Specification

## Foundational Mandates
- **ZERO RUNTIME ALLOCATION:** No `Mat()` or `Bitmap()` creations in the execution loop.
- **HANDLE PURITY:** Use direct Mat or NV21 handles from pre-allocated pools.
- **ALREADY GRAYSCALE:** Source data is already grayscale; extract only the luminance channel (e.g., Red byte in ARGB).
- **ASPECT RATIO:** ROI and Target Bridges have identical shapes; only uniform scaling is permitted.

## Algorithm Steps

### Step 1: Format Identification
Inspect the provided `masterBuffer: Any` to determine the pixel format (e.g., Bitmap ARGB_8888).

### Step 2: Resource Acquisition
Identify the two per-vehicle `MemoryBridge` crop buffers (primary and scratch) designated for the active vehicle.

### Step 3: ROI Definition
Map the odometer crop coordinates (left, top, right, bottom) to the input buffer dimensions.

### Step 4: Iterative Preprocessing Loop
Execute a loop through the defined preprocessing stages:
1.  **Standard** (None)
2.  **80% Stretch** (Normalization)
3.  **80% Stretch + Bilateral Filter**
4.  **Bilateral Filter + 80% Stretch**

**Inside the loop for EACH stage:**

#### Step 4.1: Pristine Refresh (Extraction)
Re-extract the odometer ROI from the `masterBuffer` directly into the per-vehicle primary `MemoryBridge`. 
- This ensures every pass starts with an identical, clean baseline.
- **Rules:** Red channel copy for ARGB; uniform scaling only; no color manipulation.

#### Step 4.2: Preprocessing Application
Apply the OpenCV preprocessing filter(s) for the current stage.
- **Preference:** Modify the primary vehicle bridge in-place.
- **Fallback:** Use the per-vehicle scratch `MemoryBridge` if a secondary buffer is required for a specific filter.

#### Step 4.3: Scale-to-Fit (Recognition)
Copy and scale from the processed vehicle bridge into the global **320x48 recognition buffer** (`MemoryBridge.pool320x48`).
- **Mandate:** Maintain strict aspect ratio. Anchor at (0,0) and zero-out (black) any remaining padding in the 320x48 buffer.

#### Step 4.4: ML Kit Recognition
Pass the 320x48 `MemoryBridge` item to ML Kit to extract text.

#### Step 4.5: Text Sanitization
Apply the existing **7-Segment Display Cleanup** function to the extracted text.

#### Step 4.6: Diagnostic Snapshot
Use the `takeSnapshot` routine to capture the current state of the odometer ROI for visual reporting.

#### Step 4.7: Data Aggregation
Prepare the JSON section for this stage, capturing:
- Extracted text (pre and post cleanup).
- Granular timing (extraction, preprocessing, scaling, recognition).

### Step 5: Results Consolidation
After the loop completes, consolidate the text data, Base64 snapshots, and JSON metadata into an `OcrHarnessResult` and return it.

