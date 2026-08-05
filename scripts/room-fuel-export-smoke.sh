#!/usr/bin/env bash
# Thin pointer: Room Fuel multi-tab export → json-book → optional EtherCalc.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SMOKE="$ROOT/third_party/remotetable/src/conformance/room_fuel_export_smoke.py"
[[ -f "$SMOKE" ]] || { echo "ERROR: missing $SMOKE" >&2; exit 1; }
exec python3 "$SMOKE" "$@"
