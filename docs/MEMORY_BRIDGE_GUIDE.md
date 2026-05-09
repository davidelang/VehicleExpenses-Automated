# MemoryBridge & JNI Anchoring Architecture Guide

**ATTENTION ALL AI AGENTS:** This document details the highly fragile, critical path of the hybrid `MemoryBridge` architecture used for zero-allocation OCR refinement. Deviation from these rules will result in immediate, untraceable `SIGSEGV (0x4)` or `SIGILL` crashes on background threads.

## 1. The Core Problem: JNI Thread Context Collisions
The project uses a mix of Paddle-Lite (C++), OpenCV (C++), and ML Kit (Java). To make them fast, we use `MemoryBridge.kt` and `MemoryBridge.c` to allocate a single chunk of memory that acts as:
1.  A `java.nio.ByteBuffer` (for ML Kit / raw data).
2.  An `android.graphics.Bitmap` (for UI / ML Kit).
3.  An `org.opencv.core.Mat` (for OpenCV operations).
4.  A `FloatArray` (for Paddle-Lite tensors).

**The Fatal Flaw:**
If you create a Native Object (like `new Mat()` or `PaddlePredictor`) or initialize a JNI library on a background Coroutine thread (`Dispatchers.IO` / `DefaultDispatch`), the Android JVM's JNI transition trampoline table will not be synchronized properly with the Main thread's class loader. When the background thread attempts to cross the JNI boundary via a property lookup or a native allocation, it hits a null pointer in the trampoline and crashes with `SIGSEGV 0x4`.

## 2. The Solution: "Total Eager Anchoring"
To prevent crashes, all native objects and JNI connections MUST be established **once, eagerly, on the Main thread during app startup.**

### Rule 1: No `lazy` Delegates in Native Paths
You must **NEVER** use `val something by lazy { ... }` for any buffer, canvas, paint, or engine wrapper. Kotlin's `lazy` creates a `synchronized(lock)` block. If a background thread triggers this initialization, it will trigger the `0x4` crash.
- **DO NOT:** `val myBuffer by lazy { FloatArray(100) }`
- **DO:**
  ```kotlin
  private var _myBuffer: FloatArray? = null
  val myBuffer: FloatArray get() = _myBuffer!!
  // Initialize _myBuffer on the Main thread!
  ```

### Rule 2: Explicit Main-Thread Handshake
All libraries must be loaded in a strict, deterministic sequence on the Main thread inside `Application.onCreate()`.
```kotlin
// In VehicleExpensesApplication.onCreate():
if (!org.opencv.android.OpenCVLoader.initLocal()) { ... } // 1. OpenCV
MemoryBridge.initializeGlobalPools()                      // 2. Custom C Bridge
NativePaddleEngine.initializeGlobalBuffers(this)          // 3. Paddle Engine
```

### Rule 3: External Engine Instantiation
Do not create instances of classes that wrap native code from within their own `companion object` initialization logic. This causes a JVM recursive class-loading deadlock (Image 1 hangs silently).
- **DO NOT:** Create `val engine = NativePaddleEngine()` inside `NativePaddleEngine.initialize()`.
- **DO:** Create the instances externally in `Application.onCreate` *after* calling the static initialization method.

### Rule 4: "Safe Rigid" Backing Fields
When creating shared pools or engines, use private backing fields and public non-null accessors to maintain API compatibility without triggering JNI property lookups on background threads.

## 3. The `MemoryBridge` Object Rules
When you need a new `MemoryBridge` (e.g., for a new crop size), follow these strict lifecycle rules:

### Rule A: Pre-Allocation Only
You must pre-allocate the `MemoryBridge` before the background thread loop begins.
```kotlin
// BAD (Crashes inside experiment loop)
withContext(Dispatchers.IO) {
    val bridge = MemoryBridge(320, 128) 
}

// GOOD (Allocated on Main thread, used on IO thread)
val bridgeMap = mutableMapOf<Int, MemoryBridge>()
withContext(Dispatchers.Main) {
    bridgeMap[1] = MemoryBridge(320, 128)
}
withContext(Dispatchers.IO) {
    val bridge = bridgeMap[1]
}
```

### Rule B: Permanent `Mat` Views
OpenCV's `Mat` constructor allocates native C++ memory. You must never call `Mat()` inside the experiment loop. The `MemoryBridge` has been refactored to pre-allocate `masterMat` in its constructor. Always use `bridge.getMat()` to get the existing, anchored view.

### Rule C: Safe Copy Isolation (The GC Crash)
Android's HWUI (`libhwui.so`) and Garbage Collector aggressively manage Bitmaps. If you pass the raw `MemoryBridge` bitmap (`bridge.getBitmap()`) into a legacy function (like an HTML reporting tool or ML Kit) that accidentally recycles it or holds a view onto it, the *next* time you try to write to that pool, the JVM will crash with `SIGILL` or `SIGSEGV` in `copyPixelsFromBuffer`.

**The Isolation Pattern:**
Always create an immutable, lightweight copy of the pooled Bitmap before passing it to outside libraries.
```kotlin
// 1. Get the pool view
val monoBmp = bridge.getBitmap()

// 2. Populate the pool (e.g., via manual byte copy)
monoBmp.copyPixelsFromBuffer(wrappedBytes)
bridge.syncFromBitmap() // Tell native C to update

// 3. SAFE COPY ISOLATION
val safeInput = monoBmp.copy(Bitmap.Config.ALPHA_8, false)

// 4. Pass the copy to the engine
runOcr(safeInput)

// 5. Recycle the copy when done
safeInput.recycle()
```

## 4. Format Conversion Traps
- **`drawBitmap` for ALPHA_8:** Never use Android's `Canvas.drawBitmap` with a `ColorMatrix` to convert `ARGB_8888` to `ALPHA_8`. It is mathematically flawed for our luminance mapping and invokes heavy UI-thread UI operations on background threads.
- **The Correct Way (Manual Columnar):** Extract the ARGB pixels into an `IntArray`, bit-shift out the Red channel (which represents our grayscale luminance), and pack it into a `ByteArray` to feed the `copyPixelsFromBuffer` function.

## Summary Checklist for New Native Features
1. [ ] Is the library loaded on the Main thread?
2. [ ] Are all buffers/Bitmaps/Mats allocated *before* the Coroutine starts?
3. [ ] Did you remove all `lazy` blocks from the new code path?
4. [ ] If passing a pooled Bitmap to a new library, did you use `.copy()`?