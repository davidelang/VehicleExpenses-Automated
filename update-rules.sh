#!/bin/bash
# update-rules.sh
# Synchronizes infrastructure, policies, and mandates across all agent worktrees.
#
# ALWAYS run from the orchestration root on the `orchestration` branch.
# Publishes (cp + per-worktree commit) to master/ and agent-N/.
# Per-worktree commit is mandatory — ad-hoc cp without commit blocks ./build_app.
#
# Safety (default): do not clobber a worktree file that is "ahead" of orchestration
# for that path. Use --force to publish orch over worktree anyway.
# Stash-fail, extra staged paths, prunable / grok-worktrees / third_party/*/src /
# foreign git-common-dir: skip that worktree (no copy, no commit) and exit non-zero.
#   --dry-run   show actions only
#   -f/--force  always take orchestration content when it differs
#
# Decision per path (when content differs):
#   - worktree dirty (uncommitted) → SKIP (alert), unless --force
#   - worktree blob appears in orch history for path, orch moved on → COPY (clobber)
#   - orch blob appears in worktree history for path, worktree moved on → SKIP
#   - neither / diverged → SKIP unless --force
# Content identical → SKIP (noop). mtime is logged only as a soft clue.
#
# Shared brain uses physical copies (no hard links, no skip-worktree).

# Intentionally no `set -e`: many best-effort chown/chmod/git steps use `|| true`.
set -u
# Source/scripts: umask 002 (PERMISSIONS_MODEL). umask 007 + chmod +x → 774
# and planner (not in ai-code) cannot exec helpers.
umask 002

FORCE=0
DRY_RUN=0
while [ $# -gt 0 ]; do
  case "$1" in
    -f|--force) FORCE=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help)
      cat <<'EOF'
Usage: ./update-rules.sh [--dry-run] [-f|--force]

  --dry-run   Print would-copy / would-skip without writing or committing
  -f,--force  Overwrite worktree files even if dirty or worktree-ahead/diverged
EOF
      exit 0
      ;;
    *)
      echo "Unknown option: $1 (try --help)" >&2
      exit 1
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_DIR="$(pwd)"

echo "--- Rule Update Sync Starting ---"
echo "Source: $SOURCE_DIR"
echo "Mode: force=$FORCE dry_run=$DRY_RUN"

if [ ! -f "$SOURCE_DIR/ve-resolve-orch" ]; then
    echo "ERROR: missing $SOURCE_DIR/ve-resolve-orch" >&2
    exit 1
fi
# shellcheck source=/dev/null
. "$SOURCE_DIR/ve-resolve-orch"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "ERROR: This script must be run from inside a Git worktree."
    exit 1
fi

# file_blob_in_history REPO_DIR REV PATH BLOB
# True if some commit reachable from REV that touches PATH has that blob for PATH.
file_blob_in_history() {
  local repo="$1" rev="$2" path="$3" want="$4"
  local c b
  while IFS= read -r c; do
    [ -n "$c" ] || continue
    b=$(git -C "$repo" rev-parse "$c:$path" 2>/dev/null) || continue
    if [ "$b" = "$want" ]; then
      return 0
    fi
  done < <(git -C "$repo" rev-list "$rev" -- "$path" 2>/dev/null)
  return 1
}

