#!/bin/bash
# cleanup_pr.sh: Cleanup feature branch and PR artifacts after merge.
# Usage: ./cleanup_pr.sh branch-name

if [ $# -lt 1 ]; then
    echo "Usage: $0 branch-name"
    exit 1
fi

BRANCH_NAME="$1"
SANDBOX_DIR="/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction"
PR_FILE="$SANDBOX_DIR/PRs/PR-$BRANCH_NAME.md"

if [[ "$BRANCH_NAME" == "master" ]] || [[ "$BRANCH_NAME" == "orchestration" ]]; then
    echo "Error: Protected branch '$BRANCH_NAME' cannot be cleaned up."
    exit 1
fi

echo "Cleaning up PR artifacts for '$BRANCH_NAME'..."

# 1. Delete local branch (optional, usually handled by manual worktree removal)
if git rev-parse --verify "$BRANCH_NAME" >/dev/null 2>&1; then
    echo "Attempting to delete local branch '$BRANCH_NAME'..."
    git branch -D "$BRANCH_NAME" 2>/dev/null
    if [ $? -ne 0 ]; then
        echo "Note: Could not delete branch '$BRANCH_NAME' (likely active in a worktree). The user will handle this manually."
    fi
fi

# 2. Remove PR Document
if [ -f "$PR_FILE" ]; then
    echo "Removing PR document: $PR_FILE"
    rm "$PR_FILE"
else
    echo "PR document not found: $PR_FILE"
fi

# 3. Cleanup common patch locations
PATCH_FILE="$SANDBOX_DIR/pr-patches/all_changes.patch"
if [ -f "$PATCH_FILE" ]; then
    echo "Removing legacy patch: $PATCH_FILE"
    rm "$PATCH_FILE"
fi

echo "Cleanup complete for '$BRANCH_NAME'."
