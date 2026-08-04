# patches-x86-openblas

Android x86_64 pin builds use OpenBLAS (`WITH_MKL=OFF` in `build_android.sh`).
Upstream `mklml.h` always `#include <mkl.h>`, which is missing on NDK.

These files gate MKL with `LITE_WITH_MKL` (from sandbox paddle-build patches):

- `code/lite/backends/x86/mklml.h`
- `code/lite/backends/x86/mklml.cc`

Applied only when `ARCH` is `x86` / `x86_64` in `scripts/run-android-slim.sh`.
