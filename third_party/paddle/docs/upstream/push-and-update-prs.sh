#!/usr/bin/env bash
# Push restacked Paddle-Lite PR branches and refresh GitHub PR titles/bodies.
#
# Run as YOU (not the agent). Requires: git SSH access to davidelang/Paddle-Lite,
# and `gh` authenticated with rights to edit PRs on PaddlePaddle/Paddle-Lite
# (or at least on your fork + permission to update the open PRs).
#
# Usage (from anywhere):
#   /path/to/VehicleExpenses/.../third_party/paddle/docs/upstream/push-and-update-prs.sh
#   DRY_RUN=1 .../push-and-update-prs.sh          # print actions only
#   PADDLE_REPO=~/git/paddle .../push-and-update-prs.sh
#
# What it does:
#   1. Verifies local restack branch tips
#   2. force-with-lease push restack → existing PR head branch names on origin
#   3. Updates PR #10712–#10714 title + body from docs/upstream/PR_*.md
#   4. Tries to set stacked bases (10713→cleanup head, 10714→x86 head); if GitHub
#      rejects (base must be on upstream), leaves base as develop and prints advice
#
set -euo pipefail

DRY_RUN="${DRY_RUN:-0}"
PADDLE_REPO="${PADDLE_REPO:-$HOME/git/paddle}"
UPSTREAM_REPO="${UPSTREAM_REPO:-PaddlePaddle/Paddle-Lite}"
# Durable message dir: this script lives in docs/upstream/
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MSG_DIR="${MSG_DIR:-$SCRIPT_DIR}"

ORIGIN_NAME="${ORIGIN_NAME:-origin}"

# local_restack_branch  remote_pr_head  pr_number  title  body_file
# shellcheck disable=SC2034
PLAN=(
  "pr-upstream-cleanup-restack|pr-upstream-cleanup|10712|fix: General build system robustness and bug fixes|PR_UPSTREAM_CLEANUP.md"
  "pr-x86-android-mobile-gap-restack|pr-x86-android-mobile-gap|10713|feat: Enable X86 Android (mobile gap) builds for emulators|PR_X86_ANDROID.md"
  "pr-calib-safe-uint8-dequant-restack|pr-calib-safe-uint8-dequant|10714|fix(arm): safe int8/uint8 calib dequant without input overread|PR_CALIB_SAFE_UINT8.md"
)

# After push: preferred PR base branch names on the *head* repo (fork).
# 10712 stays on develop; 10713 base=pr-upstream-cleanup; 10714 base=pr-x86-android-mobile-gap
STACK_BASES=(
  "10712|develop"
  "10713|pr-upstream-cleanup"
  "10714|pr-x86-android-mobile-gap"
)

run() {
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "DRY_RUN: $*"
  else
    echo "+ $*"
    "$@"
  fi
}

die() { echo "ERROR: $*" >&2; exit 1; }

need_cmd() { command -v "$1" >/dev/null 2>&1 || die "missing command: $1"; }

need_cmd git
need_cmd gh
need_cmd jq

[[ -d "$PADDLE_REPO/.git" ]] || die "PADDLE_REPO is not a git repo: $PADDLE_REPO"
[[ -d "$MSG_DIR" ]] || die "MSG_DIR missing: $MSG_DIR"

echo "=== config ==="
echo "PADDLE_REPO=$PADDLE_REPO"
echo "MSG_DIR=$MSG_DIR"
echo "UPSTREAM_REPO=$UPSTREAM_REPO"
echo "DRY_RUN=$DRY_RUN"
echo "origin=$(git -C "$PADDLE_REPO" remote get-url "$ORIGIN_NAME" 2>/dev/null || echo MISSING)"

cd "$PADDLE_REPO"

