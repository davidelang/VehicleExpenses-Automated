#!/usr/bin/env bash
# Render docs/user-manual.md → browser HTML + app assets (with screenshots).
# Markdown is the edit source; HTML is what browsers and the in-app WebView open.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
exec python3 "$ROOT/scripts/render_user_manual.py"
