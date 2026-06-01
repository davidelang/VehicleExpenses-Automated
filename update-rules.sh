#!/bin/bash
# update-rules.sh
# Synchronizes infrastructure, policies, and mandates across all agent worktrees.
# This script should be run from the worktree containing the source of truth (usually master).

# 1. Identify the repository root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_DIR="$(pwd)"

echo "--- Rule Update Sync Starting ---"
echo "Source: $SOURCE_DIR"

# 2. Check if we are in a Git worktree
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "ERROR: This script must be run from inside a Git worktree."
    exit 1
fi

# 3. Defined Shared Infrastructure Files
FILES=(
    ".gemini/policies/plans.toml"
    ".gemini/policies/auto-saved.toml"
    ".gemini/system.md"
    ".gemini/system_prompt.md"
    "GEMINI.md"
    "TODO.md"
    "MASTER_AGENT_MANDATE.md"
    "README-multi-agent.md"
    "agent_reminder"
    "AGENT_CONTEXT.md.template"
    "new_agent_prompt"
    ".gitignore"
    "docs/specs/OPERATIONAL_HANDBOOK.md"
)

# 4. Push updates to all other worktrees
CURRENT_WT=$(git rev-parse --show-toplevel)
# Get absolute paths of all worktrees from git
WORKTREES=$(git worktree list --porcelain | grep "^worktree " | cut -d' ' -f2-)

for WT in $WORKTREES; do
    if [ "$WT" == "$CURRENT_WT" ]; then
        continue
    fi

    echo ">>> Syncing rules to worktree: $WT"
    
    # Ensure target directories exist and copy files
    for FILE in "${FILES[@]}"; do
        if [ -f "$SOURCE_DIR/$FILE" ]; then
            TARGET_FILE="$WT/$FILE"
            TARGET_DIR_PATH=$(dirname "$TARGET_FILE")
            mkdir -p "$TARGET_DIR_PATH"
            cp "$SOURCE_DIR/$FILE" "$TARGET_FILE"
        fi
    done

    # Commit changes in the target worktree
    (
        cd "$WT" || exit
        
        # Clean up any legacy protections first to ensure git can see/modify them
        # Re-enable index tracking if it was skipped
        git update-index --no-skip-worktree "${FILES[@]}" 2>/dev/null
        # Restore write permissions
        chmod +w "${FILES[@]}" 2>/dev/null

        git add "${FILES[@]}" 2>/dev/null
        
        if ! git diff --staged --quiet; then
            echo "Changes detected in $WT, committing..."
            git commit -m "chore: Synchronize agent rules and infrastructure"
        else
            echo "No changes needed for $WT."
        fi
    )
done

echo "--- Rule Update Sync Complete ---"
echo "Status: All worktrees are now synchronized with $SOURCE_DIR."
