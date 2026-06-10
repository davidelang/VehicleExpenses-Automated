---
type: intent-spec
status: locked
ai_directive: "This is an upstream specification. DO NOT modify this document to match the codebase. If the code deviates from this spec, the code is wrong. Modifications to this file require a dedicated 'Strategy' turn and explicit user approval."
---

# BufferSet Architectural Specification (Phase 25)

## 1. Overview
`BufferSet` is the single authority for managing high-performance native image buffers. It is designed to solve memory fragmentation and race conditions by replacing loose, independently allocated native objects with a single, atomic container. 

The core philosophy is **zero-allocation iterative processing**. Processing routines read from the Primary instance and write to the Scratch instance, then call `flip()` to atomically swap roles.

## 2. Terminology
- **Manager (`BufferSet`)**: The root object that owns physical RAM allocations.
- **Primary/Scratch Buffer**: The full-size physical memory blocks. Contiguous.
- **ROI (Region of Interest)**: A specific sub-section of an image buffer, also known as a **Crop**. ROIs are non-contiguous views with a stride.
- **Slice**: The unified interface representing either a Primary/Scratch Buffer or a Crop. Any function taking a `Slice` can be passed the full buffer or a specific crop.

## 3. Syntax Structure

### Level 1: Manager Properties (`foo`)
| Syntax | Type | Description |
| :--- | :--- | :--- |
| `foo.p` / `foo.primary` | `Slice` | Current logical Primary Buffer. |
| `foo.s` / `foo.scratch` / `foo.secondary` | `Slice` | Current logical Scratch/Secondary Buffer. |
| `foo.crop[id]` / `foo.c[id]` | `Slice` | Keyed access to a persistent managed ROI. |
| `foo.width` / `foo.height` | `Int` | Physical dashboard buffer dimensions. |

### Level 2: Manager Functions
| Syntax | Description |
| :--- | :--- |
| `foo.flip()` | Atomically swaps P/S roles. All ROIs mathematically re-project onto the new Primary RAM. |
| `foo.resize(w, h)` | Reallocates P/S RAM. **Normalized ROIs are preserved**; Pixel ROIs are released. |
| `foo.normalizeYUV()` | Packs `p.yuv` into `s` (standard NV21 layout), then automatically calls `flip()`. |
| `foo.createCrop(...)` | Convenience alias for `foo.p.createCrop(...)`. |
| `foo.release()` | Destroys all RAM and clears the ROI registry. |

### Level 3: Slice Properties (`slice`)
*(Applies to `foo.p`, `foo.s`, and `foo.c[id]`)*
| Syntax | Type | Description |
| :--- | :--- | :--- |
| `slice.mat` / `slice.yMat` | `Mat` | Luma (Y) view (`8UC1`). |
| `slice.uvMat` | `Mat` | Chroma (UV) view (`8UC2` Interleaved). |
| `slice.nv21` | `ByteBuffer` | Contiguous 1.5x Byte hunk **(Primary/Scratch Buffers only)**. |
| `slice.raw` | `ByteBuffer` | Luma-only 1.0x Byte hunk. |
| `slice.nv21Mat` | `Mat` | Single Mat view of 1.5x RAM **(Primary/Scratch Buffers only)**. |
| `slice.yuv` | `YuvHandle`| Industry-standard multi-plane descriptor. |
| `slice.width` / `slice.height` | `Int` | Dimensions of this specific Slice. |

### Level 4: Slice Functions
| Syntax | Description |
| :--- | :--- |
| `slice.createCrop(x,y,w,h,id?)` | Overloaded (Int/Float). Registers an ROI relative to this slice. Overwrites if `id` exists. |
| `slice.resize(x,y,w,h)` | Overloaded (Int/Float). Updates coordinates/size of this ROI. **(Crops only)**. |
| `slice.release()` | Removes this ROI from the registry. **(Crops only)**. |
| `slice.clear()` | Zeroes Luma AND resets Chroma to 128. |
| `slice.clearChroma()` | Resets only the Chroma (UV) to 128. |

