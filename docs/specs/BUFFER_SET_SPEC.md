---
type: intent-spec
status: locked
ai_directive: "This is an upstream specification. DO NOT modify this document to match the codebase. If the code deviates from this spec, the code is wrong. Modifications to this file require a dedicated 'Strategy' turn and explicit user approval."
---

# BufferSet Architectural Specification

`BufferSet` is a **type**: an 8-bit **YUV** pair of full-size planes (logical Primary `.p` and Scratch `.s`) plus a crop registry. The application holds **several instances** (for example `bufferSetA` / `bufferSetB` at 4096², `recBufferSet` at 4096×48, det squares 256² / 608², `deskewBufferSetLarge` at 2048²). Each instance is independent. Which instance is used for which job is **not** defined here; see `dev-ai-interaction/research/bufferset-use.md` (non-normative).

The manager owns physical RAM. It exists to stop loose native allocations, fragmentation, and races. UV may be unused. Storing packed integers in the 8-bit Y allocation is pointer abuse (sometimes legitimate); it is **not** a BufferSet feature and is not specified here.

## 1. Logical image vs two buffers

**Preferred:** modify the image **in place** on the slice you already hold (usually `.p`).

**If in-place is not possible:** read from one slice (usually `.p`), write the modified result to the other (usually `.s`), then **`flip()`**. `flip()` is a first-class manager operation. After `flip()`, the modified image lives in the **same logical slice** you started with (as if you had mutated in place).

**If you must keep the original:** do **not** `flip()`. You are treating the BufferSet as **two separate buffers**, not one logical image. Example: `quantizeMonoInputToScratch()` writes an int8 tensor into `.s` and leaves `.p` readable.

## 2. Terminology

- **Manager (`BufferSet`)**: Owns physical RAM for both full-size planes and the crop registry.
- **Primary / Scratch**: The two full-size 8-bit YUV allocations. Logical roles `.p` / `.s` swap on `flip()`.
- **ROI / Crop**: A sub-rectangle of a **logical** slice (`.p` or `.s`). Non-contiguous view with a stride. Owned by that logical slice: after read-`.p` / write-`.s` / `flip()`, crops that were on `.p` are still on `.p`.
- **Slice**: Unified interface for a full-size plane or a crop. Any `Slice` parameter accepts either.

## 3. Syntax

### Level 1: Manager properties (`foo`)

| Syntax | Type | Description |
| :--- | :--- | :--- |
| `foo.p` / `foo.primary` | `Slice` | Current logical Primary. |
| `foo.s` / `foo.scratch` / `foo.secondary` | `Slice` | Current logical Scratch. |
| `foo.crop[id]` / `foo.c[id]` | `Slice` | Persistent managed ROI. |
| `foo.width` / `foo.height` | `Int` | **Logical** dimensions (may be smaller than allocated capacity). |

### Level 2: Manager functions

| Syntax | Description |
| :--- | :--- |
| `foo.flip()` | Swap logical P/S. Crops stay on their **logical** slice (`.p` crops remain `.p`). If `.p` is borrowed, `unborrow()` runs on `.p` **before** the swap. |
| `foo.resize(w, h)` | Set logical size. **Reallocates only to grow** past the prior max allocation; capacity is retained. ICRS crops refresh; pixel crops are released. No-op if `w`/`h` unchanged. |
| `foo.normalizeYUV()` | Pack `p.yuv` into `s` as **NV21**, then `flip()`. |
| `foo.quantizeMonoInputToScratch(tensorW, tensorH)` | Map luma `.p` → int8 in `.s` (`q = b XOR 128`). **No `flip()`** — original stays on `.p` (two-buffer mode). |
| `foo.createCrop(...)` | Alias for `foo.p.createCrop(...)`. Scratch-owned crops: `foo.s.createCrop(...)`. |
| `foo.clearCrops()` | Drop the ROI registry; do not free P/S RAM. |
| `foo.release()` | Free all RAM and clear the ROI registry. |
| `foo.borrowYuv(y, u, v, ...)` | Point **Primary** at external memory (e.g. CameraX). |
| `foo.unborrow()` | Restore **both** instances’ pointers to internal RAM. Also used automatically from `flip()` on the current `.p` if borrowed. |

