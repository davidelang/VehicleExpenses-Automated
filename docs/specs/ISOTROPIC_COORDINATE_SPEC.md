---
type: intent-spec
status: locked
ai_directive: "This is an upstream specification. DO NOT modify this document to match the codebase. If the code deviates from this spec, the code is wrong. Modifications to this file require a dedicated 'Strategy' turn and explicit user approval."
---

# Specification: Isotropic Center-Relative Space (ICRS)

## 1. The Problem Space

### 1.1 The Anisotropic Flaw
The current coordinate architecture normalizes Region of Interest (ROI) and landmark coordinates independently by their respective axes:
*   `X_norm = X_pixel / Image_Width`
*   `Y_norm = Y_pixel / Image_Height`

This creates an **anisotropic** coordinate space. If an image's aspect ratio changes (e.g., from 4:3 to 16:9), the physical distances between landmarks are mathematically squashed or stretched. A rigid Affine solver (`EstimateAffinePartial2D`) cannot calculate a valid transformation matrix between a 4:3 point cloud and a differently stretched 16:9 point cloud, leading to failure or severe distortion.

### 1.2 Constraint: OCR Rotation Tolerance
Optical Character Recognition (OCR) engines perform poorly on slanted text, frequently causing overlapping bounding boxes across multiple lines. Therefore, text must be physically rotated to horizontal *before* targeted OCR or final ROI extraction occurs. This invalidates approaches that simply extract a rotated bounding box from the raw image.

### 1.3 Constraint: Resolution Independence
A solution cannot rely on warping the New Image to exactly match the pixel resolution of the Reference Image. Doing so couples geometry (aspect ratio) with density (resolution). Upscaling a low-res image wastes memory, and downscaling a high-res image destroys the detail needed for the final OCR pass.

---

## 2. The Architecture: Isotropic Center-Relative Space (ICRS)

To decouple geometry from resolution and aspect ratio, all persistent coordinates MUST be defined radially from the image's optical center, using a uniform scalar derived from the image's shortest edge.

### 2.1 Coordinate Normalization Equations
Given an image of `Width` and `Height`:

1.  **Find the Uniform Scalar (S):**
    `S = min(Width, Height)`
2.  **Convert Pixel to ICRS:**
    `ICRS_X = (Pixel_X - (Width / 2.0)) / S`
    `ICRS_Y = (Pixel_Y - (Height / 2.0)) / S`
3.  **Convert ICRS to Pixel:**
    `Pixel_X = (ICRS_X * S) + (Width / 2.0)`
    `Pixel_Y = (ICRS_Y * S) + (Height / 2.0)`

### 2.2 Mathematical Characteristics
*   **The Origin `(0.0, 0.0)`:** Always represents the exact optical center of the image.
*   **The Short Edge:** Will always span exactly the range `[-0.5, 0.5]`.
*   **The Long Edge:** Will exceed the `±0.5` bounds (e.g., in a 16:9 image, the X-axis spans roughly `[-0.88, 0.88]`). This "overflow" is intended behavior and mathematically correct.
*   **Physical Purity:** A physical shape (e.g., a circular speedometer) retains its mathematical proportions regardless of the camera's aspect ratio.

---

## 3. The Pipeline Workflow

### 3.1 Step 1: Deskew (The "Working Canvas")
Because of the OCR Rotation Tolerance constraint (1.2), the first step is to level the image.
1.  Run "Blind OCR" on the raw New Image to extract text bounding-box angles.
2.  Calculate the global skew angle.
3.  Rotate the entire image buffer to zero out this angle. This creates the **"Working Canvas."**
    *   *Corner Case:* The Working Canvas will likely have different `Width` and `Height` dimensions than the raw image due to the expanded bounding box required to hold the rotated corners. ICRS math easily absorbs this dimension change.

### 3.2 Step 2: Discovery and Conversion
1.  Run "Targeted OCR" on the horizontal Working Canvas to locate the vehicle's unique landmarks.
2.  Convert the pixel coordinates of these landmarks into ICRS space, using the `Width` and `Height` of the **Working Canvas**.

### 3.3 Step 3: Affine Resolution
1.  Retrieve the "Golden State" Reference Landmarks from the database (which are stored in ICRS).
2.  Pass both point clouds (Reference ICRS and Discovered ICRS) into the Affine solver.
3.  Because both point clouds are leveled and isotropic, the solver effectively only needs to calculate the Translation (offset) and Uniform Scale difference between the Reference view and the New view.

### 3.4 Step 4: Isotropic ROI Extraction
1.  Retrieve the "Golden State" Reference ROI bounding box from the database (stored in ICRS).
2.  Apply the Affine solver's transform matrix to the Reference ROI's ICRS coordinates.
3.  Convert the resulting transformed ICRS coordinates back into Pixels, using the dimensions of the **Working Canvas**.
4.  Perform a simple, non-rotated rectangular crop on the Working Canvas. (No rotation is necessary because the Working Canvas was already leveled in Step 1).

---

## 4. Limitations & Future Proofing

### 4.1 Keystone / Perspective Distortion
This architecture explicitly relies on Rigid/Affine transformations (Translation, Rotation, Uniform Scale). It does not currently address severe Keystone (perspective) distortion (e.g., a photo taken at a steep angle from the side window).

*   **Future Upgrade Path:** If Keystone correction becomes necessary, the ICRS mathematical foundation remains perfectly valid. The system upgrade would only require replacing the `EstimateAffinePartial2D` solver with a `findHomography` solver. The normalization equations and ROI extraction logic would remain unchanged.
