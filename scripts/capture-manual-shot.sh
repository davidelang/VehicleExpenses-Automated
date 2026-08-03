#!/usr/bin/env bash
# Capture emulator/device screen to JPEG.
# Usage: ./scripts/capture-manual-shot.sh SERIAL OUT.jpg
set -euo pipefail
SERIAL="${1:?serial}"
OUT="${2:?out.jpg}"
TMP_PNG="$(mktemp /tmp/ve-cap-XXXXXX.png)"
cleanup() { rm -f "$TMP_PNG"; }
trap cleanup EXIT
adb -s "$SERIAL" exec-out screencap -p > "$TMP_PNG"
# Prefer ImageMagick; fall back to Pillow
if command -v convert >/dev/null 2>&1; then
  convert "$TMP_PNG" -quality 90 "$OUT"
else
  python3 -c "from PIL import Image; Image.open('$TMP_PNG').convert('RGB').save('$OUT','JPEG',quality=90)"
fi
echo "wrote $OUT ($(wc -c < "$OUT") bytes)"
