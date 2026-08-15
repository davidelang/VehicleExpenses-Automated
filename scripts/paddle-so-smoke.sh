#!/usr/bin/env bash
# Run paddle_so_smoke for all three product ABIs.
#
# Frequent gate after paddle build / get-artifacts. Does NOT need Android emulator.
# ARM ABIs run under qemu-user; x86_64 under qemu-x86_64 (static Android ELF) or
# falls back to host execution if the binary is runnable.
#
# Ubuntu packages: see docs/reference/PADDLE_SO_SMOKE.md
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
SMOKE_DIR="$ROOT/third_party/paddle/tests/paddle_so_smoke"
OUT_BIN="${PADDLE_SMOKE_OUT:-$SMOKE_DIR/out}"
JNI_ROOT="${PADDLE_JNI_ROOT:-}"
if [[ -z "$JNI_ROOT" ]]; then
  if [[ -d "$ROOT/third_party/paddle/artifact/jni" ]]; then
    JNI_ROOT="$ROOT/third_party/paddle/artifact/jni"
  else
    JNI_ROOT="$ROOT/third_party/paddle/src/bin"
  fi
fi
REPORT_DIR="${PADDLE_SMOKE_REPORT:-$ROOT/dev-ai-interaction/scratch/paddle-so-smoke-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$REPORT_DIR"
ABIS="${PADDLE_SMOKE_ABIS:-arm64-v8a armeabi-v7a x86_64}"
REBUILD="${PADDLE_SMOKE_REBUILD:-1}"
# If 1, also run a host (linux-gnu) binary over all SOs without QEMU (ELF checks only).
HOST_ALSO="${PADDLE_SMOKE_HOST_ALSO:-1}"

find_qemu() {
  local base=$1
  # prefer -static variants (binfmt-friendly)
  for c in "${base}-static" "$base" "/usr/bin/${base}-static" "/usr/bin/$base"; do
    if command -v "$c" >/dev/null 2>&1; then
      echo "$c"
      return 0
    fi
    if [[ -x "$c" ]]; then
      echo "$c"
      return 0
    fi
  done
  return 1
}

# Android 32-bit Bionic aborts if getpid() > 65535 (pthread_mutex layout).
# Host pid_max is often millions — run qemu-arm under a fresh PID namespace as PID 1.
run_qemu_armv7() {
  local q=$1
  shift
  if command -v bwrap >/dev/null 2>&1; then
    # bubblewrap: unshare PID ns, smoke binary sees pid 1
    bwrap --unshare-pid --as-pid-1 --die-with-parent \
      --dev-bind / / \
      -- "$q" "$@"
    return $?
  fi
  if unshare -pf --mount-proc -- true 2>/dev/null; then
    unshare -pf --mount-proc -- "$q" "$@"
    return $?
  fi
  echo "ERROR: armeabi-v7a qemu needs low PID (host pid $$ > 65535)." >&2
  echo "  Install bubblewrap:  sudo apt install -y bubblewrap" >&2
  echo "  Or (root, temporary): echo 65535 | sudo tee /proc/sys/kernel/pid_max" >&2
  return 127
}

run_binary() {
  local abi=$1
  local bin=$2
  shift 2
  local q ec
  case "$abi" in
    arm64-v8a)
      q=$(find_qemu qemu-aarch64) || {
        echo "ERROR: need qemu-aarch64 (Ubuntu 26.04+: apt install qemu-user)" >&2
        return 127
      }
      "$q" "$bin" "$@"
      return $?
      ;;
    armeabi-v7a)
      q=$(find_qemu qemu-arm) || {
        echo "ERROR: need qemu-arm (Ubuntu 26.04+: apt install qemu-user)" >&2
        return 127
      }
      run_qemu_armv7 "$q" "$bin" "$@"
      return $?
      ;;
    x86_64)
      # Static Android x86_64 ELF may need qemu-x86_64 on glibc host
      set +e
      "$bin" "$@"
      ec=$?
      set -e
      if [[ "$ec" -eq 0 ]]; then
        return 0
      fi
      q=$(find_qemu qemu-x86_64) || {
        echo "ERROR: need qemu-x86_64 (Ubuntu 26.04+: apt install qemu-user)" >&2
        return 127
      }
      "$q" "$bin" "$@"
      return $?
      ;;
    *)
      echo "unknown abi $abi" >&2
      return 2
      ;;
  esac
}

echo "paddle-so-smoke root=$ROOT jni=$JNI_ROOT report=$REPORT_DIR"
{
  echo "# paddle_so_smoke report"
  echo "date: $(date -Is)"
  echo "jni_root: $JNI_ROOT"
  echo "host: $(uname -a)"
} >"$REPORT_DIR/REPORT.md"