### Level 3: Slice properties (`slice`)

Applies to `foo.p`, `foo.s`, and `foo.c[id]`. Layout is **YUV** in the allocated 8-bit space. **NV21** is a special case of YUV (tight pack, defined stride / alignment / plane placement). `normalizeYUV()` produces NV21. `nv21` / `nv21Mat` are valid only when that layout holds.

| Syntax | Type | Description |
| :--- | :--- | :--- |
| `slice.mat` / `slice.yMat` | `Mat` | Luma (Y) `8UC1`. |
| `slice.uvMat` | `Mat` | Chroma `8UC2` interleaved (may be unused). |
| `slice.yuv` | `YuvHandle` | Multi-plane YUV descriptor (any valid YUV in the allocation). |
| `slice.raw` | `ByteBuffer` | Luma-only 1.0× byte hunk. |
| `slice.nv21` | `ByteBuffer` | Contiguous 1.5× NV21 hunk **(full P/S only; only if NV21 layout)**. |
| `slice.nv21Mat` | `Mat` | Single Mat on that 1.5× hunk **(full P/S only; only if NV21)**. |
| `slice.width` / `slice.height` | `Int` | This slice’s dimensions. |

### Level 4: Slice functions

| Syntax | Description |
| :--- | :--- |
| `slice.createCrop(x,y,w,h,id?)` | Int = pixels; Float = ICRS. ROI is owned by **this** logical slice. Overwrites `id` if present. |
| `slice.resize(x,y,w,h)` | Crops only. Int = pixels; Float = ICRS. |
| `slice.release()` | Crops only. Remove from registry. |
| `slice.clear()` | Zero luma and set chroma to 128. |
| `slice.clearChroma()` | Chroma to 128 only. |

Full-size `.p` / `.s` cannot `resize`/`release` via `Slice`; use `BufferSet.resize` / `release`.

## 4. Behavioral rules

### A. Coordinate overloading

`createCrop` and crop `resize` overload `Int` (pixels) vs `Float` (ICRS). ICRS = radial shortest-edge from the optical center. Per-axis 0.0–1.0 is obsolete. Authoritative: `docs/specs/ISOTROPIC_COORDINATE_SPEC.md`.

### B. Nested crop flattening

`foo.c[1].createCrop(...)` creating `c[2]` stores `c[2]` as an **absolute** offset from the root origin, not as a child. Releasing `c[1]` does not release `c[2]`. Discouraged; supported for math isolation.

### C. Boundaries and even YUV 4:2:0

ROI create/resize is clamped to the parent slice. Edges round **out** to even: left/top down, right/bottom up.

### D. Resize lifecycle

`resize` changes **logical** `width`/`height`. Allocation grows only if the new size exceeds the prior max; it is not freed on shrink.

- **ICRS crops:** kept; coordinates re-evaluated.
- **Pixel crops:** released (absolute offsets would be wrong).

### E. Handle persistence

`Mat` handles (`mat`, `uvMat`) stay live across `flip()` / `resize()`; native pointers update in place. `YuvHandle` is a smart proxy: `planes` are rebuilt on access so a cached handle still sees the current logical primary after `flip()`.

**MANDATE:** Do not cache aliases to `Slice` or `Mat` (e.g. `val trialMat = odoBuffer.p.mat`). Always query at the call site (`odoBuffer.p.mat`). `flip()` may run inside callees.

### F. Borrowing

Zero-copy ingest: `.p` may wrap external memory. The external owner frees it. `flip()` **unborrows `.p` before** swapping so the next write target is internal RAM. Only Primary can be borrowed; not crops, not Scratch independently. `unborrow()` on the manager clears borrow on **both** physical instances.

### G. Threading

Image-processing stages are sequential (UI may be main thread; OpenCV/ML Kit may use worker threads). No two pipelines/snapshots overlap. `BufferSet` has no locks.

## 5. Future tasks

- [ ] **AUDIT:** Remove cached Mat/Slice aliases; use call-site queries only.
