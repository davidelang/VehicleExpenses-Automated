#!/usr/bin/env bash
# finish-parity-dlang-remaining.sh — run as dlang after orch W1/W2 commit
#
# 1) Fix VE .gitignore so .grok/lib is not swallowed by lib/
# 2) Run install-workflow-parity-libs.sh (W3) with optional --push
# 3) Optionally push VE orchestration
#
# Usage:
#   bash finish-parity-dlang-remaining.sh
#   bash finish-parity-dlang-remaining.sh --push
set -euo pipefail
[[ "$(id -un)" == "dlang" || "${ALLOW_NON_DLANG:-0}" == "1" ]] || { echo "run as dlang" >&2; exit 1; }

VE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DO_PUSH=0
[[ "${1:-}" == "--push" ]] && DO_PUSH=1

cd "$VE_ROOT"

# --- gitignore exception ---
if ! grep -q '!.grok/lib/' .gitignore 2>/dev/null; then
  python3 - <<'PY'
from pathlib import Path
p = Path('.gitignore')
t = p.read_text()
old = "bin/\nlib/\n\n# Misc"
new = """bin/
lib/
# Track Grok pack launcher library (not Android/native lib/)
!.grok/lib/
!.grok/lib/**

# Misc"""
if old not in t:
    raise SystemExit('gitignore pattern not found for patch')
p.write_text(t.replace(old, new, 1))
print('patched .gitignore for .grok/lib')
PY
  git add .gitignore .grok/lib/grok-launch-common.sh 2>/dev/null || true
  if ! git diff --cached --quiet; then
    git commit -m "gitignore: track .grok/lib (pack launchers; exception to lib/)"
    echo "committed gitignore fix $(git rev-parse --short HEAD)"
  fi
else
  echo "gitignore already has .grok/lib exception"
fi

# --- W3 libs ---
bash "$VE_ROOT/install-workflow-parity-libs.sh" ${DO_PUSH:+--push}

if [[ "$DO_PUSH" -eq 1 ]]; then
  git push origin orchestration || git push
  echo "pushed VE orchestration"
fi

echo ""
echo "Re-audit:"
echo "  bash $VE_ROOT/audit-orchestration-meta-20260802.sh 2>&1 | tee /tmp/meta-audit-parity.txt"
echo "Expect: no WARN for VE legacy launchers / missing lib skills"
