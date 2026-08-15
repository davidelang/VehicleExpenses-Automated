# patches-x86-openblas

Android x86_64 pin builds use OpenBLAS (`WITH_MKL=OFF` in `build_android.sh`).
Upstream `mklml.h` always `#include <mkl.h>`, which is missing on NDK.

These files gate MKL with `LITE_WITH_MKL` (from sandbox paddle-build patches):

- `code/lite/backends/x86/mklml.h`
- `code/lite/backends/x86/mklml.cc`

Applied only when `ARCH` is `x86` / `x86_64` in `scripts/run-android-historical.sh` (via apply path).

## OpenBLAS thread count (pin `patches/external/openblas.cmake`)

| Setting | Effect |
|---------|--------|
| `NUM_THREADS=1` (old pin) | OpenBLAS built **single-thread**: `goto_set_num_threads` is a no-op, `openblas_get_num_threads` always returns 1. Emulator multi-scale stays ~1 host core no matter what `setThreads` / `set_x86_math_num_threads` do. |
| `USE_THREAD=1 NUM_THREADS=4` (current) | Multi-thread OpenBLAS; max 4 workers (match typical AVD `hw.cpu.ncore=4`). |

Size knobs kept: `DYNAMIC_ARCH=0 TARGET=CORE2 ONLY_CBLAS=1` (see `docs/specs/PADDLE_BUILD.md` § OpenBLAS).

**Rebuild (libpin — see `third_party/README.md`, `docs/reference/PADDLE_PIN_BUILDS.md`):**

```bash
# From VehicleExpenses worktree root
./third_party/fetch-deps ro paddle   # if src not materialised
PADDLE_ABIS=x86_64 PADDLE_SKIP_IMAGE_BUILD=1 \
  PADDLE_SKIP_SO_SMOKE=0 PADDLE_SKIP_OCR_QEMU=1 \
  ./third_party/fetch-deps build paddle
# get-artifacts (via build) → app/src/main/jniLibs/x86_64/libpaddle_light_api_shared.so
# Verify: openblas_get_num_threads no longer hardcodes 1; nm/objdump goto_set_num_threads is non-empty
```
