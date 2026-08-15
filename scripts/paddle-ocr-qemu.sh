#!/usr/bin/env bash
# Multi-ABI paddle OCR functional gate under QEMU user-mode (not the Android emulator UI).
#
# For each of arm64-v8a / armeabi-v7a / x86_64:
#   NDK-built harness + product libpaddle_light_api_shared.so + .nb models
#   → det → angle → deskew → det → crop → rec
# under qemu-{aarch64,arm,x86_64} with a cached Bionic rootfs.
#
# Fixture: third_party/paddle/tests/ocr_functional/fixtures/
# Docs: third_party/paddle/tests/ocr_functional/README.md
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
exec "$ROOT/third_party/paddle/tests/ocr_functional/qemu/run.sh" "$@"
