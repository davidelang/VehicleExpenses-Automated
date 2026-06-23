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

# 0. Safety Check: Don't name a branch agent-N (reserved for auto-generated dirs)
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

# Safety: refuse if branch name would conflict with an existing directory (other than the agent dir we chose)
if [ -d "$BRANCH_NAME" ] && [ "$BRANCH_NAME" != "$AGENT_ID" ]; then
    echo "Error: A directory named '$BRANCH_NAME' already exists. Choose a different branch name to avoid conflict."
    exit 1
fi
if [ -e "${BRANCH_NAME}.wt" ] && [ ! -L "${BRANCH_NAME}.wt" ]; then
    echo "Error: '${BRANCH_NAME}.wt' exists but is not a symlink. Clean it up first or choose different name."
    exit 1
fi

# 2. Create Worktree & Branch
echo "Creating worktree for $AGENT_ID on branch $BRANCH_NAME..."
# Temporarily disable the manage-configs filter (which runs bash ./filter-apply-config)
# to prevent "No such file or directory" errors during initial checkout into a fresh
# worktree dir (the filter scripts aren't present in the target yet).
# The files in the commit are already expanded; setup_agent and post-checkout handle the rest.
if git show-ref --verify --quiet "refs/heads/$BRANCH_NAME"; then
    git -c filter.manage-configs.clean=cat -c filter.manage-configs.smudge=cat worktree add "$AGENT_ID" "$BRANCH_NAME"
else
    # Create from current master
    git -c filter.manage-configs.clean=cat -c filter.manage-configs.smudge=cat worktree add "$AGENT_ID" -b "$BRANCH_NAME" master
    # Create (or force-update) a lightweight tag for git describe to anchor on.
    # If a stale tag exists from a previously removed worktree/branch with the same name,
    # we force it to the new branch's start point. This is the correct behavior when
    # there is no current branch/worktree using that name.
    echo "Creating/updating lightweight tag ${BRANCH_NAME}-start for versioning..."
    git tag -f "${BRANCH_NAME}-start" "$BRANCH_NAME"
fi

if [ $? -ne 0 ]; then
    echo "Error: Failed to create worktree."
    exit 1
fi

# 3. Create convenience symlink for the branch
if [ -e "${BRANCH_NAME}.wt" ]; then
    if [ -L "${BRANCH_NAME}.wt" ] && [ "$(readlink "${BRANCH_NAME}.wt")" = "$AGENT_ID" ]; then
        echo "Symlink '${BRANCH_NAME}.wt' already points correctly. Skipping."
    else
        echo "Warning: '${BRANCH_NAME}.wt' exists but is not the correct symlink. Removing and recreating."
        rm -f "${BRANCH_NAME}.wt"
        ln -s "$AGENT_ID" "${BRANCH_NAME}.wt"
        echo "Created symlink: ${BRANCH_NAME}.wt -> $AGENT_ID"
    fi
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

cd "$PARENT_ROOT"   # return to orchestration root 

# Apply permissions with *minimal* escalation.
# Run the general worktree fixer as current user (dlang can do setgid dirs, most chowns/chmods).
# Only use sudo for the bits that truly require root: chattr +/-a and chown root: for the log+wrapper.
echo "  Applying worktree permissions (as current user where possible)..."
./set-worktree-perms "$AGENT_ID" || echo "    (partial; root bits below if needed)"

echo "  Escalating *only* for root-required bits (chattr, root ownership on log/wrapper)..."
sudo bash -c '
  set -euo pipefail
  AGENT="'"$AGENT_ABS"'"
  SHARED="'"$SHARED_GROUP"'"
  # Log
  chattr -a "$AGENT/ENGINEERING_LOG.md" 2>/dev/null || true
  chown root:"$SHARED" "$AGENT/ENGINEERING_LOG.md"
  chmod 660 "$AGENT/ENGINEERING_LOG.md"
  chattr +a "$AGENT/ENGINEERING_LOG.md"
  # Wrapper
  chattr -a "$AGENT/append-to-engineering-log" 2>/dev/null || true
  chown root:"$SHARED" "$AGENT/append-to-engineering-log"
  chmod 2755 "$AGENT/append-to-engineering-log"
  # Optional: ensure no ACLs on these two (pure Unix)
  setfacl -b "$AGENT/ENGINEERING_LOG.md" "$AGENT/append-to-engineering-log" 2>/dev/null || true
' || echo "    (Note: may need interactive sudo for chattr/root chown; run 'sudo ./fix-engineering-log-perms' if needed)"

# Re-ensure the setuid binary
if [ -f "$AGENT_ABS/run-as-primary" ]; then
  chown "$PRIMARY_USER:$CODE_GROUP" "$AGENT_ABS/run-as-primary" 2>/dev/null || true
  chmod 4755 "$AGENT_ABS/run-as-primary" 2>/dev/null || true
fi

# Run the log/wrapper canonical fixer with sudo only if it will do system things (sudoers).
# We do the file bits above with minimal sudo; the fixer also updates sudoers.
echo "  Running fix-engineering-log-perms for sudoers + canonical (sudo only as needed)..."
sudo ./fix-engineering-log-perms 2>/dev/null || echo "    (Note: 'sudo ./fix-engineering-log-perms' may be needed for full sudoers update)"

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
