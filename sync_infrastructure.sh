#!/bin/bash
# sync_infrastructure.sh
# Sync shared infrastructure (physical copies) into the current worktree.
# Run from orchestration root or worktrees as needed for local cleanup.
# See update-rules.sh (preferred for cross-worktree push from orchestration).
#
# IMPORTANT (language-agnostic):
# Only the explicitly listed FILES below are ever synced.
# Orchestration-infra must never overwrite application source content
# (the actual program code that belongs on the feature branches).
# The list may only contain infra, orchestration scripts, agent support
# files, etc.

# Ensure group-writable files in multi-user environment
umask 007

FILES=(
    ".gemini/policies/plans.toml"
    ".gemini/policies/auto-saved.toml"
    ".gemini/system.md"
    ".gemini/system_prompt.md"
    "GEMINI.md"
    # TODO.md intentionally not synced (per-branch backlog; see update-rules.sh)
    "MASTER_AGENT_MANDATE.md"
    "README-multi-agent.md"
    "agent_reminder"
    "new_agent_prompt"
    ".gitignore"
    "docs/specs/OPERATIONAL_HANDBOOK.md"
    # New for Grok CLI parallel support (added per approved plan + review)
    "AGENT_MANDATES.md"
    "AGENTS.md"
    "GROK.md"
    "new_grok_agent_prompt"
    ".grok/config.toml"
    ".grok/hooks/plan-mode-hard-stops.js"
)

echo "Updating infrastructure files from master branch..."
git checkout master -- "${FILES[@]}"

# Handle the case where AGENT_CONTEXT.md might still be tracked in the local branch
if git ls-files --error-unmatch AGENT_CONTEXT.md >/dev/null 2>&1; then
    echo "Isolating AGENT_CONTEXT.md (removing from local index)..."
    git rm --cached AGENT_CONTEXT.md
fi

echo "Infrastructure sync complete. Your workspace status should now be cleaner."
