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

# Ensure group-writable files in multi-user environment
umask 007

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
#
# CRITICAL RULE (language-agnostic):
# The FILES list (plus STAMP_FILES) defines the orchestration-infra /
# shared brain or stampable bootstrap that may be copied into worktree directories.
#
# Dual mode: stamp subset is safe for plain/standalone checkouts; full set only
# for targets participating in the full layout (see detection in the sync loop).
#
# Orchestration-infra changes must ONLY affect these explicitly listed items.
# They must NEVER touch actual application source content (whatever directory
# or tree contains the real program code for the project).
#
# Application source must remain completely independent of
# orchestration-infra syncs.
#
# You may update infra files such as:
#   update-rules.sh, fix-perms, launchers,
#   fix-*.sh (permission fixers), mandates, .grok/, filters, setup-project, etc.
# (i.e. things installed at setup-agent or setup-project time, or maintained
# as part of the development environment).
# These must be added to FILES (for full worktrees) and/or STAMP_FILES so they
# get physically copied + committed into worktrees via update-rules.sh.
# This prevents worktrees from reverting to stale versions on git reset/checkout.
FILES=(
    ".gemini/policies/plans.toml"
    ".gemini/policies/auto-saved.toml"
    ".gemini/system.md"
    ".gemini/system_prompt.md"
    "GEMINI.md"
    # TODO.md is NOT synced. It is per-branch backlog (master product backlog vs
    # orchestration meta). Blind cp from orchestration destroyed master cleanups.
    # Merge via MASTER_AGENT_MANDATE special-file protocol only (todo-append/todo-close).
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
    ".grok/agents/plan.md"
    ".grok/agents/explore.md"
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
    "run-grok-master"
    "run-grok-coder"
    "run-grok-orchestrator"
    "run-grok"
    "ve-env"
    "ve-refresh-shell.c"
    "run-antigravity"
    ".grok/skills/prepare-local-pr/SKILL.md"
    ".grok/skills/master-merge/SKILL.md"
    # Stable canonical guardrails block (cite by path in plans; do not paste).
    # This is the single source of truth for the short "Compliance & Execution
    # Guardrails (STANDARD BLOCK)" section. Placed at repo root (not under
    # dev-ai-interaction/) so it is a regular tracked file in every worktree
    # (dev-ai-interaction/ is gitignored and symlinked in agent worktrees for
    # sandbox sharing). Listed in FILES so it is physically copied + committed
    # into each agent-N/ and master/ worktree and can be `cat`'d reliably.
    # This makes drift immediately visible to the user.
    "standard-plan-compliance-block.md"
    # Permission bootstrap infrastructure (added for Unix user/group separation)
    "setup-project"
    "filter-apply-config"
    "filter-clean-config"
    "fix-perms"
    "fix-android-sdk-perms"
    "project.config.example"
    ".gitattributes"
    # Permission bootstrap (unified in fix-perms)
    # for logs, wrappers, sandbox. Must be synced via this script so that
    # worktrees have committed versions and don't revert on git reset).
    # Opt-in bootstrap helper (stampable + full layout)
    "enable-full-orchestration.sh"
    "setup_agent.sh"
    "remove_worktree.sh"
    "deploy"
    "build_app"
    "sync_infrastructure.sh"
    "update-rules.sh"
    # Controlled wrapper to safely append only to ENGINEERING_LOG.md.
    # Enforces format and works with chattr +a / restricted perms to stop agents
    # from editing history.
    "append-to-engineering-log"
    "todo-append"
    "todo-close"
    "run-as-primary.c"
    # Special-file merge drivers (eng-log append; refuse TODO/project-facts text merge)
    "git-merge-drivers/ve-englog"
    "git-merge-drivers/ve-special-refuse"
    "install-merge-drivers.sh"
    "merge-branch-into-master.sh"
    "hooks/post-checkout"
    "install-ve-refresh-shell.sh"
)

# Note: AGENT_CONTEXT.md.template is intentionally NOT synced (per-agent instances are created once by setup_agent).

# Dual-mode / separation support (Phase 4+):
# STAMP_FILES are the minimal one-time permission/bootstrap artifacts that may
# legitimately be present (and updated) even in a plain "standalone app" master
# checkout after the user has run the opt-in stamp.
# FULL_FILES (or the main FILES) are the active brain and only synced when the
# target worktree is participating in the full orchestration layout.
# Detection below is deliberately language-agnostic (no hard-coded "app/" etc.).
STAMP_FILES=(
    "setup-project"
    "filter-apply-config"
    "filter-clean-config"
    "fix-perms"
    "fix-android-sdk-perms"
    "project.config.example"
    ".gitattributes"
    "enable-full-orchestration.sh"
    "setup_agent.sh"
    "remove_worktree.sh"
    "deploy"
    "build_app"
    "sync_infrastructure.sh"
    "update-rules.sh"
)

# 4. Push updates to all other worktrees
CURRENT_WT=$(git rev-parse --show-toplevel)
# Get absolute paths of all worktrees from git
WORKTREES=$(git worktree list --porcelain | grep "^worktree " | cut -d' ' -f2-)

