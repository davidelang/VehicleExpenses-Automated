#!/bin/bash
# update-rules.sh
# Synchronizes infrastructure, policies, and mandates across all agent worktrees.
#
# Per user direction and approved plan: ALWAYS run this from the orchestration root
# on the `orchestration` branch (development context for all rule/infra changes).
# It publishes (cp + per-worktree commit) to the `master/` worktree and all `agent-N/`
# worktrees. New worktrees inherit correct content via `git worktree add ... master`
# (after the master branch tip has the updates).
#
# Shared brain uses physical copies (no hard links, no skip-worktree).

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
    "new_agent_prompt"
    ".gitignore"
    "docs/specs/OPERATIONAL_HANDBOOK.md"
    # New for Grok CLI parallel support (added per approved plan)
    "AGENT_MANDATES.md"
    "AGENTS.md"
    "GROK.md"
    "new_grok_agent_prompt"
    ".grok/config.toml"
    ".grok/hooks/plan-mode-hard-stops.js"
    # Tracked human-facing ritual document (magic words, forbidden phrases, post-handoff instructions).
    # Added per approved meta-plan for plan/execute cycle enforcement; synced to all worktrees.
    "MULTI_AGENT_USER_INSTRUCTIONS.md"
    # Safe pre-approved helper for the mandatory builds tag preflight (see AGENT_MANDATES.md).
    # Agents use TAG=$(./get-builds-tag.sh) to avoid repeated permission prompts
    # for the common git rev-parse logic needed before resets.
    "get-builds-tag.sh"
    # run-grok-planner: convenience launcher for dedicated narrow Planning Agent sessions.
    # Reads the narrow prompt file (written by the main orchestrator to
    # dev-ai-interaction/.planning-agent-prompt.txt) to allow the user direct
    # interaction with the planner until explicit approval. Synced for use from
    # any worktree.
    "run-grok-planner"
    # run-grok-master: specialized launcher for the top-level Master Orchestrator.
    # Coordinates planning (often via run-grok-planner), spawns and monitors
    # execution sub-agents, intervenes on run-away behavior, forces proper
    # resets, collects logs, and initiates recovery planning. Synced to all
    # worktrees.
    "run-grok-master"
)

# Note: AGENT_CONTEXT.md.template is intentionally NOT synced (per-agent instances are created once by setup_agent).

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
            # Overwrite with physical copy (shared brain is physical copies on the branch, not links)
            rm -f "$TARGET_FILE"
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

# 5. Promote Policies to User-tier (ensure they are active)
USER_POLICY_DIR="$HOME/.gemini/policies"
echo ">>> Promoting policies to User-tier: $USER_POLICY_DIR"
mkdir -p "$USER_POLICY_DIR"

# Copy repo policies to system with project-specific prefixes to avoid collisions
cp "$SOURCE_DIR/.gemini/policies/plans.toml" "$USER_POLICY_DIR/vehicle_expenses_plans.toml"
cp "$SOURCE_DIR/.gemini/policies/auto-saved.toml" "$USER_POLICY_DIR/vehicle_expenses_auto_saved.toml"

echo "--- Rule Update Sync Complete ---"
echo "Status: All worktrees are now synchronized with $SOURCE_DIR."
