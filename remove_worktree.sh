#!/bin/bash
# remove_worktree.sh: Safely remove agent worktrees and their branches
# Usage: ./remove_worktree.sh [-f|-ff] agent-N|branch-name

FORCE_LEVEL=0
TARGET=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -ff)
            FORCE_LEVEL=2
            shift
            ;;
        -f|--force)
            FORCE_LEVEL=$((FORCE_LEVEL + 1))
            shift
            ;;
        *)
            TARGET="$1"
            shift
            ;;
    esac
done

if [ -z "$TARGET" ]; then
    echo "Usage: $0 [-f|-ff] agent-N|branch-name"
    exit 1
fi

# 1. Resolve TARGET to the worktree directory and branch name
if [ -d "$TARGET" ] && [ ! -L "$TARGET" ]; then
    # TARGET is the directory (agent-N)
    WORKTREE_DIR="$TARGET"
    BRANCH_NAME=$(git -C "$WORKTREE_DIR" rev-parse --abbrev-ref HEAD)
elif [ -L "$TARGET" ]; then
    # TARGET is a symlink (the branch name)
    WORKTREE_DIR=$(readlink "$TARGET")
    BRANCH_NAME="$TARGET"
else
    # TARGET might be a branch name without a symlink
    BRANCH_NAME="$TARGET"
    WORKTREE_DIR=$(git worktree list --porcelain | grep -B 2 "branch refs/heads/$BRANCH_NAME" | grep "worktree" | awk '{print $2}')
    if [ -z "$WORKTREE_DIR" ]; then
        echo "Error: Could not find worktree for target '$TARGET'."
        exit 1
    fi
fi

# Convert WORKTREE_DIR to relative path if it's absolute and inside current dir
CURRENT_DIR=$(pwd)
WORKTREE_DIR=${WORKTREE_DIR#$CURRENT_DIR/}

if [ ! -d "$WORKTREE_DIR" ]; then
    echo "Error: Worktree directory '$WORKTREE_DIR' not found."
    exit 1
fi

echo "Targeting worktree: $WORKTREE_DIR (Branch: $BRANCH_NAME)"

# 2. Status Checks
IS_MERGED=false
if git merge-base --is-ancestor "$BRANCH_NAME" master 2>/dev/null; then
    IS_MERGED=true
fi

HAS_UNIQUE_COMMITS=true
if [ "$(git rev-parse "$BRANCH_NAME" 2>/dev/null)" == "$(git merge-base "$BRANCH_NAME" master 2>/dev/null)" ]; then
    HAS_UNIQUE_COMMITS=false
fi

# 3. Safety Checks
if [ $FORCE_LEVEL -lt 1 ]; then
    # Check for uncommitted changes
    MODIFIED=$(git -C "$WORKTREE_DIR" status --porcelain -uno)
    if [ -n "$MODIFIED" ]; then
        echo "Error: Worktree '$WORKTREE_DIR' has uncommitted changes. Use -f to override."
        echo "$MODIFIED"
        exit 1
    fi

    # Check if branch is merged
    if [ "$IS_MERGED" = false ] && [ "$HAS_UNIQUE_COMMITS" = true ]; then
        echo "Warning: Branch '$BRANCH_NAME' has unique commits and has not been merged into master."
        echo "Use -f to remove the worktree, or -ff to also delete the branch."
        exit 1
    fi
fi

# 4. Removal of Worktree
echo "Removing worktree '$WORKTREE_DIR'..."
git worktree remove --force "$WORKTREE_DIR"

if [ $? -ne 0 ]; then
    echo "Error: Failed to remove worktree."
    exit 1
fi

# 5. Cleanup Symlinks
if [ -L "$BRANCH_NAME" ]; then
    echo "Removing symlink '$BRANCH_NAME'..."
    rm "$BRANCH_NAME"
fi

# 6. Branch & Tag Deletion
if [ "$IS_MERGED" = true ] || [ "$HAS_UNIQUE_COMMITS" = false ]; then
    echo "Branch '$BRANCH_NAME' is merged or empty. Deleting branch and tag..."
    git branch -d "$BRANCH_NAME"
    git tag -d "${BRANCH_NAME}-start" 2>/dev/null
else
    if [ $FORCE_LEVEL -ge 2 ]; then
        echo "Force deleting unmerged branch '$BRANCH_NAME' and tag..."
        git branch -D "$BRANCH_NAME"
        git tag -d "${BRANCH_NAME}-start" 2>/dev/null
    else
        echo "Leaving unmerged branch '$BRANCH_NAME' and its tag intact."
    fi
fi

echo "Cleanup complete."
