#!/bin/bash
# merge-branch-into-master.sh — Master-facing merge with special-file protocol.
# Usage: ./merge-branch-into-master.sh <branch-name>
#
# - Ensures merge drivers installed
# - git merge --no-ff --no-commit
# - ENGINEERING_LOG: ve-englog may produce third-version via append-to-engineering-log
# - TODO.md / project-facts.md: ALWAYS special-file review (against branch delta),
#   not only when those paths changed. Keeps master base; never naive text merge.
# - No happy-path chattr ±a
#
# Leaves merge UNCOMMITTED. Master finishes special files, then ./build_app.

set -euo pipefail

BRANCH="${1:-}"
if [ -z "$BRANCH" ]; then
  echo "Usage: $0 <branch-name>" >&2
  exit 1
fi

ROOT=$(git rev-parse --show-toplevel)
cd "$ROOT"

CURRENT=$(git rev-parse --abbrev-ref HEAD)
if [ "$CURRENT" != "master" ]; then
  echo "WARNING: current branch is '$CURRENT' (expected master). Continuing anyway." >&2
fi

if ! git rev-parse --verify "$BRANCH" >/dev/null 2>&1; then
  echo "ERROR: unknown branch/ref: $BRANCH" >&2
  exit 1
fi

if [ -x ./install-merge-drivers.sh ]; then
  ./install-merge-drivers.sh >/dev/null
else
  echo "WARNING: install-merge-drivers.sh missing; merge drivers may not run" >&2
fi

echo "=== merge-branch-into-master: merging $BRANCH into $CURRENT (--no-ff --no-commit) ==="
echo "Branch tip: $(git rev-parse --short "$BRANCH")  Base: $(git merge-base HEAD "$BRANCH" | cut -c1-8)"

# Snapshot master specials before merge (always restore as protocol base)
pre_todo=$(git rev-parse HEAD:TODO.md 2>/dev/null || echo "")
pre_facts=$(git rev-parse HEAD:project-facts.md 2>/dev/null || echo "")

set +e
git merge --no-ff --no-commit "$BRANCH"
merge_rc=$?
set -e

# Eng-log: stage third-version if worktree differs after ve-englog append
if [ -f ENGINEERING_LOG.md ]; then
  git add ENGINEERING_LOG.md 2>/dev/null || true
fi

# Always restore pre-merge master TODO + project-facts as working base.
# Semantic protocol (todo-close / fact prune) runs against branch delta next —
# never trust a 3-way or take-theirs of these files.
restore_special() {
  local f="$1" blob="$2"
  if [ -z "$blob" ]; then
    return 0
  fi
  if git ls-files -u -- "$f" | grep -q .; then
    echo "  Restoring pre-merge master for unmerged $f"
  else
    echo "  Ensuring master base for $f (special-file protocol base)"
  fi
  git show "$blob" > "$f"
  git add -f "$f" 2>/dev/null || git add "$f"
}
restore_special TODO.md "$pre_todo"
restore_special project-facts.md "$pre_facts"

cat <<EOF

========================================================================
SPECIAL FILES — REQUIRED FOR EVERY MERGE (not only when these paths diff)
Branch: $BRANCH
Merge exit code: $merge_rc  (0=clean code merge, 1=conflicts remaining)
========================================================================

ENGINEERING_LOG.md
  Driver ve-englog appends branch-only tail via ./append-to-engineering-log.
  Result is usually a THIRD version (master + branch tail), not pure ours/theirs.
  If eng-log still conflicted: resolve by keeping master body + append branch
  entries only via the wrapper. Never replace master's log with the branch file.

TODO.md  (ALWAYS — even if this file was unchanged on both sides)
  Base: master TODO (already restored if branch text was pulled in).
  Against git log/diff master...$BRANCH and dev-ai-interaction/PRs/PR-*.md:
    - todo-close items this branch implemented or PR/commits mark done
    - todo-append only for genuine new future backlog (rare)
  Never bulk-replace TODO from the branch.

project-facts.md  (ALWAYS — even if this file was unchanged on both sides)
  Base: master project-facts.
  Against branch delta and post-fork reality:
    - prune/fix facts invalidated by this merge
    - keep only stable orientation facts still true after merge
  No plan/branch/tag/"working on" narrative.

Next:
  1. Complete TODO + project-facts protocol (helpers: todo-close, todo-append).
  2. Resolve any remaining non-special conflicts.
  3. git status; git add ...
  4. ./build_app @summary.txt <files...>
  5. Do NOT set works tag. User may ./remove_worktree.sh $BRANCH when done.

Read: MASTER_AGENT_MANDATE.md §2
Skill: /master-merge
========================================================================
EOF

# Always non-zero so agent cannot treat script alone as "merge finished"
# unless we want 0 when merge_rc==0 — plan said leave uncommitted + clear stdout.
# Exit 2 = special files pending (even if git merge was clean).
if [ "$merge_rc" -ne 0 ]; then
  echo "merge-branch-into-master: git merge reported conflicts (rc=$merge_rc); special-file checklist still applies." >&2
  exit "$merge_rc"
fi
echo "merge-branch-into-master: code merge staged (no commit). Complete special files, then build_app." >&2
exit 0
