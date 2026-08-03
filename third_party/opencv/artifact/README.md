# OpenCV build outputs (in progress)

## arm64-v8a (2026-08-02)
Built from `~/git/opencv` 4.10.0 with NDK 28 + `-Wl,-z,max-page-size=16384`:
- libopencv_core.so
- libopencv_imgproc.so
- libopencv_imgcodecs.so

`libopencv_java4.so` (single JNI fat used by Maven AAR) was **not** produced: configure reported `Java wrappers: NO` (no `ant` on host). Next: install ant or use OpenCV Android SDK java module path, then rebuild for arm64 + x86_64.

VE app still ships Maven `org.opencv:opencv:4.10.0` + existing jniLibs until java4 pin lands.
