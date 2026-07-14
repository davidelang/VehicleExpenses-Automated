---
name: prepare-local-pr
description: >
  Prepare a local multi-agent PR for this project (not GitHub). Pre-submit code
  review vs approved plan, history cleanup, backup tag, generate_pr.sh to
  dev-ai-interaction/PRs/. Use when asked to prepare PR, generate PR, cleanup
  history for merge, or /prepare-local-pr. Never use gh pr create, Graphite, or pr-babysit.
when-to-use: "prepare PR, generate PR, cleanup history, local PR, /prepare-local-pr"
---

# prepare-local-pr (Coder)

You prepare a **local** PR document for Master review. This repo does **not** use GitHub PR create/babysit workflows.

## Preconditions
- Feature branch (not `master` or `orchestration`).
- Implementation for the approved plan is done; `./build_app` has succeeded for the desired state.
- Confirm `pwd` once; use `./helper` only (no `cd … &&`).

## Steps
1. **Pre-submit code review** (you): diff vs the approved plan path(s). Fail if scope creep / silent improvements. List residual risks.
2. **History cleanup** on this branch: logical commits; create/update `backup-<branch>` if required by project cleanup scripts; never `git commit --amend` of published history without explicit user protocol.
3. Run **`./generate_pr.sh`** with plan paths → `dev-ai-interaction/PRs/PR-<branch>.md`. Include review summary and any TODO items that merge should **close**.
4. `./append-to-engineering-log` note: PR prepared, path to PR doc.
5. Stop and tell the user: ready for Master (`run-grok-master`) independent review + merge. Do not merge yourself.

## Forbidden
- `gh pr create`, Graphite `gt submit`, `/pr-babysit`, force-push without project rules.
- Deploy.
