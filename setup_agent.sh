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

is_valid_worktree_dir() {
  local p="$1"
  [ -f "$p/.git" ] && grep -q '^gitdir: ' "$p/.git" 2>/dev/null
}

# 1. Determine next available agent-N ID (reuse slot if prior setup left a non-worktree stub)
N=1
while true; do
  if [ ! -d "agent-$N" ]; then
    AGENT_ID="agent-$N"
    break
  fi
  if is_valid_worktree_dir "agent-$N"; then
    N=$((N + 1))
    continue
  fi
  echo "Removing stale non-worktree directory agent-$N (failed prior setup_agent run)..."
  if [ -f "agent-$N/ENGINEERING_LOG.md" ]; then
    sudo chattr -a "agent-$N/ENGINEERING_LOG.md" 2>/dev/null || true
  fi
  if ! rm -rf "agent-$N" 2>/dev/null; then
    sudo rm -rf "agent-$N" 2>/dev/null || {
      echo "Error: cannot remove stale agent-$N (try: sudo rm -rf agent-$N)"
      exit 1
    }
  fi
  AGENT_ID="agent-$N"
  break
done

# Safety: refuse if branch name would conflict with an existing directory (other than the agent dir we chose)
if [ -d "$BRANCH_NAME" ] && [ "$BRANCH_NAME" != "$AGENT_ID" ]; then
    echo "Error: A directory named '$BRANCH_NAME' already exists. Choose a different branch name to avoid conflict."
    exit 1
fi
if [ -e "${BRANCH_NAME}.wt" ] && [ ! -L "${BRANCH_NAME}.wt" ]; then
    echo "Error: '${BRANCH_NAME}.wt' exists but is not a symlink. Clean it up first or choose different name."
    exit 1
fi

# Ensure main .git/config is not root-owned (breaks git for users without active ai-shared GID)
if [ -f .git/config ] && [ "$(stat -c '%U' .git/config 2>/dev/null || echo '')" = "root" ]; then
  echo "Repairing root-owned .git/config (requires sudo once)..."
  if ! sudo chown "$PRIMARY_USER:$SHARED_GROUP" .git/config 2>/dev/null; then
    echo "Error: .git/config is owned by root. Run:"
    echo "  sudo chown $PRIMARY_USER:$SHARED_GROUP .git/config"
    echo "  sudo ./fix-perms --verbose"
    exit 1
  fi
  chmod 660 .git/config 2>/dev/null || true
fi

# 2. Create Worktree & Branch
echo "Creating worktree for $AGENT_ID on branch $BRANCH_NAME..."

WORKTREE_ERR=0
if git show-ref --verify --quiet "refs/heads/$BRANCH_NAME"; then
    git worktree add --no-checkout "$AGENT_ID" "$BRANCH_NAME" || WORKTREE_ERR=$?
else
    # Create from current master
    git worktree add --no-checkout "$AGENT_ID" -b "$BRANCH_NAME" master || WORKTREE_ERR=$?
    if [ "$WORKTREE_ERR" -eq 0 ]; then
      # Create (or force-update) a lightweight tag for git describe to anchor on.
      echo "Creating/updating lightweight tag ${BRANCH_NAME}-start for versioning..."
      git tag -f "${BRANCH_NAME}-start" "$BRANCH_NAME" || WORKTREE_ERR=$?
    fi
fi

if [ "$WORKTREE_ERR" -ne 0 ]; then
    echo "Error: Failed to create worktree."
    exit 1
fi

# Copy project.config and filter scripts before checkout so smudge filters can
# substitute @@ tokens during population.
if [ -f project.config ]; then
  cp project.config "$AGENT_ID/project.config"
fi
for f in filter-apply-config filter-clean-config; do
  if [ -f "$f" ]; then
    cp "$f" "$AGENT_ID/$f"
  fi
done

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
cd "$AGENT_ID"
mkdir -p .gemini/policies
mkdir -p .gemini/plans
touch .gemini/plans/.gitkeep

# 5. Setup Sandbox (Symlink)
ln -s ../dev-ai-interaction dev-ai-interaction

