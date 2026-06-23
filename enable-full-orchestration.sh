#!/bin/bash
# enable-full-orchestration.sh
# Thin opt-in helper for turning a plain master/app checkout into (or documenting)
# the full orchestration layout.
#
# ONE-TIME STAMP / language-agnostic:
#   - Run from a plain checkout of the app (master or feature) to get instructions
#     or perform the add of the orchestration managing tree.
#   - After stamping, this helper (if copied in) belongs to the target.
#   - Does not assume "app/" directory; detects presence of build system vs brain files.
#
# Usage (from a plain tree):
#   ./enable-full-orchestration.sh
#
# It will:
# - Detect context (standalone vs full).
# - Print / execute safe steps to add the orchestration branch as a sibling managing tree
#   using git worktree (preferred) or guidance for clone.
# - Set up the dev-ai-interaction symlink if a sibling managing tree provides it.
# - Never modifies app source.
#
# After success you can cd ../orchestration (or the name you choose) and run update-rules.sh
# (once the brain is present) and launch agents from there.

set -euo pipefail

echo "=== enable-full-orchestration.sh (opt-in to full multi-agent layout) ==="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CURRENT_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

has_gradlew=0
has_app_dir=0
has_update_rules=0
has_run_grok_master=0

[ -x "$CURRENT_ROOT/gradlew" ] && has_gradlew=1
[ -d "$CURRENT_ROOT/app" ] && has_app_dir=1
[ -f "$CURRENT_ROOT/update-rules.sh" ] && has_update_rules=1
[ -f "$CURRENT_ROOT/run-grok-master" ] && has_run_grok_master=1

if [ "$has_update_rules" -eq 1 ] && [ "$has_run_grok_master" -eq 1 ]; then
  echo "Context: looks like full orchestration layout (brain files present)."
  echo "Nothing to do. Use ./setup_agent.sh from here to create agent worktrees."
  exit 0
fi

echo "Context: looks like standalone app/plain checkout."
echo "has_gradlew=$has_gradlew has_app_dir=$has_app_dir (detection only; paths not assumed hardcoded beyond this script)"
echo ""

# Try to discover if an orchestration branch is already known locally or via remotes.
ORCH_BRANCH="orchestration"
if git rev-parse --verify "$ORCH_BRANCH" >/dev/null 2>&1; then
  echo "Local branch '$ORCH_BRANCH' exists."
else
  if git ls-remote --heads origin "$ORCH_BRANCH" >/dev/null 2>&1; then
    echo "Remote branch 'origin/$ORCH_BRANCH' exists. Will fetch."
    git fetch origin "$ORCH_BRANCH:$ORCH_BRANCH" || git fetch origin "$ORCH_BRANCH" || true
  else
    echo "No local or origin/$ORCH_BRANCH branch visible."
    echo "You may need to add the remote that carries the orchestration branch, or clone with --mirror or all branches."
    echo "Example (one-time):"
    echo "  git remote add origin <url>   # if missing"
    echo "  git fetch origin $ORCH_BRANCH:$ORCH_BRANCH"
  fi
fi

# Propose sibling location (sibling dir is conventional).
PARENT_DIR="$(dirname "$CURRENT_ROOT")"
SUGGESTED_ORCH="$PARENT_DIR/orchestration"

echo ""
echo "Recommended opt-in steps (from $CURRENT_ROOT):"
echo "1. Ensure you have the orchestration branch locally (see above)."
echo "2. Add worktree for the managing orchestration tree (outside the current tree):"
echo "     git worktree add -f \"$SUGGESTED_ORCH\" $ORCH_BRANCH"
echo "   (or pick another location; the managing tree is the one with update-rules.sh etc.)"
echo "3. From the new managing tree:"
echo "     cd \"$SUGGESTED_ORCH\""
echo "     ./update-rules.sh     # push current brain into all worktrees (including this one)"
echo "4. (Re)create the dev-ai-interaction symlink in this tree if needed:"
echo "     ln -sfn \"$(dirname "$SUGGESTED_ORCH")/dev-ai-interaction\" \"$CURRENT_ROOT/dev-ai-interaction\" || true"
echo "5. Use launchers from the managing tree for agents, or ../run-grok* from worktrees."
echo ""
echo "Reverse (full -> standalone view): simply stay in the master/ worktree (or a plain clone of master). It remains usable; the heavy brain is only actively managed from the orchestration branch/tree."

# Optional auto step (safe, non-destructive): only if user passed --apply or similar.
if [ "${1:-}" = "--apply" ]; then
  echo ""
  echo "--apply requested: attempting non-destructive worktree add (will not overwrite)."
  if git rev-parse --verify "$ORCH_BRANCH" >/dev/null 2>&1; then
    if [ ! -d "$SUGGESTED_ORCH" ]; then
      git worktree add "$SUGGESTED_ORCH" "$ORCH_BRANCH" || echo "worktree add failed or already present; continuing with guidance only."
      echo "Created: $SUGGESTED_ORCH"
    else
      echo "$SUGGESTED_ORCH already exists; skipping worktree add."
    fi
  else
    echo "Cannot auto-add: local $ORCH_BRANCH branch not present."
  fi
  # Symlink hint only (never force in auto without user review)
  if [ -d "$(dirname "$SUGGESTED_ORCH")/dev-ai-interaction" ]; then
    ln -sfn "$(dirname "$SUGGESTED_ORCH")/dev-ai-interaction" "$CURRENT_ROOT/dev-ai-interaction" 2>/dev/null || true
    echo "Symlinked dev-ai-interaction (if not present)."
  fi
else
  echo ""
  echo "Re-run with --apply to let this helper attempt the worktree add + basic symlink (review first!)."
fi

echo ""
echo "After opt-in, the plain master tree is still usable independently."
echo "Full layout uses the managing orchestration tree for brain updates and agent launch."
exit 0
