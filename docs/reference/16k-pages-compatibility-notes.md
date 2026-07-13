# Reference Notes: 16KB Page Size Compatibility

This document lists the compatibility issues, build configurations, and JNI/memory architecture risks identified during the 16KB page size research. 

---

## 1. Summary of 16KB Page Size Support
Android 15+ (API 35+) devices and emulators running on 16KB memory page kernels require all native shared libraries (`.so` files) to have their ELF load segments aligned to a 16KB boundary (`0x4000`). If any native library inside the APK has a 4KB segment alignment (`0x1000`), the operating system will fail to load it, triggering an `UnsatisfiedLinkError` and causing the application to crash.

To achieve 16KB page size compatibility, the build process must satisfy two core requirements:
1. **Compilation:** Libraries must be compiled using Android NDK r27+ or r28+ (which default to 16KB ELF alignment) or linked explicitly with the linker flag `-Wl,-z,max-page-size=16384`.
2. **Packaging:** Legacy packaging must be disabled (`useLegacyPackaging = false` in `build.gradle`) to store native libraries uncompressed, allowing the Android Gradle Plugin (AGP 8.5.1+) or `zipalign` tool to pack and align them correctly.

---

## 2. Dependency Limitations & Upstream Status

### OpenCV Android SDK
* **Official Prebuilts:** Standard prebuilt binaries published to Maven Central (`org.opencv:opencv`) and standard GitHub releases (up to `5.0.0`) **do not support 16KB alignment out of the box**. They are compiled on legacy CI builders (using NDK r25 or older) to maximize compatibility with older devices.
* **OpenCV 5.x Status:** Although source-level fixes were introduced in the 4.12.0+ branch, the official prebuilt 5.x packages still default to 4KB alignment. Simply upgrading the package version will not resolve the warning.
* **Resolution:** OpenCV must be compiled from source using NDK r28c and target CMake flag `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`.

### Go-based Rclone (`librclone.aar` / full backends)
* **Build System:** The Go build workspace and scripts (`build_lite.sh`) are located in the sandbox under `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/rclone-build/`.
* **Resolution:** Since Go runtime is page-size agnostic but `gomobile bind` relies on `cgo` utilizing the system's C linker, alignment must be forced by setting:
  ```bash
  export CGO_LDFLAGS="-Wl,-z,max-page-size=16384"
  ```
  before executing the `gomobile bind` compilation.

### ABI Compatibility
* **x86 & x86_64:** These architectures must not be dropped. They are required for emulator support. Both OpenCV and Rclone must be built for `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `x86`.

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
