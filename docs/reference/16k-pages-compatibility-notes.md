# Reference Notes: 16KB Page Size Compatibility

This document lists the compatibility issues, build configurations, and JNI/memory architecture risks identified during the 16KB page size research. 

---

## 1. Summary of 16KB Page Size Support
Android 15+ (API 35+) devices and emulators running on 16KB memory page kernels require all native shared libraries (`.so` files) to have their ELF load segments aligned to a 16KB boundary (`0x4000`). If any native library inside the APK has a 4KB segment alignment (`0x1000`), the operating system will fail to load it, triggering an `UnsatisfiedLinkError` and causing the application to crash.

To achieve 16KB page size compatibility, the build process must satisfy two core requirements:
1. **Compilation:** Libraries must be compiled using Android NDK r27+ or r28+ (which default to 16KB ELF alignment) or linked explicitly with the linker flag `-Wl,-z,max-page-size=16384`.
2. **Packaging:** Legacy packaging must be disabled (`useLegacyPackaging = false` in `build.gradle`) to store native libraries uncompressed, allowing the Android Gradle Plugin (AGP 8.5.1+) or `zipalign` tool to pack and align them correctly.

### App status (product / Play 64-bit path)
* **`app/build.gradle.kts`:** `useLegacyPackaging = false`; CMake arg `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`.
* **`app/src/main/cpp/CMakeLists.txt`:** `add_link_options(-Wl,-z,max-page-size=16384)` for app natives (all ABIs).
* **Verified:** debug APK x86_64 + arm64 product SOs **Stored** + LOAD Align **0x4000**; First 10 align+pump before/after on emulator-5554 PASS (scratch `16k-5554-first10-20260805`).
* **Still open (armv7 purity only):** OpenCV armv7 pin; paddle armv7 max-page; NDK armv7 `libc++_shared` may remain 4KB (Play’s hard gate is 64-bit).

---

## 2. Dependency Limitations & Upstream Status

### OpenCV Android SDK — **must pin-build** (not an upgrade)
* **Official Prebuilts:** Maven Central / GitHub releases (through 5.x) still ship **4KB** LOAD alignment. Version bumps alone do not fix this.
* **Resolution:** Pin-build from source (`third_party/opencv`, NDK r28c, `-Wl,-z,max-page-size=16384`). App uses fat `libopencv_java4.so` under `jniLibs` for **arm64-v8a + x86_64**; **armeabi-v7a 16KB OpenCV still TODO**.

### CameraX — **upgrade** (not like OpenCV)
* **Not** a rebuild-from-source problem. Google ships CameraX AARs with natives; Jetpack rebuilds them regularly.
* **Resolution:** Use CameraX **≥ 1.4** (community-confirmed for `libimage_processing_util_jni`); app targets **1.6.1** stable. Do not fork CameraX.

### ML Kit text-recognition — **upgrade / wait**, not pin-build
* Bundled `com.google.mlkit:text-recognition` latest published **16.0.1** (as of ML Kit release table). Arm64 native is already **0x4000** with that version; armv7 may still be 4KB.
* Other ML Kit modules (e.g. digital-ink 19.0.0) explicitly noted 16KB in release notes; text-recognition has not needed a newer artifact than 16.0.1 yet.
* **Unlike OpenCV:** no VE pin rebuild of ML Kit. Bump when Google publishes a newer text-recognition; Play’s hard gate is **64-bit** 16KB.

### Go-based Rclone (`librclone.aar` / photo pin)
* **Build System:** `third_party/rclone/` (`libpin.toml`, `scripts/build-photo-aar.sh`, Docker).
* **Resolution:** `CGO_LDFLAGS=-Wl,-z,max-page-size=16384` before `gomobile bind` — all product ABIs **0x4000**.

### libc++_shared
* **Do not** hand-copy into `jniLibs`. With `ANDROID_STL=c++_shared`, AGP packages the NDK STL per ABI (NDK r28c: arm64/x86_64 already 16KB; armv7 NDK STL is still 4KB — acceptable for Play’s 64-bit-only 16KB rule).

### ABI Compatibility
* Product ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`. Pin-built OpenCV currently arm64+x86_64; armv7 OpenCV 16KB still open.

---

## 3. Low-Level JNI & Memory Architecture Risks
Our `BufferSet` library performs direct native-heap manipulation and JNI calls that are highly version-sensitive. Upgrading OpenCV (e.g. from 4.10.0 to 5.x) carries high risks of memory instability:

### JNI Reflection (`nativeDisarmMat`)
To prevent the JVM finalizer from double-deleting our custom memory buffers owned by C++ `BufferSetHandle`, our JNI library accesses private fields inside OpenCV Java's wrapper class:
```cpp
jfieldID nativeObjField = env->GetFieldID(matClass, "nativeObj", "J");
if (nativeObjField != nullptr) env->SetLongField(matObj, nativeObjField, 0);
```
* **Risk:** If OpenCV 5.x refactors this class (e.g. renaming the private field `nativeObj` or encapsulating the pointer), the JNI reflection will return `NULL`. This will silently fail to clear the field, causing JVM finalizers to double-free C++ heap addresses, resulting in catastrophic SegFaults.

### Direct `Mat` Structure Casting
Our C++ crop slices are bound by casting the `nativeObj` address to a C++ pointer:
```cpp
cv::Mat& cropMat = *(cv::Mat*)cropMatPtr;
```
* **Risk:** This compiler cast expects the memory layout of the `cv::Mat` struct inside our JNI code to match *exactly* with the structure inside `libopencv_java.so`. If the structural alignment or size of `cv::Mat` changes across versions, casting it will cause our code to read garbage offsets, leading to silent memory corruption and crashes.

---

## 4. Re-evaluation Plan
When revisiting this requirement in the future, check if:
1. OpenCV officially publishes 16KB-aligned Android artifacts on Maven Central under the `org.opencv` group.
2. The low-level JNI field naming of `nativeObj` remains stable, or if `nativeDisarmMat` needs to be rewritten to interface with the new wrapper design.
3. CMake targets inside `app/src/main/cpp/CMakeLists.txt` are fully compatible with C++17 compilers (mandated by newer OpenCV editions).
