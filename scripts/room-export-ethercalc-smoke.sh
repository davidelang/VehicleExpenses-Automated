#!/usr/bin/env bash
# Thin pointer: Room Vehicles export → json-book → optional local EtherCalc.
# Implementation lives in remotetable pin conformance (not app production path).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/third_party/remotetable/src"
SMOKE="$SRC/conformance/room_export_to_ethercalc_smoke.py"
[[ -f "$SMOKE" ]] || { echo "ERROR: missing $SMOKE — fetch-deps / pin src" >&2; exit 1; }
exec python3 "$SMOKE" "$@"