# Prefer SSH origin for push (user machine)
origin_url="$(git remote get-url "$ORIGIN_NAME")"
if [[ "$origin_url" == https://github.com/* ]]; then
  echo "NOTE: origin is HTTPS ($origin_url)."
  echo "      Push may prompt for credentials. SSH is fine if you set:"
  echo "      git remote set-url origin git@github.com:davidelang/Paddle-Lite.git"
fi

echo ""
echo "=== verify local restack tips ==="
for row in "${PLAN[@]}"; do
  IFS='|' read -r local_br remote_br prn title bodyf <<<"$row"
  git rev-parse --verify "$local_br" >/dev/null 2>&1 \
    || die "missing local branch $local_br — check out restack in $PADDLE_REPO first"
  tip="$(git rev-parse --short "$local_br")"
  msg_tail="$(git log -1 --format=%B "$local_br" | tr -d '\r' | grep -c 'test=develop' || true)"
  echo "  $local_br @ $tip  (test=develop lines: $msg_tail) → $remote_br  PR#$prn"
  [[ "$msg_tail" -ge 1 ]] || echo "  WARNING: $local_br tip commit lacks test=develop"
  [[ -f "$MSG_DIR/$bodyf" ]] || die "missing body file $MSG_DIR/$bodyf"
done

# Strip HTML comments from body for a cleaner GitHub description (optional)
body_tmp_dir="$(mktemp -d)"
cleanup() { rm -rf "$body_tmp_dir"; }
trap cleanup EXIT

prepare_body() {
  local src="$1" dst="$2"
  # Drop HTML comment lines only; keep markdown
  sed '/^<!--/d' "$src" >"$dst"
}

echo ""
echo "=== push heads (force-with-lease restack → PR branch names) ==="
for row in "${PLAN[@]}"; do
  IFS='|' read -r local_br remote_br prn title bodyf <<<"$row"
  # force-with-lease: refuse if remote-tracking tip of remote_br advanced under us
  run git push --force-with-lease "$ORIGIN_NAME" "${local_br}:${remote_br}"
done

echo ""
echo "=== update PR title + body via gh REST API ==="
# Prefer REST: `gh pr edit` (GraphQL) can fail on some repos with a Projects classic deprecation error.
for row in "${PLAN[@]}"; do
  IFS='|' read -r local_br remote_br prn title bodyf <<<"$row"
  body_out="$body_tmp_dir/${prn}.md"
  prepare_body "$MSG_DIR/$bodyf" "$body_out"
  echo "--- PR #$prn ---"
  echo "title: $title"
  echo "body:  $MSG_DIR/$bodyf → $body_out ($(wc -l <"$body_out") lines)"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "DRY_RUN: gh api -X PATCH repos/$UPSTREAM_REPO/pulls/$prn (title+body)"
  else
    payload="$(jq -n --arg title "$title" --rawfile body "$body_out" '{title:$title, body:$body}')"
    echo "$payload" | gh api -X PATCH "repos/${UPSTREAM_REPO}/pulls/${prn}" --input - \
      --jq '"updated #\(.number) \(.title) \(.html_url)"'
  fi
done

echo ""
echo "=== try stacked bases (best-effort) ==="
# Cross-fork PRs usually require base to live on UPSTREAM_REPO.
# If base is only on the fork, GitHub may reject — we report and continue.
for row in "${STACK_BASES[@]}"; do
  IFS='|' read -r prn base <<<"$row"
  echo -n "PR #$prn base → $base … "
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "DRY_RUN"
    continue
  fi
  if gh pr edit "$prn" --repo "$UPSTREAM_REPO" --base "$base" 2>"$body_tmp_dir/base.err"; then
    echo "OK"
  else
    echo "SKIPPED ($(tr '\n' ' ' <"$body_tmp_dir/base.err" | head -c 200))"
    if [[ "$base" != "develop" ]]; then
      echo "  Stacked base may need the UI: open PR, Edit, set base to your fork branch"
      echo "  if GitHub offers it; otherwise leave base=develop and merge in order."
    fi
  fi
done

echo ""
echo "=== final PR status ==="
if [[ "$DRY_RUN" != "1" ]]; then
  for prn in 10712 10713 10714; do
    gh pr view "$prn" --repo "$UPSTREAM_REPO" \
      --json number,title,baseRefName,headRefName,url,commits \
      --jq '"#\(.number) base=\(.baseRefName) head=\(.headRefName) commits=\(.commits|length)\n  \(.title)\n  \(.url)"'
  done
fi

echo ""
echo "Done."
echo "Next: confirm each PR shows checks beyond CLA (commit must contain test=develop),"
echo "      and ping code owners if still idle: @zhupengyang @hong19860320"
echo ""
echo "Bodies sourced from:"
echo "  $MSG_DIR/PR_UPSTREAM_CLEANUP.md"
echo "  $MSG_DIR/PR_X86_ANDROID.md"
echo "  $MSG_DIR/PR_CALIB_SAFE_UINT8.md"