# decide_sync_action WT FILE
# Sets global DECIDE_ACTION=copy|skip and DECIDE_REASON=...
DECIDE_ACTION=
DECIDE_REASON=
decide_sync_action() {
  local wt="$1" file="$2"
  local src="$SOURCE_DIR/$file" dst="$wt/$file"
  DECIDE_ACTION=copy
  DECIDE_REASON="missing or default publish"

  if [ ! -f "$src" ]; then
    DECIDE_ACTION=skip
    DECIDE_REASON="source missing"
    return
  fi

  if [ ! -f "$dst" ]; then
    DECIDE_ACTION=copy
    DECIDE_REASON="worktree missing file"
    return
  fi

  local src_hash dst_hash
  src_hash=$(git hash-object "$src" 2>/dev/null || cksum "$src" | awk '{print $1}')
  dst_hash=$(git hash-object "$dst" 2>/dev/null || cksum "$dst" | awk '{print $1}')
  if [ "$src_hash" = "$dst_hash" ]; then
    DECIDE_ACTION=skip
    DECIDE_REASON="content identical"
    return
  fi

  # Soft clue only
  local sm dm
  sm=$(stat -c %Y "$src" 2>/dev/null || echo 0)
  dm=$(stat -c %Y "$dst" 2>/dev/null || echo 0)
  if [ "$dm" -gt "$sm" ]; then
    echo "    note: $file worktree mtime newer than orch (clue only; not decisive)"
  fi

  local porcelain
  porcelain=$(git -C "$wt" status --porcelain -- "$file" 2>/dev/null || true)
  if [ -n "$porcelain" ]; then
    if [ "$FORCE" -eq 1 ]; then
      DECIDE_ACTION=copy
      DECIDE_REASON="FORCE over dirty worktree ($porcelain)"
    else
      DECIDE_ACTION=skip
      DECIDE_REASON="DIRTY worktree (uncommitted edits) — commit/backport or --force"
    fi
    return
  fi

  # Git blob comparison via history ancestry of content
  local orch_rev wt_rev orch_blob wt_blob
  orch_rev=$(git -C "$SOURCE_DIR" rev-parse HEAD 2>/dev/null || true)
  wt_rev=$(git -C "$wt" rev-parse HEAD 2>/dev/null || true)
  orch_blob=$(git -C "$SOURCE_DIR" rev-parse "HEAD:$file" 2>/dev/null || true)
  wt_blob=$(git -C "$wt" rev-parse "HEAD:$file" 2>/dev/null || true)

  # Prefer hashing working-tree source (may include uncommitted orch edits we are publishing)
  local src_blob
  src_blob=$(git hash-object "$src" 2>/dev/null || true)
  [ -n "$src_blob" ] && orch_blob="$src_blob"

  if [ -z "$wt_blob" ]; then
    # tracked? untracked file with content
    wt_blob=$(git hash-object "$dst" 2>/dev/null || true)
  fi

  if [ -n "$wt_blob" ] && [ -n "$orch_blob" ] && [ "$wt_blob" = "$orch_blob" ]; then
    DECIDE_ACTION=skip
    DECIDE_REASON="content identical (blob)"
    return
  fi

  local wt_in_orch=0 orch_in_wt=0
  if [ -n "$wt_blob" ] && [ -n "$orch_rev" ]; then
    if file_blob_in_history "$SOURCE_DIR" "$orch_rev" "$file" "$wt_blob"; then
      wt_in_orch=1
    fi
  fi
  if [ -n "$orch_blob" ] && [ -n "$wt_rev" ]; then
    if file_blob_in_history "$wt" "$wt_rev" "$file" "$orch_blob"; then
      orch_in_wt=1
    fi
  fi

  # worktree content is an older orch version → orch moved on → clobber
  if [ "$wt_in_orch" -eq 1 ] && [ "$orch_in_wt" -eq 0 ]; then
    DECIDE_ACTION=copy
    DECIDE_REASON="worktree blob is ancestor content on orch (orch ahead)"
    return
  fi

  # orch content still in worktree history, worktree changed away → worktree ahead
  if [ "$orch_in_wt" -eq 1 ] && [ "$wt_in_orch" -eq 0 ]; then
    if [ "$FORCE" -eq 1 ]; then
      DECIDE_ACTION=copy
      DECIDE_REASON="FORCE over worktree-ahead content"
    else
      DECIDE_ACTION=skip
      DECIDE_REASON="worktree-ahead (orch blob in wt history; wt moved on) — backport or --force"
    fi
    return
  fi

  # both or neither: divergent or unknown
  if [ "$FORCE" -eq 1 ]; then
    DECIDE_ACTION=copy
    DECIDE_REASON="FORCE over divergent/unknown history (wt_in_orch=$wt_in_orch orch_in_wt=$orch_in_wt)"
  else
    DECIDE_ACTION=skip
    DECIDE_REASON="divergent/unknown (wt_in_orch=$wt_in_orch orch_in_wt=$orch_in_wt) — inspect, backport, or --force"
  fi
}

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
    "docs/ENVIRONMENT_SETUP.md"
    "docs/reference/ORCHESTRATION_MERGE_INFRA_SYNC.md"
    "docs/reference/MERGE_POSTMORTEM_IMPROVE_PUMP_CLASSIFICATION.md"
    # New for Grok CLI parallel support (added per approved plan)
    "AGENT_MANDATES.md"
    "AGENTS.md"
    "GROK.md"
    "new_grok_agent_prompt"
    ".grok/config.toml"
    ".grok/hooks/plan-mode-hard-stops.js"
    ".grok/agents/plan.md"
    ".grok/agents/explore.md"
    ".grok/prompts/planning-subagent.md"
    ".grok/prompts/execution-subagent.md"
    ".grok/prompts/dedicated-planner.md"
    ".grok/prompts/compose-session-prompt.sh"
    ".grok/prompts/role-coder.md"
    ".grok/prompts/role-planner.md"
    ".grok/prompts/role-master.md"
    ".grok/prompts/role-orchestrator.md"
    ".grok/prompts/role-primary.md"
    ".grok/lib/grok-launch-common.sh"
    ".grok/prompts/packs/coder.pack"
    ".grok/prompts/packs/master.pack"
    ".grok/prompts/packs/orchestrator.pack"
    ".grok/prompts/packs/planner.pack"
    ".grok/prompts/packs/primary.pack"
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
    "run-antigravity-master"
    "run-antigravity-planner"
    ".grok/skills/prepare-local-pr/SKILL.md"
    ".grok/skills/master-merge/SKILL.md"
    ".grok/skills/rebase-on-master/SKILL.md"
    ".grok/skills/review/SKILL.md"
    ".grok/skills/check-upgrade/SKILL.md"
    ".grok/skills/validate-plans/SKILL.md"
    "generate_pr.sh"
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
    "sync-debug-keystores"
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
    "git-merge-drivers/ve-special-ours"
    "git-merge-drivers/ve-special-refuse"
    "install-merge-drivers.sh"
    "merge-branch-into-master.sh"
    "hooks/post-checkout"
    "install-ve-refresh-shell.sh"
    "ve-resolve-orch"
    "agent-landlock"
    "landlock.config"
    "landlock.config.example"
    "deploy-orchestration"
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
    "sync-debug-keystores"
    "project.config.example"
    ".gitattributes"
    "enable-full-orchestration.sh"
    "setup_agent.sh"
    "remove_worktree.sh"
    "deploy"
    "build_app"
    "sync_infrastructure.sh"
    "update-rules.sh"
    "ve-resolve-orch"
)

