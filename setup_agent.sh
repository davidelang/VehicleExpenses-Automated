#!/bin/bash
# setup_agent.sh: Automate creation of agent worktrees
# Usage: ./setup_agent.sh branch-name
#
# Always run from the orchestration root.
# This script now ensures the new worktree is fully permissioned
# (setgid dirs, correct ownership/modes for log/wrapper, run-as-primary setuid,
# no stray ACLs where possible, chattr +a, etc.) so it is immediately usable
# by agents without extra manual sudo steps.

BRANCH_NAME=$1

if [ -z "$BRANCH_NAME" ]; then
    echo "Usage: $0 branch-name"
    exit 1
fi

# Load primary config for ownership (orchestration root always has project.config)
if [ -f project.config ]; then
  . <(sed 's/=/ /; s/^/export /' project.config | grep -E '^(primary_user|code_group|shared_group|planning_user|coder_user|orchestrator_user)')
fi
PRIMARY_USER=${primary_user:-dlang}
CODE_GROUP=${code_group:-ai-code}
SHARED_GROUP=${shared_group:-ai-shared}
PLANNING_USER=${planning_user:-ai-planner}
CODER_USER=${coder_user:-ai-coder}
ORCHESTRATOR_USER=${orchestrator_user:-ai-orchestrator}

# Enforce correct creation umask for setgid inheritance
umask 007

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

# 7. Make this a *fully working* tree with all correct permissions.
#    Since we are always run from the orchestration root (which has the
#    authoritative latest copies and fixers), we:
#    - copy latest critical infra files
#    - build the setuid helper
#    - run the standard permission fixers (via sudo for root:ai-shared parts
#      like ENGINEERING_LOG.md ownership + chattr +a, and wrapper)
#    This ensures the new agent can immediately use append-to-engineering-log,
#    run-as-primary re-exec, correct setgid, etc. without extra steps.
echo "Applying full permissions to make $AGENT_ID fully working..."

AGENT_ABS=$(pwd)
PARENT_ROOT=".."

# Copy latest authoritative copies of key permission/infra files from orchestration root
# (ensures even if the branch tip was slightly behind, the tree is current)
for f in set-worktree-perms fix-engineering-log-perms fix-sudoers fix-this-worktree append-to-engineering-log run-as-primary.c; do
  if [ -f "$PARENT_ROOT/$f" ]; then
    cp -p "$PARENT_ROOT/$f" "$AGENT_ABS/$f" 2>/dev/null || true
  fi
done

# Build / ensure the setuid helper (run-as-primary) is present and correct
if [ -f "$AGENT_ABS/run-as-primary.c" ]; then
  echo "  Building run-as-primary setuid helper..."
  (cd "$AGENT_ABS" && gcc -O2 -Wall -o run-as-primary run-as-primary.c && chmod 4755 run-as-primary && chown "$PRIMARY_USER:$CODE_GROUP" run-as-primary) 2>/dev/null || echo "    (Warning: run-as-primary build/chmod may need gcc or manual fix)"
fi

# Create log file with minimal header if missing (fixer will harden it)
if [ ! -f "$AGENT_ABS/ENGINEERING_LOG.md" ]; then
  echo "## $(date +%Y-%m-%d) - Initial log for $AGENT_ID" > "$AGENT_ABS/ENGINEERING_LOG.md"
fi

cd "$PARENT_ROOT"   # return to orchestration root for calling fixers

# Apply the full permission model (this sets 2775/2770 setgid, 660/664, chattr +a,
# root:ai-shared for log+wrapper, dlang:ai-code for run-as-primary, strips bad ACLs where
# possible, etc.). The fixers are designed for this and use sudo internally where needed.
echo "  Running set-worktree-perms for $AGENT_ID..."
sudo ./set-worktree-perms "$AGENT_ID" 2>/dev/null || echo "    (Note: 'sudo ./set-worktree-perms $AGENT_ID' may be needed if sudo tty restricted)"

echo "  Running fix-engineering-log-perms (hardens log + wrapper + sudoers)..."
sudo ./fix-engineering-log-perms 2>/dev/null || echo "    (Note: 'sudo ./fix-engineering-log-perms' may be needed if sudo tty restricted)"

# Re-ensure the setuid binary (fixers may have touched modes)
if [ -f "$AGENT_ABS/run-as-primary" ]; then
  chown "$PRIMARY_USER:$CODE_GROUP" "$AGENT_ABS/run-as-primary" 2>/dev/null || true
  chmod 4755 "$AGENT_ABS/run-as-primary" 2>/dev/null || true
fi

cd "$AGENT_ABS" || true   # restore for the optional launcher exec below

echo "Setup complete for $AGENT_ID (fully permissioned)."
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
