# BufferSet Architectural Specification

## 1. Overview
`BufferSet` is the single authority for managing high-performance native image buffers. It is designed to solve memory fragmentation and race conditions by replacing loose, independently allocated native objects (Mat, NV21, YUV handles) with a single, atomic container. 

The core philosophy is **zero-allocation iterative processing**. Instead of allocating buffers for every image, a `BufferSet` holds two internal "Instances" (Primary and Scratch). Processing routines can read from Primary and write to Scratch, then call `flip()` to atomically swap roles.

Crucially, **all buffer types (YUV, NV21, Mat) share the same underlying RAM**. A `BufferSet` allows you to access this memory as a raw YUV stream, an NV21 byte array, or an OpenCV Grayscale Mat simultaneously without any data movement or duplication. This unified memory model ensures that conversion between these types is a **zero-copy operation**—simply interpreting the same memory address through a different handle—while keeping the primary/scratch memory stable.

## 2. Interface Specification (User Manual)
The `BufferSet` object exposes handles to access the underlying Instances.

### Handle Access Pattern
Each hunk (primary/scratch/crop) provides direct access to its views via properties:
- `handle.yMat`: Returns `org.opencv.core.Mat` (Y-plane view, `8UC1`).
- `handle.uvMat`: Returns `org.opencv.core.Mat` (Interleaved UV-plane view, `8UC2`).
- `handle.yuv`: Returns a unified YUV handle encapsulating both `yMat` and `uvMat`.
- `handle.nv21`: Returns `java.nio.ByteBuffer` (Full NV21 view).

**Example Usage:**
```kotlin
// Direct access
val srcY = bufferSet.primary.yMat
val dstY = bufferSet.scratch.yMat

// Scaling color data (8UC2 allows standard resize to work for both U and V)
Imgproc.resize(srcUV, dstUV, Size(targetW/2, targetH/2), 0.0, 0.0, INTER_AREA)
```

### The Unified .yuv Handle
The `.yuv` handle is designed for external utilities (like `bitmapToYUV`) that need to operate on the full image state without knowing the internal BufferSet details.
- **Contract:** It provides both Luma (`8UC1`) and Chroma (`8UC2`) views.
- **Resizing:** When using `cv::resize` on the `.yuv` handle, the logic must independently scale the Y and UV planes to maintain the 4:2:0 subsampling geometry.

### Functions
- `resize(w, h)`: Dynamically reallocates underlying memory.
- `flip()`: Atomically swaps the active index.
- `clear()`: Zeroes Luma (`0`) and resets Chroma (`128`).
- `createCrop(x: Int, y: Int, w: Int, h: Int): Int`: Registers a persistent indexed sub-view (absolute pixels). Returns the Crop ID.
- `createCropNormalized(x: Float, y: Float, w: Float, h: Float): Int`: Registers a persistent indexed sub-view (normalized 0.0-1.0). Returns the Crop ID.
- `getCropMat(id: Int): Mat`: Retrieves the current `yMat` proxy for a managed crop.
- `getCrop(id: Int): ManagedCrop`: Retrieves the full crop object, providing access to `.yMat`, `.uvMat`, and `.yuv`.
- `releaseCrop(id: Int)`: Forcefully disarms and removes a managed crop.

### Managed Crop Lifecycle
`BufferSet` managed crops are pinned to the **Primary Instance**. 
1. When `flip()` is called, all crops are automatically re-projected onto the new Primary memory.
2. When `resize()` is called, all crops (especially Normalized ones) are re-calculated to match the new parent dimensions.
3. **2-Pixel Alignment Rule:** All crops are automatically aligned to the nearest even pixel boundary to satisfy NV21 chroma requirements.

## 3. Implementation Details (Developer/Maintainer Manual)
### Architecture
- **UnifiedHandle (C++):** A struct wrapping the raw `uint8_t*` buffer and the `cv::Mat` headers.
- **Safety Registry:** Uses a `std::set<BufferSetHandle*>` in C++ to track allocations.
- **Placement-Assignment:** When `resize` is called, we delete the old `data` and allocate new memory. We then update the `cv::Mat` headers in-place.
- **Chroma Handling:** The `uvMat` is initialized as an `8UC2` matrix pointing to the start of the chroma plane. This allows OpenCV's standard processing tools to treat the interleaved `V,U,V,U` data as a single 2-channel image.

### Internal Operational Logic: Stride-Aware Normalization
When `syncMatFromArgb` is called:
1. **Source Layout:** The hardware writes a width of `W` but a stride of `S`.
2. **The Compaction Loop:** The native code performs a row-by-row `memcpy` of length `W`, skipping padding.
3. **Final Result:** After this compaction, the `yMat` and `uvMat` handles point to perfectly aligned, contiguous data.

### Troubleshooting
- **Green/Pink Snapshots:** This usually indicates a YUV plane mismatch. Verify that the UV offset is correctly calculated as `width * height`.
- **Jagged Edges on Annotations:** Ensure the 2-pixel alignment rule is enforced for all drawing coordinates.
