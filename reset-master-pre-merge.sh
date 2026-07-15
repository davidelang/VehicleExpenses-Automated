#!/bin/bash
# reset-master-pre-merge.sh — Reset master to a pre-merge tip + restore merge-driver infra.
#
# Usage:
#   ./reset-master-pre-merge.sh
#   PRE_MERGE=ae6747b1 PRE_BUILD=f0446cd7 INFRA_COMMIT=8c0294d6 ./reset-master-pre-merge.sh
#
# Requires sudo for chattr -a on ENGINEERING_LOG.md and fix-engineering-log-perms.

set -euo pipefail

PRE_MERGE="${PRE_MERGE:-ae6747b1}"
PRE_BUILD="${PRE_BUILD:-f0446cd7}"
INFRA_COMMIT="${INFRA_COMMIT:-8c0294d6}"

ROOT=$(git rev-parse --show-toplevel 2>/dev/null || true)
if [ -z "$ROOT" ]; then
  echo "ERROR: not inside a git worktree" >&2
  exit 1
fi
cd "$ROOT"

echo "=== reset-master-pre-merge ==="
echo "  PRE_MERGE=$PRE_MERGE  PRE_BUILD=$PRE_BUILD  INFRA_COMMIT=$INFRA_COMMIT"
echo "  worktree: $ROOT"
echo "  current branch: $(git rev-parse --abbrev-ref HEAD)"
echo ""

englog_remove_append_only() {
  if [ ! -f ENGINEERING_LOG.md ]; then
    echo "ERROR: ENGINEERING_LOG.md not found" >&2
    exit 1
  fi
  echo ">> sudo chattr -a ENGINEERING_LOG.md"
  sudo chattr -a ENGINEERING_LOG.md
  if lsattr ENGINEERING_LOG.md 2>/dev/null | grep -qE -- '[^-]a'; then
    echo "ERROR: ENGINEERING_LOG.md is still append-only after sudo chattr -a" >&2
    echo "       Run manually: sudo chattr -a ENGINEERING_LOG.md && lsattr ENGINEERING_LOG.md" >&2
    exit 1
  fi
  echo "  append-only cleared ($(lsattr ENGINEERING_LOG.md | awk '{print $1}'))"
}

englog_restore_from_commit() {
  local commit="$1"
  echo ">> Restore ENGINEERING_LOG.md from $commit"
  git show "$commit:ENGINEERING_LOG.md" > ENGINEERING_LOG.md
  git update-index --cacheinfo "100644,$(git hash-object ENGINEERING_LOG.md),ENGINEERING_LOG.md"
}

materialize_index_to_worktree() {
  local f
  # Eng log already restored; checkout-index the rest (or all — +a is off)
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    [ "$f" = "ENGINEERING_LOG.md" ] && continue
    git checkout-index -f -- "$f" 2>/dev/null || true
  done < <(git ls-tree -r --name-only HEAD)
}

echo ">> Clear partial merge / autostash"
git merge --abort 2>/dev/null || true
rm -f "$(git rev-parse --git-path MERGE_HEAD)" 2>/dev/null || true
while git stash list 2>/dev/null | head -1 | grep -q autostash; do
  git stash drop >/dev/null 2>&1 || break
done

englog_remove_append_only

echo ">> Point master at $PRE_MERGE (no git checkout — avoids +a unlink)"
git update-ref refs/heads/master "$PRE_MERGE"
git symbolic-ref HEAD refs/heads/master
git read-tree --reset "$PRE_MERGE"
englog_restore_from_commit "$PRE_MERGE"
materialize_index_to_worktree

# Drop debug branch if present
if git show-ref --verify --quiet refs/heads/test-merge-driver-attrs; then
  git branch -D test-merge-driver-attrs 2>/dev/null || true
fi

echo ">> Restore merge-driver tooling from $INFRA_COMMIT"
INFRA_PATHS=(
  .gitattributes
  install-merge-drivers.sh
  merge-branch-into-master.sh
  git-merge-drivers/
  MASTER_AGENT_MANDATE.md
  .grok/skills/master-merge/SKILL.md
)
if git cat-file -e "$INFRA_COMMIT:reset-master-pre-merge.sh" 2>/dev/null; then
  INFRA_PATHS+=(reset-master-pre-merge.sh)
fi
git checkout "$INFRA_COMMIT" -- "${INFRA_PATHS[@]}" 2>/dev/null || true
# Always keep this script from worktree (may be newer than INFRA_COMMIT)
if [ -f "$ROOT/reset-master-pre-merge.sh" ]; then
  chmod +x "$ROOT/reset-master-pre-merge.sh"
fi
chmod +x install-merge-drivers.sh merge-branch-into-master.sh \
  git-merge-drivers/ve-englog git-merge-drivers/ve-special-ours git-merge-drivers/ve-special-refuse 2>/dev/null || true

./install-merge-drivers.sh

echo ">> Restore ENGINEERING_LOG perms (sudo ./fix-engineering-log-perms)"
sudo ./fix-engineering-log-perms

echo ">> Restore builds tag → $PRE_BUILD"
git tag -f builds "$PRE_BUILD"

echo ""
echo "=== Verification ==="
git status -sb
echo "HEAD: $(git rev-parse --short HEAD) ($(git log -1 --oneline))"
echo "builds tag: $(git rev-parse --short builds) ($(git describe --tags builds 2>/dev/null || true))"
ENG_WT=$(wc -l < ENGINEERING_LOG.md | tr -d ' ')
ENG_HEAD=$(git show HEAD:ENGINEERING_LOG.md | wc -l | tr -d ' ')
echo "ENGINEERING_LOG.md lines: worktree=$ENG_WT  HEAD=$ENG_HEAD"
if [ "$ENG_WT" != "$ENG_HEAD" ]; then
  echo "WARNING: eng-log worktree line count != HEAD blob" >&2
fi
git check-attr merge -- TODO.md project-facts.md ENGINEERING_LOG.md
echo "merge.autostash=$(git config --get merge.autostash || echo unset)"
if git rev-parse --verify full-code-review1 >/dev/null 2>&1; then
  echo "full-code-review1 tip: $(git rev-parse --short full-code-review1)"
fi

echo ""
echo "Done. Next: ./merge-branch-into-master.sh full-code-review1"