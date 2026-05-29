#!/bin/bash
# sync_infrastructure.sh
# Sync shared infrastructure from the master branch into the current worktree.
# This cleans up uncommitted changes in shared documentation and policies caused by shared symlinks.

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
)

echo "Updating infrastructure files from master branch..."
git checkout master -- "${FILES[@]}"

# Handle the case where AGENT_CONTEXT.md might still be tracked in the local branch
if git ls-files --error-unmatch AGENT_CONTEXT.md >/dev/null 2>&1; then
    echo "Isolating AGENT_CONTEXT.md (removing from local index)..."
    git rm --cached AGENT_CONTEXT.md
fi

echo "Infrastructure sync complete. Your workspace status should now be cleaner."