if [[ "$REBUILD" == "1" ]] || [[ ! -x "$OUT_BIN/paddle_so_smoke.x86_64" ]]; then
  echo "Building smoke binaries..."
  OUT="$OUT_BIN" "$SMOKE_DIR/build.sh" | tee "$REPORT_DIR/build.log"
fi
# Host ELF checker (linux-gnu) — validates all SO files without QEMU; does not execute ARM ISA.
if [[ "$HOST_ALSO" == "1" ]]; then
  if [[ "$REBUILD" == "1" ]] || [[ ! -x "$OUT_BIN/paddle_so_smoke.host" ]]; then
    g++ -std=c++17 -O2 -o "$OUT_BIN/paddle_so_smoke.host" "$SMOKE_DIR/smoke_main.cpp"
  fi
fi

FAIL=0
MISSING_QEMU=0

# Host pass first (fast; same checks, host CPU)
if [[ "$HOST_ALSO" == "1" && -x "$OUT_BIN/paddle_so_smoke.host" ]]; then
  echo ""
  echo "######## HOST (linux-gnu) ELF checks ########"
  for abi in $ABIS; do
    jni="$JNI_ROOT/$abi/libpaddle_lite_jni.so"
    light="$JNI_ROOT/$abi/libpaddle_light_api_shared.so"
    [[ -f "$jni" ]] || { echo "FAIL missing $jni"; FAIL=1; continue; }
    so_args=(--abi "$abi" --so "$jni")
    [[ -f "$light" ]] && so_args+=(--so "$light")
    logf="$REPORT_DIR/host-$abi.log"
    set +e
    "$OUT_BIN/paddle_so_smoke.host" "${so_args[@]}" >"$logf" 2>&1
    ec=$?
    set -e
    cat "$logf"
    if [[ "$ec" -ne 0 ]]; then
      echo "- **host/$abi**: FAIL" >>"$REPORT_DIR/REPORT.md"
      FAIL=1
    else
      echo "- **host/$abi**: PASS" >>"$REPORT_DIR/REPORT.md"
    fi
  done
fi

for abi in $ABIS; do
  echo ""
  echo "######## ABI $abi ########"
  bin="$OUT_BIN/paddle_so_smoke.$abi"
  if [[ ! -x "$bin" ]]; then
    echo "FAIL: missing $bin" | tee -a "$REPORT_DIR/REPORT.md"
    FAIL=1
    continue
  fi
  so_args=()
  jni="$JNI_ROOT/$abi/libpaddle_lite_jni.so"
  light="$JNI_ROOT/$abi/libpaddle_light_api_shared.so"
  if [[ ! -f "$jni" ]]; then
    echo "FAIL: missing $jni" | tee -a "$REPORT_DIR/REPORT.md"
    FAIL=1
    continue
  fi
  so_args+=(--so "$jni")
  if [[ -f "$light" ]]; then
    so_args+=(--so "$light")
  fi

  logf="$REPORT_DIR/smoke-$abi.log"
  set +e
  run_binary "$abi" "$bin" --abi "$abi" "${so_args[@]}" >"$logf" 2>&1
  ec=$?
  set -e
  cat "$logf"
  if [[ "$ec" -eq 0 ]]; then
    echo "- **qemu/$abi**: PASS" >>"$REPORT_DIR/REPORT.md"
  elif [[ "$ec" -eq 127 ]]; then
    echo "- **qemu/$abi**: SKIP (qemu not installed)" >>"$REPORT_DIR/REPORT.md"
    MISSING_QEMU=1
    FAIL=1
  else
    echo "- **qemu/$abi**: FAIL (exit $ec)" >>"$REPORT_DIR/REPORT.md"
    FAIL=1
  fi
done

echo "" >>"$REPORT_DIR/REPORT.md"
if [[ "$MISSING_QEMU" -eq 1 ]]; then
  echo "Install (Ubuntu 26.04+): sudo apt install -y qemu-user qemu-user-binfmt" >>"$REPORT_DIR/REPORT.md"
  echo "Install (older Ubuntu):  sudo apt install -y qemu-user-static binfmt-support" >>"$REPORT_DIR/REPORT.md"
fi
if [[ "$FAIL" -eq 0 ]]; then
  echo "**RESULT: PASS**" | tee -a "$REPORT_DIR/REPORT.md"
  echo "Report: $REPORT_DIR"
  exit 0
fi
echo "**RESULT: FAIL**" | tee -a "$REPORT_DIR/REPORT.md"
echo "Report: $REPORT_DIR"
exit 1
