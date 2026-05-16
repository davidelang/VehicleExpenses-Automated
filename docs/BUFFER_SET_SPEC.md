# BufferSet Architectural Specification

## 1. Overview
`BufferSet` is the single authority for managing high-performance native image buffers. It is designed to solve memory fragmentation and race conditions by replacing loose, independently allocated native objects (Mat, NV21, YUV handles) with a single, atomic container. 

The core philosophy is **zero-allocation iterative processing**. Instead of allocating buffers for every image, a `BufferSet` holds two internal "Hunks" (Primary and Scratch). Processing routines can read from Primary and write to Scratch, then call `flip()` to atomically swap roles.

Crucially, **all buffer types (YUV, NV21, Mat) share the same underlying RAM**. A `BufferSet` allows you to access this memory as a raw YUV stream, an NV21 byte array, or an OpenCV Grayscale Mat simultaneously without any data movement or duplication. This unified memory model ensures that conversion between these types is a **zero-copy operation**—simply interpreting the same memory address through a different handle—while keeping the primary/scratch memory stable.

## 2. Interface Specification (User Manual)
The `BufferSet` object exposes handles to access the underlying Hunks.

### Handle Access Pattern
Each hunk (primary/scratch/crop) provides direct access to its views via properties:
- `handle.mat`: Returns `org.opencv.core.Mat` (Y-plane view).
- `handle.nv21`: Returns `java.nio.ByteBuffer` (NV21 view).
- `handle.yuv`: Returns `YUVHandle` (Metadata/Buffer descriptor).

**Example Usage:**
```kotlin
// Direct access
val src = bufferSet.primary.mat
val dst = bufferSet.scratch.mat

// Ingestion
bufferSet.scratch.yuv.yBuffer.put(frameData)
```


### YUVHandle Descriptor
A `YUVHandle` provides the metadata necessary for hardware-level ingestion:
- **Buffers:** `yBuffer`, `uBuffer`, `vBuffer` (DirectByteBuffers).
- **Metadata:** `yRowStride`, `yPixStride`, `uRowStride`, `uPixStride`, `vRowStride`, `vPixStride`.
- **Contract:** A producer (Camera/Codec) writes to these buffers adhering to the strides. `normalizeYUV` then consumes this handle to compact the data into standard layout.

### Functions
- `resize(w, h)`: Dynamically reallocates underlying memory.
- `flip()`: Atomically swaps the active index.
- `normalizeYUV(handle: YUVHandle)`: Compacts raw hardware YUV data from the handle into standard NV21/CV_8 layout.
- `clear()`: Zeroes all pixels.
- `createCrop(x: Int, y: Int, w: Int, h: Int)`: Registers a persistent indexed sub-view (top-left x, y, width, height).
- `createCropNormalized(x: Float, y: Float, w: Float, h: Float)`: Registers a persistent indexed sub-view (normalized 0.0-1.0 top-left x, y, width, height).

## 3. Examples of Use
### Zero-Copy Ingestion
```kotlin
val bufferSet = BufferSet(w, h)
// Write hardware data directly to a hunk
bufferSet.scratch.populateFromHardware(...) 

// Manually normalize to standard layout
bufferSet.normalizeYUV(bufferSet.scratch.yuv)
```

### Processing Pipeline with Flip
```kotlin
// Direct access to handles without intermediate variables
OdometerOcrUtils.applyFilters(bufferSet.primary.mat, bufferSet.scratch.mat)

// Atomic flip
bufferSet.flip() 
```

### Creating and Using a Persistent Crop
```kotlin
// Defined once
bufferSet.createCropNormalized(0.1f, 0.2f, 0.5f, 0.2f) 

// OCR reads directly from crop handle
OdometerOcrUtils.runDetection(bufferSet.crop[0].mat)
```

## 4. Implementation Details (Developer/Maintainer Manual)
### Architecture
- **UnifiedHandle (C++):** A POD struct wrapping the raw `uint8_t*` buffer and the `cv::Mat`. The C++ handle address is stable for the life of the `BufferSet`.
- **Safety Registry:** Uses a `std::set<UnifiedHandle*>` in C++ to track allocations. Any JNI call for a handle not in the registry is rejected, preventing "Zombie Pointer" double-frees.
- **Placement-Assignment:** When `resize` is called, we delete the old `data` and allocate new memory. We then update the `cv::Mat` in-place using `*yMat = Mat(...)`. This keeps the `yMat` object instance address constant, ensuring Kotlin-side `Mat` objects don't dangle.
- **Mutexing:** All `resize`, `flip`, and `createCrop` operations are protected by a `kotlinx.coroutines.sync.Mutex`.

### Internal Operational Logic: Stride-Aware Normalization
When `normalizeYUV(handle)` is called:
1. **Source Layout:** The hardware writes a width of `W` but a stride of `S` (where `S > W`), leading to skew if copied linearly.
2. **The Compaction Loop:**
   - The native code performs a row-by-row `memcpy` of length `W`, skipping the `(S - W)` padding bytes.
   - This "pulls" the skewed image into a contiguous layout.
3. **Interleaving:** If the chroma planes (U/V) are interleaved (e.g., `pixelStride = 2`), the loop correctly pulls the U and V samples into the standard NV21 `V, U, V, U` order.
4. **Final Result:** After this compaction, the `CV_8` Mat handle now points to a perfectly aligned, contiguous Y-plane that `Paddle` and `ML Kit` expect.

### Normalized Crop Persistence
- Crops created via `createCropNormalized` store the coordinate ratios.
- When `resize()` is called, the parent `BufferSet` iterates through all registered crops and recomputes their internal `submat` ROI based on the new `w` and `h`. This ensures that crops are always pinned to the same dashboard location, regardless of the buffer size.

### Troubleshooting
- **Skewed OCR results:** Verify the `rowStride` and `pixelStride` passed to `normalizeYUV`. If the image is "leaning," the `memcpy` is likely reading the padding bytes.
- **Color distortion/Mono failure:** If the grayscale is noisy, verify that the U/V planes are being cleared to `128` (or correctly compacted) in the `normalizeYUV` logic.
