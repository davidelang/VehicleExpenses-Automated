#!/bin/bash
# update-rules.sh
# Synchronizes infrastructure, policies, and mandates from the master branch.
# Can be run from any agent worktree.

# 1. Identify the repository root (handle physical directories and symlinks)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="$(pwd)"

echo "--- Rule Update Sync Starting ---"
echo "Target: $TARGET_DIR"

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

# 4. Pull updates from master branch
echo "Pulling shared infrastructure from 'master' branch..."
git checkout master -- "${FILES[@]}"

# 5. Restore read-only protection to infrastructure (if applicable)
echo "Ensuring read-only protection for policies and core mandates..."
chmod -w .gemini/policies/plans.toml .gemini/policies/auto-saved.toml GEMINI.md .gemini/system.md .gemini/system_prompt.md 2>/dev/null

# 6. Isolate local instance metadata
if git ls-files --error-unmatch AGENT_CONTEXT.md >/dev/null 2>&1; then
    echo "Isolating AGENT_CONTEXT.md (removing from local branch index)..."
    git rm --cached AGENT_CONTEXT.md
fi

echo "--- Rule Update Sync Complete ---"
echo "Status: CLEAN. Agent rules are now aligned with master."
