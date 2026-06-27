#!/bin/bash
# Push phone-updated vehicle_ref_*.jpg files to device(s) with cache bust + clean launch.
# Thin wrapper around deploy --push-vehicle-refs.
#
# Usage:
#   ./push-vehicle-refs.sh /path/to/vehicle_ref_1778726321661.jpg /path/to/vehicle_ref_1778726533320.jpg
#   ./push-vehicle-refs.sh /path/to/dir/with/vehicle_ref_*.jpg
#   ./push-vehicle-refs.sh /path/to/dir tablet
#
# See ./deploy --help comments for Honda/Ford reference dash photo workflow.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$ROOT/deploy" --push-vehicle-refs "$@"