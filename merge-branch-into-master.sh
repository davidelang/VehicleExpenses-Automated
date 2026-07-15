#!/bin/bash
# merge-branch-into-master.sh — Master-facing merge with special-file protocol.
# Usage: ./merge-branch-into-master.sh <branch-name>
#
# Special files:
#   ENGINEERING_LOG.md — chattr +a: index-first or skip-worktree; ve-englog appends tail.
#   TODO.md / project-facts.md — merge=ve-special-ours (keep master in index); restore_special
#     enforces master worktree+index; Master runs todo-close/append + facts prune.
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

echo "=== merge-branch-into-master: merging $BRANCH into $CURRENT ==="
echo "Branch tip: $(git rev-parse --short "$BRANCH")  Base: $(git merge-base HEAD "$BRANCH" | cut -c1-8)"

while git stash list 2>/dev/null | head -1 | grep -q autostash; do
  echo "  Dropping stale autostash entry"
  git stash drop >/dev/null 2>&1 || break
done

pre_todo=$(git rev-parse HEAD:TODO.md 2>/dev/null || echo "")
pre_facts=$(git rev-parse HEAD:project-facts.md 2>/dev/null || echo "")
pre_head=$(git rev-parse HEAD)

restore_special() {
  local f="$1" blob="$2"
  if [ -z "$blob" ]; then
    return 0
  fi
  echo "  restore_special: master base for $f"
  git show "$blob" > "$f"
  git add -f "$f" 2>/dev/null || git add "$f"
}

verify_index_blob() {
  local f="$1" expected="$2"
  local actual
  actual=$(git rev-parse ":$f" 2>/dev/null || echo "")
  if [ "$actual" != "$expected" ]; then
    echo "ERROR: staged $f blob ${actual:0:8} != master ${expected:0:8}" >&2
    return 1
  fi
  echo "  verify: $f index matches master (${expected:0:8})"
}

englog_sync_index_to_worktree() {
  if [ ! -f ENGINEERING_LOG.md ]; then
    return 0
  fi
  git update-index --no-skip-worktree ENGINEERING_LOG.md 2>/dev/null || true
  local hash
  hash=$(git hash-object ENGINEERING_LOG.md)
  git update-index --cacheinfo 100644,"$hash",ENGINEERING_LOG.md
  echo "  ENGINEERING_LOG.md: index synced to worktree (${hash:0:8})"
}

englog_merge_via_driver() {
  local branch="$1"
  local base ancestor ours theirs
  if [ ! -x ./git-merge-drivers/ve-englog ]; then
    echo "WARNING: ve-englog missing; eng-log append skipped" >&2
    return 1
  fi
  base=$(git merge-base "$pre_head" "$branch")
  ancestor=$(mktemp)
  ours=$(mktemp)
  theirs=$(mktemp)
  git show "$base":ENGINEERING_LOG.md > "$ancestor" 2>/dev/null || : > "$ancestor"
  git show "$pre_head":ENGINEERING_LOG.md > "$ours" 2>/dev/null || : > "$ours"
  git show "$branch":ENGINEERING_LOG.md > "$theirs"
  ./git-merge-drivers/ve-englog "$ancestor" "$ours" "$theirs" "" ENGINEERING_LOG.md
  rm -f "$ancestor" "$ours" "$theirs"
  echo "  ENGINEERING_LOG.md: ve-englog append complete"
}

checkout_merged_worktree_skip_englog() {
  local f
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    [ "$f" = "ENGINEERING_LOG.md" ] && continue
    [ "$f" = "TODO.md" ] && continue
    [ "$f" = "project-facts.md" ] && continue
    git checkout-index -f -- "$f" 2>/dev/null || true
  done < <(git diff --cached --name-only --diff-filter=ACMR)
}

write_merge_head() {
  printf '%s\n' "$(git rev-parse "$BRANCH")" > "$(git rev-parse --git-path MERGE_HEAD)"
}

merge_index_first() {
  local branch="$1"
  local base tree
  base=$(git merge-base HEAD "$branch")
  englog_sync_index_to_worktree
  tree=$(git merge-tree --write-tree --merge-base="$base" HEAD "$branch")
  if [ -z "$tree" ]; then
    echo "ERROR: merge-tree produced empty tree" >&2
    return 1
  fi
  echo "  merge-tree OK → ${tree:0:8}"
  git read-tree --reset "$tree"
  restore_special TODO.md "$pre_todo"
  restore_special project-facts.md "$pre_facts"
  verify_index_blob TODO.md "$pre_todo"
  verify_index_blob project-facts.md "$pre_facts"
  checkout_merged_worktree_skip_englog
  write_merge_head
  englog_merge_via_driver "$branch" || true
  git add ENGINEERING_LOG.md 2>/dev/null || true
}

merge_git_ort() {
  local branch="$1"
  englog_sync_index_to_worktree
  git update-index --skip-worktree ENGINEERING_LOG.md 2>/dev/null || true
  git merge --no-ff --no-commit --no-autostash "$branch"
  restore_special TODO.md "$pre_todo"
  restore_special project-facts.md "$pre_facts"
  verify_index_blob TODO.md "$pre_todo"
  verify_index_blob project-facts.md "$pre_facts"
  englog_merge_via_driver "$branch" || true
  git add ENGINEERING_LOG.md 2>/dev/null || true
  git update-index --no-skip-worktree ENGINEERING_LOG.md 2>/dev/null || true
}

set +e
merge_git_ort "$BRANCH"
merge_rc=$?
if [ "$merge_rc" -ne 0 ]; then
  echo "  git merge failed (rc=$merge_rc); falling back to index-first +a-safe path" >&2
  git merge --abort 2>/dev/null || true
  git read-tree --reset "$pre_head" 2>/dev/null || true
  merge_index_first "$BRANCH"
  merge_rc=$?
fi
set -e

cat <<EOF

========================================================================
SPECIAL FILES — REQUIRED FOR EVERY MERGE (not only when these paths diff)
Branch: $BRANCH
Merge exit code: $merge_rc
========================================================================

ENGINEERING_LOG.md
  ve-englog append-only (never chattr -a / unlink). Third version = master + branch tail.

TODO.md / project-facts.md
  merge=ve-special-ours keeps master in git index; restore_special enforces master on disk.
  Still required: todo-close / todo-append vs branch+PR; project-facts prune/add vs branch delta.

Next: complete TODO + project-facts protocol → git status → ./build_app
========================================================================
EOF

if [ "$merge_rc" -ne 0 ]; then
  echo "merge-branch-into-master: failed (rc=$merge_rc)" >&2
  exit "$merge_rc"
fi
echo "merge-branch-into-master: staged (no commit). Complete special files, then build_app." >&2
exit 0