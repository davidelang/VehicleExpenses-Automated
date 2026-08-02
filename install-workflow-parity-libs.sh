#!/usr/bin/env bash
# install-workflow-parity-libs.sh — run as dlang
#
# Bootstrap W3 from approved plan finish-lib-ve-workflow-parity-…:
# copy VE workflow tools onto remotetable + extractmail (orch + master).
# After this, libs use the same prepare-local-pr / master-merge / generate_pr flow.
#
# Usage:
#   bash install-workflow-parity-libs.sh
#   bash install-workflow-parity-libs.sh --push
set -euo pipefail

[[ "$(id -un)" == "dlang" || "${ALLOW_NON_DLANG:-0}" == "1" ]] \
  || { echo "run as dlang" >&2; exit 1; }

VE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GIT_HOME="${GIT_HOME:-$HOME/git}"
DO_PUSH=0
[[ "${1:-}" == "--push" ]] && DO_PUSH=1

copy_into() {
  local dest="$1"
  echo "==> $dest"
  mkdir -p "$dest/.grok/skills" "$dest/.grok/lib" "$dest/.grok/prompts/packs" \
           "$dest/sandbox/PRs" "$dest/sandbox/plans" "$dest/sandbox/historical-plans" \
           "$dest/sandbox/research"

  cp -a "$VE_ROOT/.grok/skills/prepare-local-pr" "$dest/.grok/skills/"
  cp -a "$VE_ROOT/.grok/skills/master-merge" "$dest/.grok/skills/"
  cp -a "$VE_ROOT/generate_pr.sh" "$dest/generate_pr.sh"
  chmod 775 "$dest/generate_pr.sh"

  # Keep lib pack launchers; refresh common from VE (sandbox_path alias)
  cp -a "$VE_ROOT/.grok/lib/grok-launch-common.sh" "$dest/.grok/lib/"
  # Packs from VE (canonical after cutover)
  cp -a "$VE_ROOT/.grok/prompts/packs/." "$dest/.grok/prompts/packs/"

  if [[ -f "$VE_ROOT/dev-ai-interaction/audit_merge.py" ]]; then
    cp -a "$VE_ROOT/dev-ai-interaction/audit_merge.py" "$dest/sandbox/audit_merge.py"
  fi

  # Prefer full VE merge-branch if lib slim is too thin (keep lib name)
  if [[ -f "$VE_ROOT/merge-branch-into-master.sh" ]]; then
    # Port: use generic script but allow missing build_app — lib already has slim;
    # only overwrite if VE script is substantially better. For now copy VE and
    # patch build_app requirement via comment — lib master runs without Android.
    cp -a "$VE_ROOT/merge-branch-into-master.sh" "$dest/merge-branch-into-master.sh"
    chmod 775 "$dest/merge-branch-into-master.sh"
  fi

  # MASTER mandate: sync from VE then light path note is already sandbox-aware
  if [[ -f "$VE_ROOT/MASTER_AGENT_MANDATE.md" ]]; then
    # Keep lib-sized mandate if much shorter and product-focused; still ensure
    # PR path language. Prefer VE mandate for full protocol on libs.
    cp -a "$VE_ROOT/MASTER_AGENT_MANDATE.md" "$dest/MASTER_AGENT_MANDATE.md"
  fi

  # project.config.example: ensure sandbox_dir=sandbox
  if [[ -f "$dest/project.config.example" ]]; then
    if ! grep -q 'sandbox_dir' "$dest/project.config.example" 2>/dev/null; then
      printf '\n# sandbox_dir=sandbox\n' >>"$dest/project.config.example"
    fi
  fi

  # AGENTS one-liner if missing pack note
  if [[ -f "$dest/AGENTS.md" ]] && ! grep -q 'prepare-local-pr' "$dest/AGENTS.md"; then
    printf '\n**Workflows:** same as VehicleExpenses — pack launchers, local PR (`./generate_pr.sh` → `sandbox/PRs/`), skills `prepare-local-pr` / `master-merge`.\n' >>"$dest/AGENTS.md"
  fi
}

commit_tree() {
  local dir="$1" msg="$2"
  (
    cd "$dir"
    git add -A -- \
      .grok/skills .grok/lib/grok-launch-common.sh .grok/prompts/packs \
      generate_pr.sh merge-branch-into-master.sh MASTER_AGENT_MANDATE.md \
      sandbox/audit_merge.py project.config.example AGENTS.md \
      2>/dev/null || true
    # do not add project.config
    git reset HEAD -- project.config 2>/dev/null || true
    if git diff --cached --quiet 2>/dev/null; then
      echo "  (no commit needed)"
      return 0
    fi
    git commit -m "$msg"
    echo "  committed $(git rev-parse --short HEAD)"
    if [[ "$DO_PUSH" -eq 1 ]]; then
      git push
      echo "  pushed"
    fi
  )
}

MSG='workflow: prepare-local-pr / master-merge / generate_pr parity with VE

Same local PR and master-merge process as VehicleExpenses; sandbox_dir=sandbox.
Pack launchers refreshed; audit_merge under sandbox/.'

for host in remotetable extractmail; do
  root="$GIT_HOME/$host"
  [[ -d "$root" ]] || { echo "missing $root" >&2; exit 1; }
  copy_into "$root"
  commit_tree "$root" "$MSG"
  if [[ -d "$root/master" ]]; then
    copy_into "$root/master"
    commit_tree "$root/master" "$MSG"
  fi
done

echo ""
echo "Done. Verify:"
echo "  ls $GIT_HOME/remotetable/.grok/skills/"
echo "  ls $GIT_HOME/extractmail/sandbox/audit_merge.py"
echo "  # optional: cd lib worktree && ./generate_pr.sh sandbox/plans/<plan>.md"
