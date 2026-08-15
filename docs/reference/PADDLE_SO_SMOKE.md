# paddle_so_smoke — multi-ABI library smoke (no full Android emulator)

**Status:** Implemented (2026-08-04)  
**Driver:** `scripts/paddle-so-smoke.sh`  
**Sources:** `third_party/paddle/tests/paddle_so_smoke/`

Frequent gate after paddle build / `get-artifacts`. Runs a **NDK-built checker** for each ABI under the **matching ISA** (QEMU user for ARM). Does **not** boot Android UI.

See also: `PADDLE_ABI_EMULATOR_TEST.md` (layers: smoke → opportunistic 5554/hardware).

---

## Ubuntu packages

### Ubuntu 26.04+ (Resolute and similar)

The old name **`qemu-user-static` has no installation candidate** — user-mode QEMU is the **`qemu-user`** package (static binaries). Enable **universe** if needed.

```bash
sudo apt update
sudo apt install -y \
  qemu-user \
  qemu-user-binfmt \
  bubblewrap \
  build-essential \
  binutils \
  file
```

| Package | Why |
|---------|-----|
| **`qemu-user`** | User-mode emulation: `qemu-arm`, `qemu-aarch64`, `qemu-x86_64` for smoke ELFs per ABI (replaces old `qemu-user-static` name) |
| **`qemu-user-binfmt`** | Registers binfmt handlers (optional; script invokes qemu by name) |
| **`bubblewrap`** | **Required for armeabi-v7a:** Android 32-bit Bionic aborts if PID > 65535; `bwrap --unshare-pid --as-pid-1` runs the smoke as PID 1 under qemu-arm |
| **`build-essential`** | `g++` for host ELF checker |
| **`binutils`** | `readelf` / `nm` for debugging |
| **`file`** | Sanity-check built smoke ELFs |

#### armeabi-v7a + high host PIDs

On modern Linux, `pid_max` is millions. Static Android armv7 binaries use Bionic, which only supports 16-bit PIDs in `pthread_mutex_t`. Bare `qemu-arm ./smoke…` then aborts with:

```text
32-bit pthread_mutex_t only supports pids <= 65535
```

The harness wraps qemu-arm in **bubblewrap** (preferred) so the guest sees PID 1. Do **not** lower system-wide `pid_max` unless you know you want that.

### Older Ubuntu (≤24.04)

```bash
sudo apt install -y qemu-user-static binfmt-support build-essential binutils file
```

**Also required (not apt):** Android **NDK** (`$HOME/Android/Sdk/ndk/…` or `ANDROID_NDK_HOME`) to **compile** the smoke binaries.

### Verify

```bash
# 26.04 style
which qemu-aarch64 qemu-arm qemu-x86_64
# and/or
ls /usr/bin/qemu-*-static 2>/dev/null

# quick run
qemu-aarch64 --version
qemu-arm --version
```

If `apt` still finds nothing for `qemu-user`:

```bash
sudo add-apt-repository universe
sudo apt update
sudo apt install -y qemu-user qemu-user-binfmt
```
---

## How to run

### Integrated with pin build (preferred)

```bash
./third_party/paddle/build          # ends with link-gate + SO smoke + OCR QEMU
# or after collect:
./third_party/get-artifacts paddle
./third_party/paddle/test           # prefers artifact/jni, else src/bin
```

| Env | Default | Meaning |
|-----|---------|---------|
| `PADDLE_SKIP_SO_SMOKE=1` | off | Skip SO smoke at end of `./build` / `./test` |
| `PADDLE_SKIP_OCR_QEMU=1` | off | Skip multi-ABI OCR functional QEMU (step 7) |
| `PADDLE_SMOKE_REBUILD=1` | `0` when called from `./build` | Rebuild NDK smoke binaries |
| `PADDLE_OCR_QEMU=0` | on (`1`) in `./test` | Alternate way to disable OCR QEMU |

### Standalone driver

From worktree root (after products exist under `third_party/paddle/artifact/jni/` or `src/bin/`):

```bash
./scripts/paddle-so-smoke.sh
# or
./third_party/paddle/test
```
Environment overrides:

| Variable | Default | Meaning |
|----------|---------|---------|
| `PADDLE_JNI_ROOT` | `third_party/paddle/artifact/jni` | Per-ABI SO directories |
| `PADDLE_SMOKE_ABIS` | `arm64-v8a armeabi-v7a x86_64` | ABIs to run |
| `PADDLE_SMOKE_REBUILD` | `1` | Rebuild smoke binaries |
| `PADDLE_SMOKE_REPORT` | `dev-ai-interaction/scratch/paddle-so-smoke-<ts>/` | Report dir |
| `ANDROID_NDK_HOME` | auto-detect Sdk/ndk | NDK for compile |

Exit **0** only if all selected ABIs PASS.

---

## What each smoke checks

For each ABI’s `libpaddle_lite_jni.so` (+ light if present):

1. File is ELF; **e_machine** matches ABI (ARM / AArch64 / X86_64)  
2. Dynamic symbol **`Java_com_baidu_paddle_lite_PaddlePredictor_getVersion`** on jni  
3. Product **stamps** (substring scan):  
   - arm64: `uint8_to_fp16`, `fp32_to_uint8`  
   - armv7: `uint8_to_fp32`, `int8_to_fp32`, `fp32_to_uint8`  
   - x86: `fp32_to_uint8` on **light** (thin jni may lack stamps)  

The checker binary itself is compiled **for that ABI** and executed via **QEMU**, so ARM instruction streams are exercised (not only host `strings`).

---

## What this is not

- Full app / First 10 (use 5554 when free)  
- `dlopen` of paddle under Bionic (would need Android linker + rootfs)  
- Performance or OCR correctness  

For **functional** angle → deskew → det → crop → OCR (real VE code on device), see:

- `third_party/paddle/tests/ocr_functional/`
- `scripts/paddle-ocr-functional.sh`
- `docs/reference/PADDLE_ABI_EMULATOR_TEST.md` (Layer A2)

---

## Layout

```text
third_party/paddle/tests/paddle_so_smoke/
  smoke_main.cpp    # checker
  build.sh          # NDK multi-ABI build (static preferred)
  out/              # paddle_so_smoke.<abi> (gitignored)
scripts/paddle-so-smoke.sh
```
