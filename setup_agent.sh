#!/bin/bash
# setup_agent.sh: Automate creation of agent worktrees
# Usage: ./setup_agent.sh agent-N branch-name

AGENT_ID=$1
BRANCH_NAME=$2

if [ -z "$AGENT_ID" ] || [ -z "$BRANCH_NAME" ]; then
    echo "Usage: $0 agent-N branch-name"
    exit 1
fi

# 1. Create Worktree & Branch
echo "Creating worktree for $AGENT_ID on branch $BRANCH_NAME..."
# If the branch doesn't exist, create it with -b
if git show-ref --verify --quiet "refs/heads/$BRANCH_NAME"; then
    git worktree add "$AGENT_ID" "$BRANCH_NAME"
else
    # Create from current master
    git worktree add "$AGENT_ID" -b "$BRANCH_NAME" master
    # Create an annotated tag for git describe to anchor on
    echo "Creating annotated tag ${BRANCH_NAME}-start for versioning..."
    git tag -a "${BRANCH_NAME}-start" -m "Start of feature branch $BRANCH_NAME"
fi

if [ $? -ne 0 ]; then
    echo "Error: Failed to create worktree."
    exit 1
fi

# 2. Setup Untracked Symlinks
# Note: .gemini and build_app are tracked symlinks in the branch itself,
# so we only need to link the sandbox which is outside the repo.
echo "Setting up sandbox symlink..."
cd "$AGENT_ID"
ln -s ../dev-ai-interaction dev-ai-interaction

# 3. Initialize AGENT_CONTEXT.md
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
echo "Agent can begin work via: cd $AGENT_ID"
