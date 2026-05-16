# BufferSet Architectural Specification

## 1. Overview
`BufferSet` is the single authority for managing high-performance native image buffers. It is designed to solve memory fragmentation and race conditions by replacing loose, independently allocated native objects (Mat, NV21, YUV handles) with a single, atomic container. 

The core philosophy is **zero-allocation iterative processing**. Instead of allocating buffers for every image, a `BufferSet` holds two internal "Instances" (Primary and Scratch). Processing routines can read from Primary and write to Scratch, then call `flip()` to atomically swap roles.

Crucially, **all buffer types (YUV, NV21, Mat) share the same underlying RAM**. A `BufferSet` allows you to access this memory as a raw YUV stream, an NV21 byte array, or an OpenCV Grayscale Mat simultaneously without any data movement or duplication. This unified memory model ensures that conversion between these types is a **zero-copy operation**—simply interpreting the same memory address through a different handle—while keeping the primary/scratch memory stable.

## 2. Interface Specification (User Manual)
The `BufferSet` object exposes handles to access the underlying Instances.

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
- `createCrop(x: Int, y: Int, w: Int, h: Int): Int`: Registers a persistent indexed sub-view (absolute pixels). Returns the Crop ID.
- `createCropNormalized(x: Float, y: Float, w: Float, h: Float): Int`: Registers a persistent indexed sub-view (normalized 0.0-1.0). Returns the Crop ID.
- `getCropMat(id: Int): Mat`: Retrieves the current `Mat` proxy for a managed crop.
- `releaseCrop(id: Int)`: Forcefully disarms and removes a managed crop.

### Managed Crop Lifecycle
`BufferSet` managed crops are pinned to the **Primary Instance**. 
1. When `flip()` is called, all crops are automatically re-projected onto the new Primary memory.
2. When `resize()` is called, all crops (especially Normalized ones) are re-calculated to match the new parent dimensions.
3. Managed crops MUST NOT be cached. Always query `getCropMat(id)` immediately before use.

## 3. Examples of Use
### Short-Term Pixel Crop (Discovery Stage)
```kotlin
// Create a crop for a specific detected box
val id = bufferSet.createCrop(detectedL, detectedT, detectedW, detectedH)

// Process the crop
Imgproc.GaussianBlur(bufferSet.getCropMat(id), bufferSet.getCropMat(id), Size(3.0, 3.0), 0.0)

// Explicitly clean up when finished
bufferSet.releaseCrop(id)
```

### Long-Term Normalized Crop (Odometer Stage)
```kotlin
// Create once at start of experiment (based on vehicle metadata)
val odoId = bufferSet.createCropNormalized(0.1f, 0.4f, 0.8f, 0.2f) 

// In the iterative loop...
// The crop automatically stays pinned to the odometer region even if the parent flips or resizes.
OdometerOcrUtils.runDetection(bufferSet.getCropMat(odoId))
```

## 4. Kotlin Integration (`BufferSet.kt`)
The Kotlin API provides a safe, idiomatic wrapper around the C++ lifecycle.

### 4.1 The Proxy Disarm Architecture
To maintain the "Single Authority" mandate without crashing the JVM, `BufferSet` uses a **Proxy Disarm** strategy for its OpenCV `Mat` views.
1. When Kotlin requests `hunk.mat`, a lightweight Java `Mat` proxy object is created, pointing to the C++ memory.
2. Because OpenCV's Java finalizer automatically calls `delete` on its pointers during Garbage Collection, `BufferSet` must prevent this to avoid double-free crashes.
3. When `BufferSet.release()` or `BufferSet.resize()` is called, a JNI reflection function (`nativeDisarmMat`) forcefully overwrites the Java `Mat` object's internal `nativeObj` pointer to `0`.
4. When the GC eventually runs, the OpenCV finalizer safely ignores the `0` pointer, leaving the C++ explicit `delete` as the sole authority over memory destruction.

### 4.2 Usage Anti-Pattern: Caching Proxies
Because `BufferSet` can dynamically mutate the underlying C++ pointer (via `resize`), any Kotlin variable holding a `Mat` proxy is inherently volatile. 

**MANDATE:** You MUST NOT cache `Mat` objects returned by `BufferSet` in long-lived variables or class properties. 

**BAD (Volatile):**
```kotlin
val myMat = bufferSet.primary.mat
bufferSet.resize(newW, newH)
Imgproc.GaussianBlur(myMat, myMat, ...) // FATAL: myMat was disarmed by resize!
```

**GOOD (Safe):**
Always query the view on-demand from the Instance.
```kotlin
bufferSet.resize(newW, newH)
Imgproc.GaussianBlur(bufferSet.primary.mat, bufferSet.primary.mat, ...)
```
*Note: Storing the `Mat` in a local `val` within a single function scope is acceptable, provided no `resize()` or `flip()` operations occur within that scope.*

## 5. Implementation Details (Developer/Maintainer Manual)
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