# 5b. Copy local.properties for Android builds
if [ -f "../master/local.properties" ]; then
    cp ../master/local.properties local.properties
elif [ -f "../local.properties" ]; then
    cp ../local.properties local.properties
fi

# 6. Initialize AGENT_CONTEXT.md
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

# Now populate the worktree contents. Because we pre-copied project.config and the
# filter scripts, the smudge filters will run with the config available and
# perform the proper substitutions for @@ tokens.
if ! git checkout .; then
  echo "Error: Failed to populate worktree (git checkout .)."
  cd ..
  git worktree remove --force "$AGENT_ID" 2>/dev/null || rm -rf "$AGENT_ID"
  exit 1
fi

# Verify smudge filters ran (project.config must exist before checkout; @@ tokens must be gone after).
SMUDGE_CHECK_FAILED=0
if [ ! -f project.config ]; then
  echo "Error: project.config missing after checkout (smudge filters cannot run without it)."
  SMUDGE_CHECK_FAILED=1
else
  STAMPED_FILES=(
    run-grok run-grok-master run-grok-planner
    setup-project set-worktree-perms set-sandbox-perms
  )
  for f in "${STAMPED_FILES[@]}"; do
    [ -f "$f" ] || continue
    if grep -q '@@' "$f" 2>/dev/null; then
      echo "Error: Unsubstituted @@ tokens remain in $f (smudge filter did not run correctly)."
      SMUDGE_CHECK_FAILED=1
    fi
  done
fi
if [ "$SMUDGE_CHECK_FAILED" -ne 0 ]; then
  echo "Hint: ensure project.config and filter-apply-config were copied before 'git checkout .'."
  cd ..
  git worktree remove --force "$AGENT_ID" 2>/dev/null || rm -rf "$AGENT_ID"
  exit 1
fi

if [ "$(id -un)" != "$PRIMARY_USER" ] && [ "$EUID" -ne 0 ]; then
  sudo -u "$PRIMARY_USER" git config core.sharedRepository group
else
  git config core.sharedRepository group
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
AGENT_ABS=$(pwd)
PARENT_ROOT=".."

# Copy latest authoritative copies of key permission/infra files from orchestration root
# (ensures even if the branch tip was slightly behind, the tree is current)
for f in append-to-engineering-log run-as-primary.c; do
  if [ -f "$PARENT_ROOT/$f" ]; then
    cp -p "$PARENT_ROOT/$f" "$AGENT_ABS/$f" 2>/dev/null || true
  fi
done

# Build / ensure the setuid helper (run-as-primary) is present and correct
if [ -f "$AGENT_ABS/run-as-primary.c" ]; then
  (cd "$AGENT_ABS" && gcc -O2 -Wall -o run-as-primary run-as-primary.c && chmod 4755 run-as-primary && chown "$PRIMARY_USER:$CODE_GROUP" run-as-primary) 2>/dev/null || echo "    Warning: run-as-primary build/chmod may need gcc or manual fix"
fi

# Create log file with minimal header if missing (fixer will harden it)
if [ ! -f "$AGENT_ABS/ENGINEERING_LOG.md" ]; then
  echo "## $(date +%Y-%m-%d) - Initial log for $AGENT_ID" > "$AGENT_ABS/ENGINEERING_LOG.md"
fi 2>/dev/null || true

cd "$PARENT_ROOT"   # return to orchestration root 

# Use unified fix-perms for the new tree (pass --skip-sudoers so sudoers rules
# are only (re)installed at true initial setup-project time).
# Silent on success (no output if it works).
sudo ./fix-perms --skip-sudoers "$AGENT_ABS" 2>/dev/null || true

# Re-lock setuid binary silently (in case not covered).
if [ -f "$AGENT_ABS/run-as-primary" ]; then
  chown "$PRIMARY_USER:$CODE_GROUP" "$AGENT_ABS/run-as-primary" 2>/dev/null || true
  chmod 4755 "$AGENT_ABS/run-as-primary" 2>/dev/null || true
fi

cd "$AGENT_ABS" || true   # restore for the optional launcher exec below
# success is silent (Unix convention)

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
