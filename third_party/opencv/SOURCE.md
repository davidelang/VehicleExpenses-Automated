# OpenCV third_party pin

## Host
- `~/git/opencv` tag/branch **4.10.0**
- Build script: `~/git/opencv/ve-build/build-android-16k.sh` (uses SDK cmake + NDK; 16KB max-page-size)
- VE delegate: `./third_party/opencv/build`

## Status (2026-08-02)
| ABI | Artifacts | 16KB Align |
|-----|-----------|------------|
| arm64-v8a | `libopencv_core.so`, `libopencv_imgproc.so`, `libopencv_imgcodecs.so` | **yes** (LOAD Align `0x4000`) |
| x86_64 | not built yet | — |
| libopencv_java4.so | **blocked** — OpenCV `modules/java/android_sdk` writes under wrong `/opencv/...` paths without full Android SDK layout; needs script fix or prebuilt SDK approach |

## App integration (not done)
App still uses Maven `org.opencv:opencv:4.10.0` + checked-in jniLibs. Switch after java4 + x86_64 ready.

## Modules built
`core,imgproc,imgcodecs` (VE Mat/imgproc path). Expand only if needed.
