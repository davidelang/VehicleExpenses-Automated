#!/bin/bash
# setup_agent.sh: Automate creation of agent worktrees
# Usage: ./setup_agent.sh branch-name

BRANCH_NAME=$1

if [ -z "$BRANCH_NAME" ]; then
    echo "Usage: $0 branch-name"
    exit 1
fi

# 1. Determine next available agent-N ID
N=1
while [ -d "agent-$N" ]; do
    N=$((N + 1))
done
AGENT_ID="agent-$N"

# 2. Create Worktree & Branch
echo "Creating worktree for $AGENT_ID on branch $BRANCH_NAME..."
# If the branch doesn't exist, create it with -b
if git show-ref --verify --quiet "refs/heads/$BRANCH_NAME"; then
    git worktree add "$AGENT_ID" "$BRANCH_NAME"
else
    # Create from current master
    git worktree add "$AGENT_ID" -b "$BRANCH_NAME" master
    # Create an annotated tag for git describe to anchor on
    echo "Creating annotated tag ${BRANCH_NAME}-start for versioning..."
    git tag -a "${BRANCH_NAME}-start" "$BRANCH_NAME" -m "Start of feature branch $BRANCH_NAME"
fi

if [ $? -ne 0 ]; then
    echo "Error: Failed to create worktree."
    exit 1
fi

# 3. Create convenience symlink for the branch
ln -s "$AGENT_ID" "$BRANCH_NAME"
echo "Created symlink: $BRANCH_NAME -> $AGENT_ID"

# 4. Setup Shared Rules (Hard Links - Read Only)
echo "Setting up shared brain (read-only hard links)..."
cd "$AGENT_ID"
mkdir -p .gemini/policies
ln ../.gemini/system.md .gemini/system.md
ln ../.gemini/system_prompt.md .gemini/system_prompt.md
ln ../.gemini/policies/plans.toml .gemini/policies/plans.toml
ln ../.gemini/policies/auto-saved.toml .gemini/policies/auto-saved.toml
mkdir .gemini/plans
touch .gemini/plans/.gitkeep
ln ../new_agent_prompt new_agent_prompt

# Protect Shared Rules
chmod 444 .gemini/system.md .gemini/system_prompt.md .gemini/policies/*.toml new_agent_prompt

# 5. Setup Sandbox (Symlink)
echo "Setting up sandbox symlink..."
ln -s ../dev-ai-interaction dev-ai-interaction

# 6. Initialize AGENT_CONTEXT.md
echo "Initializing AGENT_CONTEXT.md..."
if [ -f "../AGENT_CONTEXT.md.template" ]; then
    cp "../AGENT_CONTEXT.md.template" AGENT_CONTEXT.md
    sed -i "s/agent-X/$AGENT_ID/" AGENT_CONTEXT.md
    sed -i "s/UNASSIGNED/$BRANCH_NAME/" AGENT_CONTEXT.md
else
    cat > AGENT_CONTEXT.md <<EOF
# Agent Context: $AGENT_ID

- **Current Branch:** $BRANCH_NAME
- **Status:** INITIALIZED
EOF
fi

echo "Setup complete for $AGENT_ID."
echo "Agent can begin work via: cd $AGENT_ID (or cd $BRANCH_NAME)"
