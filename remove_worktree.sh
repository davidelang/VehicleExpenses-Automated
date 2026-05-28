#!/bin/bash
# remove_worktree.sh: Safely remove agent worktrees and their branches
# Usage: ./remove_worktree.sh [-f|--force] agent-N|branch-name

FORCE=false
TARGET=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -f|--force)
            FORCE=true
            shift
            ;;
        *)
            TARGET="$1"
            shift
            ;;
    esac
done

if [ -z "$TARGET" ]; then
    echo "Usage: $0 [-f|--force] agent-N|branch-name"
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

# 2. Safety Checks
if [ "$FORCE" = false ]; then
    # Check for uncommitted changes
    MODIFIED=$(git -C "$WORKTREE_DIR" status --porcelain -uno)
    if [ -n "$MODIFIED" ]; then
        echo "Error: Worktree '$WORKTREE_DIR' has uncommitted changes. Use -f to override."
        echo "$MODIFIED"
        exit 1
    fi

    # Check if branch is merged into master
    if ! git merge-base --is-ancestor "$BRANCH_NAME" master; then
        echo "Warning: Branch '$BRANCH_NAME' has not been merged into master. Use -f to override."
        exit 1
    fi
fi

# 3. Removal
echo "Removing worktree '$WORKTREE_DIR'..."
git worktree remove --force "$WORKTREE_DIR"

if [ $? -ne 0 ]; then
    echo "Error: Failed to remove worktree."
    exit 1
fi

# 4. Cleanup Symlinks
if [ -L "$BRANCH_NAME" ]; then
    echo "Removing symlink '$BRANCH_NAME'..."
    rm "$BRANCH_NAME"
fi

# 5. Branch & Tag Deletion
if git merge-base --is-ancestor "$BRANCH_NAME" master; then
    echo "Branch '$BRANCH_NAME' is merged. Deleting branch and tag..."
    git branch -d "$BRANCH_NAME"
    git tag -d "${BRANCH_NAME}-start" 2>/dev/null
else
    if [ "$FORCE" = true ]; then
        echo "Force deleting unmerged branch '$BRANCH_NAME' and tag..."
        git branch -D "$BRANCH_NAME"
        git tag -d "${BRANCH_NAME}-start" 2>/dev/null
    else
        echo "Leaving unmerged branch '$BRANCH_NAME' and its tag intact."
    fi
fi

echo "Cleanup complete."