### Level 5: Borrowing Functions (Primary Buffer Only)
| Syntax | Description |
| :--- | :--- |
| `foo.borrowYuv(y, u, v, ...)` | Overrides the physical pointers of the Primary Buffer to target external memory (e.g., CameraX `ImageProxy`). |
| `foo.unborrow()` | Resets Primary Buffer pointers to internal RAM. **Note: This is automatically called by `foo.flip()` if a borrow is active.** |

## 4. Behavioral Rules

### A. Coordinate Overloading
Kotlin supports full parameter-type overloading. `createCrop` and `resize` natively support both absolute pixel offsets (`Int`) and normalized offsets (`Float` from 0.0 to 1.0). 

### B. Nested Crop Flattening
If you call `foo.c[1].createCrop(...)` to create `foo.c[2]`, the API performs **Coordinate Flattening**. `c[2]` is stored in the registry as an absolute offset from the *root buffer origin*, not as a child. Releasing `c[1]` has zero effect on `c[2]`. 
*Note: This feature can be dangerous and lead to confusing state management where a "child" outlives its "parent". It is supported for strict math isolation, but generally discouraged. Use at your own risk.*

### C. Boundary Enforcement & YUV Alignment
- **Clamping:** ROI creation/resizing is safely clamped to the boundaries of the parent slice.
- **Round Out:** To satisfy YUV 4:2:0 subsampling geometry, all ROI boundaries are rounded **outward** to a multiple of 2:
    - **Left and Top:** Rounded **DOWN** to nearest even number.
    - **Right and Bottom:** Rounded **UP** to nearest even number.

### D. Resize Lifecycle
When `foo.resize()` changes the physical dimensions of the buffer:
- **Normalized ROIs:** Automatically recalculated to stretch with the new physical dimensions.
- **Pixel ROIs:** Automatically released. Their absolute offsets are no longer valid, preventing memory corruption.

### E. Handle Persistence & Smart Proxies
To prevent JVM crashes and memory corruption during `flip()` or `resize()` operations, `BufferSet` implements a persistence layer for its handles:
- **Mat Persistence:** OpenCV `Mat` objects (`mat`, `uvMat`) for both base buffers and crops are **persistent**. When a flip or resize occurs, the underlying C++ data pointers are updated in-place via JNI.
- **YuvHandle Smart Proxy:** The `YuvHandle` is a **Smart Proxy**. While the `YuvHandle` object itself can be cached, its `planes` property dynamically generates fresh `ByteBuffer` slices at the moment of access. This ensures that even if a `YuvHandle` is cached across a `flip()`, accessing its data will always yield pointers to the correct, current Primary RAM.

**MANDATE:** Caching or assigning local JVM/native references (aliases) to active `Slice` or `Mat` instances (e.g., `val trialMat = odoBuffer.p.mat`) is **STRICTLY FORBIDDEN** across the codebase. Assigning local aliases to save typing or shorten code is unacceptable. Because functions or operations may invoke `flip()` internally now or in future refactoring, local aliases create severe pointer stability risks. You must always query handles dynamically (e.g., `odoBuffer.p.mat`) at the call-site.

### F. Buffer Borrowing Lifecycle
`BufferSet` supports **Zero-Copy Ingestion** via Buffer Borrowing. This allows the Primary (`p`) buffer to temporarily wrap memory owned by the OS or another subsystem.
- **External Authority:** While borrowed, the external provider maintains ownership of the memory. The `BufferSet` must not attempt to free this memory.
- **Automatic Unborrow Safeguard:** To prevent data corruption or accidental writes to external memory, the `flip()` operation includes a **Strict Safeguard**: if the `BufferSet` is currently in a borrowed state, it MUST automatically execute `unborrow()` *before* swapping the Primary and Scratch roles. This ensures the Scratch buffer (the target for the next write operation) is always backed by safe, internal RAM.
- **Scope:** Borrowing is only supported on the Primary Buffer. Crops and Scratch buffers cannot be independently borrowed.

## 5. Future Tasks
- [ ] **AUDIT:** Audit the entire codebase to locate and remove any cached Mat/Slice pointer aliases (e.g., `trialMat`), replacing them with dynamic call-site queries.

