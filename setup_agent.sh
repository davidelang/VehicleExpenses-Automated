#!/bin/bash
# setup_agent.sh: Automate creation of agent worktrees
# Usage: ./setup_agent.sh branch-name

BRANCH_NAME=$1

if [ -z "$BRANCH_NAME" ]; then
    echo "Usage: $0 branch-name"
    exit 1
fi

# 0. Safety Check: Don't name a branch agent-N
if [[ "$BRANCH_NAME" =~ ^agent-[0-9]+$ ]]; then
    echo "Error: Branch name cannot be '$BRANCH_NAME' (reserved for directory names)."
    echo "Use a descriptive name like 'feature-x' instead."
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
    # Create a lightweight tag for git describe to anchor on
    if git rev-parse "${BRANCH_NAME}-start" >/dev/null 2>&1; then
        echo "Versioning tag ${BRANCH_NAME}-start already exists. Skipping creation."
    else
        echo "Creating lightweight tag ${BRANCH_NAME}-start for versioning..."
        git tag "${BRANCH_NAME}-start" "$BRANCH_NAME"
    fi
fi

if [ $? -ne 0 ]; then
    echo "Error: Failed to create worktree."
    exit 1
fi

# 3. Create convenience symlink for the branch
if [ -e "${BRANCH_NAME}.wt" ]; then
    echo "Warning: File/link '${BRANCH_NAME}.wt' already exists. Skipping symlink creation."
else
    ln -s "$AGENT_ID" "${BRANCH_NAME}.wt"
    echo "Created symlink: ${BRANCH_NAME}.wt -> $AGENT_ID"
fi

# 4. Setup Agent Workspace Folders
echo "Setting up workspace metadata folders..."
cd "$AGENT_ID"
mkdir -p .gemini/policies
mkdir -p .gemini/plans
touch .gemini/plans/.gitkeep

# 5. Setup Sandbox (Symlink)
echo "Setting up sandbox symlink..."
ln -s ../dev-ai-interaction dev-ai-interaction

# 5b. Copy local.properties for Android builds
echo "Setting up local.properties..."
if [ -f "../master/local.properties" ]; then
    cp ../master/local.properties local.properties
elif [ -f "../local.properties" ]; then
    cp ../local.properties local.properties
fi

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
echo "Agent can begin work via: cd $AGENT_ID (or cd ${BRANCH_NAME}.wt)"

# 7. Optional: Start Agent session immediately if in an interactive terminal
if [ -t 0 ]; then
    echo "Starting agent session..."
    if [ -f "../run-antigravity" ]; then
        exec ../run-antigravity
    elif [ -f "../run-gemini" ]; then
        exec ../run-gemini
    else
        export GEMINI_PROJECT_ROOT=$(pwd)
        exec ~/git/gemini/bin/gemini -i "Read new_agent_prompt and follow its instructions." --include-directories ~/git/VehicleExpenses-automated/dev-ai-interaction/
    fi
fi