for WT in $WORKTREES; do
    if [ "$WT" == "$CURRENT_WT" ]; then
        continue
    fi

    echo ">>> Syncing rules to worktree: $WT"

    # Dual-mode decision (language-agnostic, no hard-coded app/ paths):
    # If the target already has full brain markers (launchers or is agent-* style)
    # then push the full FILES list; otherwise only the stamp subset.
    # This keeps heavy orchestration files out of pristine plain master views
    # while still supporting opt-in and physical copy model within the repo.
    TARGET_USES_FULL=0
    if [ -f "$WT/run-grok-master" ] || [ -f "$WT/update-rules.sh" ] || [[ "$(basename "$WT")" == agent-* ]]; then
        TARGET_USES_FULL=1
    fi

    COPY_LIST=("${STAMP_FILES[@]}")
    if [ "$TARGET_USES_FULL" -eq 1 ]; then
        COPY_LIST=("${FILES[@]}")
    fi

    echo "    (mode: $( [ "$TARGET_USES_FULL" -eq 1 ] && echo full || echo stamp-only ))"

    # Stash any existing staged changes before overwriting working tree files.
    # This allows a clean add/commit of only the synced infra, then restores
    # the previous staged state (so agent work is not lost or mixed).
    # Only stash if there is actually something staged.
    # Use --staged so we only set aside the index (staged files) and do not
    # snapshot/restore dirty working-tree files for the synced items (cp must win
    # on infra files; non-infra unstaged work remains untouched).
    stashed=0
    if [ -d "$WT" ]; then
        if ( cd "$WT" && ! git diff --staged --quiet 2>/dev/null ); then
            echo "  Stashing staged changes temporarily in $WT before sync..."
            if ( cd "$WT" && git stash push --staged --message "update-rules temp: preserve staged work before infra sync" --quiet ); then
                stashed=1
            else
                echo "  WARNING: stash failed in $WT"
            fi
        fi
    fi

    # Ensure target directories exist and copy files.
    # Only the decided COPY_LIST (stamp or full) are touched for this target.
    # Application source content must never be overwritten by infra sync.
    # Primary user/group for ownership after cp (cp creates files owned by the
    # update-rules runner — often ai-orchestrator — which breaks dlang ./deploy
    # when +x is missing or only owner can exec).
    PRIMARY_USER=$(sed -n 's/^primary_user=//p' "$SOURCE_DIR/project.config" 2>/dev/null | tr -d '\r' | head -1)
    PRIMARY_USER=${PRIMARY_USER:-dlang}
    CODE_GROUP=$(sed -n 's/^code_group=//p' "$SOURCE_DIR/project.config" 2>/dev/null | tr -d '\r' | head -1)
    CODE_GROUP=${CODE_GROUP:-ai-code}

    for FILE in "${COPY_LIST[@]}"; do
        if [ -f "$SOURCE_DIR/$FILE" ]; then
            TARGET_FILE="$WT/$FILE"
            TARGET_DIR_PATH=$(dirname "$TARGET_FILE")
            mkdir -p "$TARGET_DIR_PATH"
            # Overwrite with physical copy (shared brain is physical copies on the branch, not links)
            rm -f "$TARGET_FILE"
            # Preserve mode from source when possible (keeps 100755 scripts executable)
            cp -p "$SOURCE_DIR/$FILE" "$TARGET_FILE" 2>/dev/null || cp "$SOURCE_DIR/$FILE" "$TARGET_FILE"
            # Prefer primary:ai-code so humans (dlang) own scripts they run
            chown "$PRIMARY_USER:$CODE_GROUP" "$TARGET_FILE" 2>/dev/null || true
            # If source was executable (or known launcher names), force +x for ugo
            if [ -x "$SOURCE_DIR/$FILE" ] || [[ "$FILE" == *.sh ]] || \
               [[ "$FILE" == deploy || "$FILE" == build_app || "$FILE" == gradlew ]] || \
               [[ "$FILE" == git-merge-drivers/* ]]; then
              chmod a+x "$TARGET_FILE" 2>/dev/null || true
            fi
        fi
    done

    # Ensure management/orchestration scripts end up executable (right perms).
    # Avoid a single chmod with globs that can fail the whole line; set each path.
    for _exe in "$WT"/deploy "$WT"/build_app "$WT"/gradlew \
                "$WT"/install-merge-drivers.sh "$WT"/merge-branch-into-master.sh; do
      [ -f "$_exe" ] || continue
      chown "$PRIMARY_USER:$CODE_GROUP" "$_exe" 2>/dev/null || true
      chmod a+x "$_exe" 2>/dev/null || true
    done
    find "$WT" -maxdepth 1 -type f -name '*.sh' -exec chown "$PRIMARY_USER:$CODE_GROUP" {} + 2>/dev/null || true
    find "$WT" -maxdepth 1 -type f -name '*.sh' -exec chmod a+x {} + 2>/dev/null || true
    if [ -d "$WT/git-merge-drivers" ]; then
      find "$WT/git-merge-drivers" -type f -exec chown "$PRIMARY_USER:$CODE_GROUP" {} + 2>/dev/null || true
      find "$WT/git-merge-drivers" -type f -exec chmod a+x {} + 2>/dev/null || true
    fi

    # Commit changes in the target worktree
    (
        cd "$WT" || exit
        
        # Clean up any legacy protections first to ensure git can see/modify them
        # Re-enable index tracking if it was skipped
        git update-index --no-skip-worktree "${COPY_LIST[@]}" 2>/dev/null
        # Restore write permissions
        chmod +w "${COPY_LIST[@]}" 2>/dev/null

        # Stage the files we just copied. Use -f for the infrastructure launchers
        # and block (robust against any transient ignore rules or new-file edge
        # cases during the batch). Do not blanket-suppress errors on the add;
        # surface problems so we can see why a sync would fail to commit.
        git add -f standard-plan-compliance-block.md get-builds-tag.sh run-grok-planner run-grok-master run-antigravity 2>&1 | cat
        git add "${COPY_LIST[@]}" 2>&1 | cat

        if ! git diff --staged --quiet; then
            echo "Changes detected in $WT, committing..."
            git commit -m "chore: Synchronize agent rules and infrastructure"
        else
            # As a final robustness measure for new files that may not have
            # produced a visible diff in some git edge cases, explicitly check
            # and force-add the critical new launchers + block if they are
            # untracked in this worktree, then commit if anything is now staged.
            for extra in standard-plan-compliance-block.md run-grok-planner run-grok-master run-antigravity; do
                if [ -f "$extra" ]; then
                    git add -f "$extra" 2>&1 | cat
                fi
            done
            if ! git diff --staged --quiet; then
                echo "Changes (including new launchers/block) detected in $WT after extra pass, committing..."
                git commit -m "chore: Synchronize agent rules and infrastructure"
            else
                echo "No changes needed for $WT."
            fi
        fi

        if [ "$stashed" -eq 1 ]; then
            echo "  Popping stash in $WT to restore previous state..."
            # Use --index so the previously-staged items are restored to the index
            # (staged) on top of the freshly committed infra sync. The working tree
            # state for non-infra files is left as it was (cp already forced infra).
            if git stash pop --index --quiet; then
                :
            else
                echo "  WARNING: git stash pop failed in $WT. You may need to resolve conflicts manually."
                echo "  Status after failed pop:"
                git status --short | cat
            fi
        fi
    )

    # Re-assert executables after commit (git may not preserve all mode bits in WT)
    for _exe in "$WT"/deploy "$WT"/build_app "$WT"/gradlew \
                "$WT"/install-merge-drivers.sh "$WT"/merge-branch-into-master.sh; do
      [ -f "$_exe" ] || continue
      chown "$PRIMARY_USER:$CODE_GROUP" "$_exe" 2>/dev/null || true
      chmod a+x "$_exe" 2>/dev/null || true
    done
    find "$WT" -maxdepth 1 -type f -name '*.sh' -exec chmod a+x {} + 2>/dev/null || true
    if [ -x "$WT/fix-perms" ]; then
        echo "  Ensuring perms on $WT via fix-perms..."
        "$WT/fix-perms" "$WT" 2>/dev/null || sudo "$WT/fix-perms" "$WT" 2>/dev/null || true
    fi
    # Merge drivers live in shared .git config (one install covers all worktrees)
    if [ -x "$WT/install-merge-drivers.sh" ]; then
      (cd "$WT" && ./install-merge-drivers.sh >/dev/null) || true
    fi
    # Deploy setuid-root ve-refresh-shell into each worktree (binary not in git)
    if [ -x "$SOURCE_DIR/install-ve-refresh-shell.sh" ]; then
      "$SOURCE_DIR/install-ve-refresh-shell.sh" "$WT" 2>/dev/null || \
        sudo "$SOURCE_DIR/install-ve-refresh-shell.sh" "$WT" 2>/dev/null || true
    fi
done

# Install merge drivers from orchestration root as well
if [ -x "$SOURCE_DIR/install-merge-drivers.sh" ]; then
  (cd "$SOURCE_DIR" && ./install-merge-drivers.sh) || true
fi

# 5. Promote Policies to User-tier (ensure they are active)
USER_POLICY_DIR="$HOME/.gemini/policies"
echo ">>> Promoting policies to User-tier: $USER_POLICY_DIR"
mkdir -p "$USER_POLICY_DIR"

# Copy repo policies to system with project-specific prefixes to avoid collisions
cp "$SOURCE_DIR/.gemini/policies/plans.toml" "$USER_POLICY_DIR/vehicle_expenses_plans.toml"
cp "$SOURCE_DIR/.gemini/policies/auto-saved.toml" "$USER_POLICY_DIR/vehicle_expenses_auto_saved.toml"

echo "--- Rule Update Sync Complete ---"
echo "Status: All worktrees are now synchronized with $SOURCE_DIR."
