---
name: master-merge
description: >
  Master PR review and special-file merge per MASTER_AGENT_MANDATE.md. Independent
  review of local PR markdown, audit_merge, eng-log/TODO/project-facts handling,
  build_app. Use when reviewing or merging a feature branch / PR in master worktree.
when-to-use: "review PR, merge branch, master merge, integrate branch, /master-merge"
---

# master-merge (Master)

Follow **`MASTER_AGENT_MANDATE.md` §2** in full. Read that file first.

## Typical triggers
- Review PR / review branch (forensic only unless user says merge)
- Merge / integrate branch (full special-file protocol + `./build_app`)

## Steps (summary — mandate is authoritative)
1. Read `dev-ai-interaction/PRs/PR-<branch>.md`.
2. `git log master..<branch>` and `git diff master..<branch>` vs plans.
3. Reject unauthorized / plan-violating changes.
4. On merge:
   - `python3 dev-ai-interaction/audit_merge.py <branch>`
   - **`./install-merge-drivers.sh`** (sets `merge.autostash=false`, registers drivers)
   - **`./merge-branch-into-master.sh <branch>`** — FF index path when possible; else `git merge --no-autostash`; else index-first for +a eng-log; **`restore_special`** + **`verify_index_blob`** for TODO/facts
   - **ENGINEERING_LOG:** `ve-englog` + `append-to-engineering-log` → third version. No `chattr -a`.
   - **TODO / project-facts:** `ve-special-ours` + `restore_special` → master base; then **`todo-close`** / **`todo-append`** and facts prune vs branch delta (always, even if paths unchanged).
   - **POST-MERGE GATE (before `./build_app`):**
     ```bash
     git diff --cached --name-only
     # App merge: must list feature paths (e.g. *.kt), not ONLY ENGINEERING_LOG.md
     ```
     If only eng-log is staged while the branch changed app code → **FAILED**. Do not `build_app`.
     Retry: `git reset HEAD` (unstage), then re-run the merge script (or fix script on **orchestration** + `update-rules`).
   - `./build_app` to commit merge **only after** the staged set looks complete.
5. After successful merge, if the PR/plan is fully landed, ensure designated plan(s) are under `dev-ai-interaction/historical-plans/` (move from `plans/` if still active). Do **not** auto-run `./update-rules.sh` unless the human requests a brain sweep (can interfere with active agents).
6. Never set `works` tag. Inform user to run `./remove_worktree.sh` when done.

## Failed merge recovery
See `docs/reference/ORCHESTRATION_MERGE_INFRA_SYNC.md` § "Failed merge recovery",  
`docs/reference/MERGE_POSTMORTEM_IMPROVE_PUMP_CLASSIFICATION.md`, and `MASTER_AGENT_MANDATE.md` §2.

Do **not** promote one-off `reset-master-pre-merge.sh` as normal flow.  
Do **not** leave `merge-branch-into-master.sh` fixes only on master — orchestration is SoT, then `./update-rules.sh`.

## Expectation
Independent review should ideally find **nothing** if coder used prepare-local-pr well.
