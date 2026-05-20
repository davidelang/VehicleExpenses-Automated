# Detailed Engineering Specification: Stateless Native Snapshot Utility (`takeSnapshot`)

## 1. Goal & Architectural Principles
The goal is to provide a stateless, high-fidelity utility to capture snapshots of an image region of interest (ROI) and return it as a Base64-encoded JPEG. This utility is physically isolated from the main OCR pipeline to prevent "ghosting" artifacts and memory bloat.

### Core Principles:
*   **Encapsulation**: The caller provides only the data source and desired constraints. All internal buffer management is handled by the utility.
*   **Statelessness**: The utility does not use global shared variables. Every call is independent and thread-safe.
*   **Resolution-First**: Resizing happens at the start of the pipeline. All subsequent operations (drawing, conversion) happen at the final target resolution.
*   **Zero-Allocation In-Loop**: The utility must utilize pre-allocated row-level buffers to avoid GC pressure.

---

## 2. Interface Definition

```kotlin
/**
 * Captures a visual snapshot of a region of interest from an arbitrary source.
 * 
 * @param source The input buffer (Bitmap, Mat, or BufferSet.Instance).
 * @param sourceRect The ROI within the source (null = full source).
 * @param targetW Max width allowed (0 = limit only by scratch buffer size).
 * @param targetH Max height allowed (0 = limit only by scratch buffer size).
 * @param annotations Graphical overlays (Lines, Rectangles) provided in source coordinates.
 * @return Base64-encoded JPEG string.
 */
fun takeSnapshot(
    source: Any,
    sourceRect: Rect?,
    targetW: Int,
    targetH: Int,
    annotations: List<SnapshotAnnotation>
): String
```

### 2.1 Constraints and Scaling
*   **Target Dimensions**: `targetW` and `targetH` represent the maximum allowable dimensions for the output.
*   **Aspect Ratio**: The original aspect ratio of the `sourceRect` is strictly preserved.
*   **Fitting**: The utility calculates the largest possible dimensions that fit within the `targetW`/`targetH` constraints while maintaining the aspect ratio and not exceeding the physical capacity of the internal scratch buffers.

---

## 3. Buffer Management & Normalization

### 3.1 Workspace Selection
The utility relies on the existing dual-buffer architecture established for each processing task. Every vehicle/row has an associated BufferSet containing a `primary` (source data) and a `scratch` (workspace) instance.
*   **Safety of Use**: The `scratch` buffer is fundamentally transient. Its state is expected to change with any function call. By using the `scratch` buffer for snapshots, we avoid allocating new high-resolution memory.
*   **Isolation**: Because the `scratch` buffer is bound to the specific row/vehicle processing task, Row #15 and Row #16 use physically different memory areas, making cross-photo data leakage (ghosting) impossible.
*   **Source Protection**: The `primary` buffer is treated as read-only during the snapshot process to ensure the integrity of the underlying image data.

### 3.2 Input Normalization (Conversion to YUV)
Regardless of the input type, all snapshots are normalized into a native YUV/NV21 format within the workspace buffer before any annotations are applied.
*   **If Source is ARGB (Bitmap)**:
    1.  The ROI is extracted and scaled directly from the source bitmap into the row-level `scratchBmp` (ARGB) using an optimized `Canvas.drawBitmap` call. This avoids a 4K memory copy by performing hardware-accelerated sampling.
    2.  The downscaled pixels in `scratchBmp` are then synchronized into the native YUV buffer via the JNI bridge.
*   **If Source is Native (Mat or BufferSet.Instance)**:
    1.  A zero-copy ROI header is created for the `sourceRect`.
    2.  OpenCV's `cv::resize` scales the ROI directly into the YUV workspace buffer at the target resolution.

---

## 4. Native Annotation Engine
Drawing overlays (rectangles, lines) is performed directly on the native YUV buffer in C++ to avoid ARGB overhead.

### 4.1 Drawing Specifications
*   **2-Pixel Alignment Rule**: To align with NV21 chroma subsampling (where one U/V pair covers a 2x2 Y block), all coordinates and **stroke widths** MUST be rounded up to the nearest multiple of 2:
    - `final_val = (scaled_val + 1) / 2 * 2`
*   **Coordinate Mapping**: Coordinates provided in the source space are scaled to the thumbnail space using the calculated ratio `(thumbnail_dim / roi_dim)` before the 2-pixel rounding is applied.

---

## 5. Direct Encoding (YUV -> Base64)
The pipeline eliminates the expensive ARGB `Bitmap` intermediate for output generation.
1.  **Compression**: Use `libjpeg-turbo` (NDK) to compress the YUV/NV21 workspace directly into a JPEG byte stream.
2.  **Base64 Result**: The JPEG bitstream is Base64 encoded and returned. No intermediate ARGB Bitmaps are created for the output.

---

## 6. Red Team Rules (Verification)
*   **Zero Ghosting**: The utility enforces an internal clear of the scratch workspace at the start of every call. A failed alignment row correctly results in a clean black snapshot.
*   **Zero Stray Buffers**: No high-resolution ARGB or Mat copies are created. All operations occur within pre-allocated scratch handles.
*   **Resolution Integrity**: By resizing before drawing, "thumbnail in the corner" artifacts are structurally prevented.

---

## 7. Hard Architectural Mandates (Stability Protection)

To prevent the recurring "improvement corruption" of the snapshot pipeline, the following rules are absolute:

*   **Rule 1: No Heap Pixel Allocation**: NO new pixel-data buffers (Bitmaps or Mats) may be allocated inside this utility. All conversions must use the pre-allocated row-local `scratchBmp` (ARGB scratchpad) or the `dashboardPool.s` (Native scratchpad).
*   **Rule 2: Native Drawing via authoritative Mat properties**: All native annotations (boxes/lines) MUST be drawn using the pre-allocated Mat properties provided by a `BufferSet.Slice` (e.g. `slice.mat`, `slice.uvMat`). Creating new manual `Mat` wrappers from raw buffers is strictly forbidden as it bypasses stride/step logic.
*   **Rule 3: Local Function Scoping (Capture)**: This function MUST be implemented as a local helper within the row processing loop to capture its required workspace buffers via closure. Global/Static access to high-res bitmaps is prohibited to prevent clobbering.
*   **Rule 4: Zero-Clobbering for UI**: Any thumbnail generation intended only for the UI (600px) MUST NOT reuse the high-resolution Native scratchpads. UI thumbnails must use isolated temporary bitmaps (pass `null` as `targetBuffer` in `createScaledBase64`).
*   **Rule 5: Fail-Fast Native Access**: If a `BufferSet` crop or handle cannot be resolved, the function MUST throw a fatal `IllegalStateException` immediately. Silently returning empty results is a protocol violation.
