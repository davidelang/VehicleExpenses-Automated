#!/usr/bin/env bash
# Run VE paddle OCR functional gate (angle → deskew → det → crop → rec) on a device.
#
# Uses real app code via androidTest:
#   PaddleOcrFunctionalTest.skewedHello_angleDeskewCropOcr
#
# Fixture: third_party/paddle/tests/ocr_functional/fixtures/ (also androidTest assets)
#
# Usage:
#   ./scripts/paddle-ocr-functional.sh
#   ./scripts/paddle-ocr-functional.sh --serial emulator-5554
#   ./scripts/paddle-ocr-functional.sh --skip-build   # install+run only
#
# Env:
#   ANDROID_SERIAL / --serial   device id
#   PADDLE_OCR_FUNCTIONAL_SKIP_BUILD=1
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
SERIAL="${ANDROID_SERIAL:-}"
SKIP_BUILD="${PADDLE_OCR_FUNCTIONAL_SKIP_BUILD:-0}"
PKG="com.davidlang.vehicleexpensesautomated"
TEST_CLASS="${PKG}.ocr.PaddleOcrFunctionalTest"
TEST_METHOD="skewedHello_angleDeskewCropOcr"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    -h|--help)
      sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

ADB=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB=(adb -s "$SERIAL")
fi

if ! command -v adb >/dev/null; then
  echo "ERROR: adb not found" >&2
  exit 2
fi

# Pick a device if none specified
if [[ -z "$SERIAL" ]]; then
  mapfile -t devs < <("${ADB[@]}" devices | awk 'NR>1 && $2=="device" {print $1}')
  if [[ ${#devs[@]} -eq 0 ]]; then
    echo "ERROR: no adb device/emulator online" >&2
    exit 2
  fi
  # Prefer free emulator-5554 if present
  SERIAL=""
  for d in "${devs[@]}"; do
    if [[ "$d" == "emulator-5554" ]]; then SERIAL="$d"; break; fi
  done
  if [[ -z "$SERIAL" ]]; then SERIAL="${devs[0]}"; fi
  ADB=(adb -s "$SERIAL")
fi

echo "paddle-ocr-functional: serial=$SERIAL skip_build=$SKIP_BUILD"

if [[ "$SKIP_BUILD" != "1" ]]; then
  echo "==> assembleDebug + assembleDebugAndroidTest"
  (cd "$ROOT" && ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest)
fi

APK_DEBUG=$(ls -1 "$ROOT"/app/build/outputs/apk/debug/*.apk 2>/dev/null | head -1 || true)
APK_TEST=$(ls -1 "$ROOT"/app/build/outputs/apk/androidTest/debug/*.apk 2>/dev/null | head -1 || true)
if [[ -z "$APK_DEBUG" || -z "$APK_TEST" ]]; then
  echo "ERROR: missing APKs (debug=$APK_DEBUG test=$APK_TEST). Build first." >&2
  exit 2
fi

echo "==> install $APK_DEBUG"
"${ADB[@]}" install -r "$APK_DEBUG"
echo "==> install $APK_TEST"
"${ADB[@]}" install -r "$APK_TEST"

echo "==> instrument $TEST_CLASS#$TEST_METHOD"
set +e
OUT=$("${ADB[@]}" shell am instrument -w -r -e class "${TEST_CLASS}#${TEST_METHOD}" \
  "${PKG}.test/androidx.test.runner.AndroidJUnitRunner" 2>&1)
EC=$?
set -e
echo "$OUT"

if echo "$OUT" | grep -qE 'OK \([0-9]+ test'; then
  echo "paddle-ocr-functional: PASS"
  exit 0
fi
if echo "$OUT" | grep -qi 'FAILURES!!!\|Error in\|Process crashed\|INSTRUMENTATION_FAILED'; then
  echo "paddle-ocr-functional: FAIL" >&2
  exit 1
fi
if [[ "$EC" -ne 0 ]]; then
  echo "paddle-ocr-functional: FAIL (am exit $EC)" >&2
  exit "$EC"
fi
# Some runners only print OK / failures in summary
if echo "$OUT" | grep -q 'OK ('; then
  echo "paddle-ocr-functional: PASS"
  exit 0
fi
echo "paddle-ocr-functional: ambiguous result — treat as FAIL" >&2
exit 1
