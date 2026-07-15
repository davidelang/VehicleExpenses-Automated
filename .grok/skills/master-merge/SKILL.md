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
   - **`./merge-branch-into-master.sh <branch>`** (preferred; no temp branch for eng-log alone)
   - **ENGINEERING_LOG:** driver `ve-englog` + `append-to-engineering-log` → third-version result (master + branch tail). Never replace with branch file. No `chattr -a`.
   - **TODO.md / project-facts.md:** **always** special-file review against **branch delta** (and PR/commits), even if those paths did not change. Base = master. `todo-close` completed work; prune invalid facts. Drivers refuse naive text merge when both sides touch the path.
   - Finish specials → `./build_app`
5. Never set `works` tag. Inform user to run `./remove_worktree.sh` when done.

## Expectation
Independent review should ideally find **nothing** if coder used prepare-local-pr well.
