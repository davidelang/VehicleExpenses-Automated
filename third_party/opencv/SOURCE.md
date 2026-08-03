# OpenCV pin

| | |
|--|--|
| **Upstream** | `opencv/opencv` **4.10.0** = `71d3237a093b60a27601c20e9ee6c3e52154e8b1` |
| **build_time** | `tens_of_minutes` (both ABIs) |
| **reproducible** | `false` (typical native toolchain variance) |
| **Products** | `artifact/jni/{arm64-v8a,x86_64}/libopencv_java4.so` |

## Reproduce

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$HOME/Android/Sdk
./third_party/fetch-deps ro opencv
./third_party/fetch-deps build opencv
```

Optional: `GIT_HOME=~/git` with a full clone of OpenCV speeds materialize (worktree/objects).

## Build layout (under materialized `src/`)

OpenCV uses **out-of-source** CMake:

- `src/build/<abi>/` — CMake binary dir  
- `src/bin/<abi>/libopencv_java4.so` — product before collection  
- `artifact/jni/<abi>/…` — stable pin (via get-artifacts)

## Optimizations (script)

- Modules: core, imgproc, imgcodecs, java only  
- 16KB: `-Wl,-z,max-page-size=16384`  
- arm64 `-O3`; x86_64 `-O2 -mno-avx512f`  
- strip unneeded symbols  
- Many WITH_*=OFF (CUDA, IPP, TIFF, …)

Further size/speed work: after happy path (see project TODO / plan).

## Patches

None yet (`patches/` empty aside from README). All deviation is build flags.