# 4. Push updates to all other worktrees
CURRENT_WT=$(git rev-parse --show-toplevel)
SOT_COMMON=$(git rev-parse --git-common-dir)
case "$SOT_COMMON" in
  /*) ;;
  *) SOT_COMMON="$SOURCE_DIR/$SOT_COMMON" ;;
esac
SOT_COMMON=$(cd "$SOT_COMMON" && pwd)

# Porcelain parse: path + prunable. Do not treat grok-worktrees, third_party
# src, prunable, or foreign git-common-dir as sync targets.
WT_PATHS=()
WT_PRUNABLE=()
_wt_path=""
_wt_prunable=0
_flush_wt() {
  if [ -n "$_wt_path" ]; then
    WT_PATHS+=("$_wt_path")
    WT_PRUNABLE+=("$_wt_prunable")
  fi
  _wt_path=""
  _wt_prunable=0
}
while IFS= read -r _line || [ -n "${_line:-}" ]; do
  case "$_line" in
    worktree\ *)
      _flush_wt
      _wt_path="${_line#worktree }"
      ;;
    prunable*)
      _wt_prunable=1
      ;;
    "")
      _flush_wt
      ;;
  esac
done < <(git worktree list --porcelain)
_flush_wt
unset _line _wt_path _wt_prunable

SKIP_SAFETY=0

# Prints skip reason to stdout and returns 0 if this worktree must not be synced.
worktree_skip_reason() {
  local wt="$1" prunable="$2"
  if [ "$prunable" = 1 ]; then
    echo "prunable"
    return 0
  fi
  case "$wt" in
    */grok-worktrees|*/grok-worktrees/*)
      echo "grok-worktrees pool"
      return 0
      ;;
    */third_party/*/src|*/third_party/*/src/*)
      echo "third_party src worktree"
      return 0
      ;;
  esac
  if [ ! -e "$wt/.git" ]; then
    echo "no .git"
    return 0
  fi
  if [ ! -f "$wt/.git" ] && [ ! -d "$wt/.git" ]; then
    echo ".git is not a file or directory"
    return 0
  fi
  local common
  common=$(git -C "$wt" rev-parse --git-common-dir 2>/dev/null) || {
    echo "cannot resolve git-common-dir"
    return 0
  }
  case "$common" in
    /*) ;;
    *) common="$wt/$common" ;;
  esac
  common=$(cd "$common" 2>/dev/null && pwd) || {
    echo "cannot canonicalize git-common-dir"
    return 0
  }
  if [ "$common" != "$SOT_COMMON" ]; then
    echo "git-common-dir $common != SoT $SOT_COMMON"
    return 0
  fi
  return 1
}

COMMIT_EXTRAS=(
  standard-plan-compliance-block.md
  get-builds-tag.sh
  run-grok
  run-grok-planner
  run-grok-master
  run-grok-coder
  run-grok-orchestrator
  run-antigravity
  run-antigravity-master
  run-antigravity-planner
)

if [ "$DRY_RUN" -eq 1 ]; then
    echo ">>> would seed orch_root=$SOURCE_DIR into $SOURCE_DIR/project.config"
else
    ve_seed_worktree_project_config "$SOURCE_DIR" "$SOURCE_DIR"
    echo ">>> seeded orch_root=$SOURCE_DIR into $SOURCE_DIR/project.config"
fi

_wt_i=0
while [ "$_wt_i" -lt "${#WT_PATHS[@]}" ]; do
    WT="${WT_PATHS[$_wt_i]}"
    WT_IS_PRUNABLE="${WT_PRUNABLE[$_wt_i]}"
    _wt_i=$((_wt_i + 1))
    if [ "$WT" == "$CURRENT_WT" ]; then
        continue
    fi

    if _skip=$(worktree_skip_reason "$WT" "$WT_IS_PRUNABLE"); then
        echo "ERROR: skip $WT — $_skip"
        SKIP_SAFETY=$((SKIP_SAFETY + 1))
        continue
    fi

    echo ">>> Syncing rules to worktree: $WT"
    if [ "$DRY_RUN" -eq 1 ]; then
        echo "    would seed orch_root=$SOURCE_DIR into $WT/project.config"
    else
        ve_seed_worktree_project_config "$WT" "$SOURCE_DIR"
        echo "    seeded orch_root=$SOURCE_DIR into $WT/project.config"
    fi

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
    if [ "$DRY_RUN" -eq 0 ] && [ -d "$WT" ]; then
        if ( cd "$WT" && ! git diff --staged --quiet 2>/dev/null ); then
            echo "  Stashing staged changes temporarily in $WT before sync..."
            if ( cd "$WT" && git stash push --staged --message "update-rules temp: preserve staged work before infra sync" --quiet ); then
                stashed=1
            else
                echo "ERROR: stash failed in $WT — skip copy and commit"
                SKIP_SAFETY=$((SKIP_SAFETY + 1))
                continue
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

    COPIED_N=0
    SKIPPED_N=0
    for FILE in "${COPY_LIST[@]}"; do
        if [ ! -f "$SOURCE_DIR/$FILE" ]; then
            continue
        fi
        decide_sync_action "$WT" "$FILE"
        if [ "$DECIDE_ACTION" = "skip" ]; then
            SKIPPED_N=$((SKIPPED_N + 1))
            if [ "$DECIDE_REASON" != "content identical" ] && [ "$DECIDE_REASON" != "content identical (blob)" ]; then
                echo "    SKIP $FILE — $DECIDE_REASON"
            fi
            continue
        fi
        echo "    COPY $FILE — $DECIDE_REASON"
        COPIED_N=$((COPIED_N + 1))
        if [ "$DRY_RUN" -eq 1 ]; then
            continue
        fi
        TARGET_FILE="$WT/$FILE"
        TARGET_DIR_PATH=$(dirname "$TARGET_FILE")
        mkdir -p "$TARGET_DIR_PATH"
        rm -f "$TARGET_FILE"
        cp -p "$SOURCE_DIR/$FILE" "$TARGET_FILE" 2>/dev/null || cp "$SOURCE_DIR/$FILE" "$TARGET_FILE"
        chown "$PRIMARY_USER:$CODE_GROUP" "$TARGET_FILE" 2>/dev/null || true
        if [ -x "$SOURCE_DIR/$FILE" ] || [[ "$FILE" == *.sh ]] || \
           [[ "$FILE" == deploy || "$FILE" == build_app || "$FILE" == gradlew ]] || \
           [[ "$FILE" == run-* ]] || \
           [[ "$FILE" == git-merge-drivers/* ]] || \
           [[ "$FILE" == agent-landlock || "$FILE" == todo-append || "$FILE" == todo-close || "$FILE" == ve-env ]]; then
          chmod a+x "$TARGET_FILE" 2>/dev/null || true
        fi
    done
    echo "    summary: copy=$COPIED_N skip=$SKIPPED_N dry_run=$DRY_RUN"

    if [ "$DRY_RUN" -eq 1 ]; then
        continue
    fi

    # Ensure management/orchestration scripts end up executable (right perms).
    for _exe in "$WT"/deploy "$WT"/build_app "$WT"/gradlew \
                "$WT"/ve-resolve-orch \
                "$WT"/agent-landlock "$WT"/todo-append "$WT"/todo-close "$WT"/ve-env \
                "$WT"/landlock-smoke-matrix "$WT"/landlock-write-probe \
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

    (
        cd "$WT" || exit 1

        ALLOW_COMMIT=("${COPY_LIST[@]}")
        for extra in "${COMMIT_EXTRAS[@]}"; do
          [ -e "$extra" ] && ALLOW_COMMIT+=("$extra")
        done

        git update-index --no-skip-worktree "${ALLOW_COMMIT[@]}" 2>/dev/null || true
        chmod +w "${ALLOW_COMMIT[@]}" 2>/dev/null || true

        add_failed=0
        for f in "${ALLOW_COMMIT[@]}"; do
          [ -e "$f" ] || continue
          if ! git add -f -- "$f"; then
            echo "ERROR: git add failed for $f in $WT"
            add_failed=1
          fi
        done
        if [ "$add_failed" -ne 0 ]; then
          echo "ERROR: skip commit in $WT after add failure"
          git reset >/dev/null 2>&1 || true
          exit 2
        fi

        extra_staged=0
        while IFS= read -r p; do
          [ -n "$p" ] || continue
          in_allow=0
          for a in "${ALLOW_COMMIT[@]}"; do
            if [ "$p" = "$a" ]; then
              in_allow=1
              break
            fi
          done
          if [ "$in_allow" -eq 0 ]; then
            echo "ERROR: extra staged path $p in $WT — refusing commit"
            extra_staged=1
          fi
        done < <(git diff --cached --name-only)

        if [ "$extra_staged" -ne 0 ]; then
          git reset >/dev/null 2>&1 || true
          exit 2
        fi

        staged_only=()
        while IFS= read -r p; do
          [ -n "$p" ] && staged_only+=("$p")
        done < <(git diff --cached --name-only)
        if [ "${#staged_only[@]}" -eq 0 ]; then
          echo "No changes needed for $WT."
        else
          echo "Changes detected in $WT, committing allowlist only..."
          if ! git commit --only -m "chore: Synchronize agent rules and infrastructure" -- "${staged_only[@]}"; then
            echo "ERROR: git commit --only failed in $WT"
            git reset >/dev/null 2>&1 || true
            exit 2
          fi
        fi

        if [ "$stashed" -eq 1 ]; then
            echo "  Popping stash in $WT to restore previous state..."
            if git stash pop --index --quiet; then
                :
            else
                echo "  WARNING: git stash pop failed in $WT. You may need to resolve conflicts manually."
                echo "  Status after failed pop:"
                git status --short | cat
            fi
        fi
    ) || {
        echo "ERROR: infra commit skipped or failed in $WT"
        SKIP_SAFETY=$((SKIP_SAFETY + 1))
        continue
    }

    # Re-assert executables after commit (git may not preserve all mode bits in WT)
    for _exe in "$WT"/deploy "$WT"/build_app "$WT"/gradlew \
                "$WT"/ve-resolve-orch \
                "$WT"/agent-landlock "$WT"/todo-append "$WT"/todo-close "$WT"/ve-env \
                "$WT"/landlock-smoke-matrix "$WT"/landlock-write-probe \
                "$WT"/install-merge-drivers.sh "$WT"/merge-branch-into-master.sh; do
      [ -f "$_exe" ] || continue
      chown "$PRIMARY_USER:$CODE_GROUP" "$_exe" 2>/dev/null || true
      chmod a+x "$_exe" 2>/dev/null || true
    done
    find "$WT" -maxdepth 1 -type f -name '*.sh' -exec chmod a+x {} + 2>/dev/null || true
    ve_ensure_tracked_exec_other_x "$WT"
    if [ -x "$WT/fix-perms" ]; then
        echo "  Ensuring perms on $WT via fix-perms..."
        if ! "$WT/fix-perms" "$WT"; then
            sudo "$WT/fix-perms" "$WT" || {
                echo "ERROR: fix-perms failed for $WT" >&2
                exit 1
            }
        fi
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

if [ "$DRY_RUN" -eq 0 ]; then
  # Install merge drivers from orchestration root as well
  if [ -x "$SOURCE_DIR/install-merge-drivers.sh" ]; then
    (cd "$SOURCE_DIR" && ./install-merge-drivers.sh) || true
  fi

  # 5. Promote Policies to User-tier (ensure they are active)
  USER_POLICY_DIR="$HOME/.gemini/policies"
  echo ">>> Promoting policies to User-tier: $USER_POLICY_DIR"
  mkdir -p "$USER_POLICY_DIR"
  cp "$SOURCE_DIR/.gemini/policies/plans.toml" "$USER_POLICY_DIR/vehicle_expenses_plans.toml"
  cp "$SOURCE_DIR/.gemini/policies/auto-saved.toml" "$USER_POLICY_DIR/vehicle_expenses_auto_saved.toml"
fi

echo "--- Rule Update Sync Complete ---"
if [ "$DRY_RUN" -eq 1 ]; then
  echo "Status: dry-run only (no files written, no commits)."
else
  echo "Status: Worktrees processed from $SOURCE_DIR (skipped worktree-ahead/dirty paths unless --force)."
fi
if [ "$SKIP_SAFETY" -ne 0 ]; then
  echo "ERROR: $SKIP_SAFETY worktree(s) skipped for safety (stash fail, extra staged, foreign/prunable/grok-worktrees/third_party src)."
  exit 1
fi
