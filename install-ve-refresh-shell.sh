#!/bin/bash
# install-ve-refresh-shell.sh — build + install setuid-root ve-refresh-shell
#
# The binary is NOT checked into git (architecture-specific; setuid root cannot
# be stored safely). Source ve-refresh-shell.c IS tracked.
#
# Usage:
#   ./install-ve-refresh-shell.sh              # install into cwd (and rebuild)
#   ./install-ve-refresh-shell.sh /path/to/wt  # install into that worktree
#   ./install-ve-refresh-shell.sh --all        # orch root + all agent-* / master worktrees
#
# Requires sudo once for chown root + chmod 4755. Idempotent.

set -euo pipefail

ROOT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
# Prefer common git dir's parent worktree that has update-rules (orchestration)
if [ -f "$ROOT/update-rules.sh" ]; then
  ORCH="$ROOT"
else
  ORCH="$ROOT"
fi

install_one() {
  local dest="$1"
  local src_c bin
  [ -d "$dest" ] || return 1

  src_c=""
  if [ -f "$dest/ve-refresh-shell.c" ]; then
    src_c="$dest/ve-refresh-shell.c"
  elif [ -f "$ORCH/ve-refresh-shell.c" ]; then
    src_c="$ORCH/ve-refresh-shell.c"
    cp -f "$src_c" "$dest/ve-refresh-shell.c" 2>/dev/null || true
    src_c="$dest/ve-refresh-shell.c"
  else
    echo "install-ve-refresh-shell: no ve-refresh-shell.c in $dest or $ORCH" >&2
    return 1
  fi

  bin="$dest/ve-refresh-shell"
  # Rebuild if missing, older than source, or not a regular file
  if [ ! -f "$bin" ] || [ "$src_c" -nt "$bin" ]; then
    echo "  Building $bin ..."
    if ! gcc -O2 -Wall -o "$bin" "$src_c" 2>/dev/null; then
      echo "install-ve-refresh-shell: gcc failed for $bin" >&2
      return 1
    fi
  fi

  # Setuid root only — never leave setuid on non-root owner
  if command -v sudo >/dev/null 2>&1; then
    if sudo chown root:root "$bin" 2>/dev/null && sudo chmod 4755 "$bin" 2>/dev/null; then
      echo "  OK: $bin ($(stat -c '%U:%G %a' "$bin" 2>/dev/null))"
      return 0
    fi
  fi
  # Direct root
  if [ "$(id -u)" -eq 0 ]; then
    chown root:root "$bin" && chmod 4755 "$bin"
    echo "  OK: $bin (as root)"
    return 0
  fi

  chmod 755 "$bin" 2>/dev/null || true
  echo "  WARN: could not chown root $bin — left 755 (run with sudo)" >&2
  return 1
}

is_good() {
  local b="$1"
  [ -x "$b" ] && [ -u "$b" ] && [ "$(stat -c '%U' "$b" 2>/dev/null)" = "root" ]
}

MODE="${1:-.}"
if [ "$MODE" = "--all" ]; then
  echo "install-ve-refresh-shell: orchestration + worktrees under $ORCH"
  install_one "$ORCH" || true
  # Common worktree names
  for d in "$ORCH"/agent-* "$ORCH"/master; do
    [ -d "$d" ] || continue
    [ -f "$d/.git" ] || [ -d "$d/.git" ] || continue
    echo "install-ve-refresh-shell: $d"
    install_one "$d" || true
  done
  # Also any git worktrees
  if command -v git >/dev/null 2>&1; then
    while read -r wt; do
      [ -n "$wt" ] || continue
      [ "$wt" = "$ORCH" ] && continue
      [ -d "$wt" ] || continue
      echo "install-ve-refresh-shell: $wt"
      install_one "$wt" || true
    done < <(git -C "$ORCH" worktree list --porcelain 2>/dev/null | awk '/^worktree /{print $2}')
  fi
  exit 0
fi

DEST="$MODE"
if [ "$DEST" = "." ] || [ -z "$DEST" ]; then
  DEST=$(pwd)
fi
# Absolute
DEST=$(cd "$DEST" && pwd)
echo "install-ve-refresh-shell: $DEST"
install_one "$DEST"
if is_good "$DEST/ve-refresh-shell"; then
  exit 0
fi
exit 1
