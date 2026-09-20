---
name: validate-plans
description: >
  Review sandbox plans this Host+Worktree wrote that are not yet validated:
  pending vs landed, Aim match (not only Critical Files), report gaps, write
  new DRAFT gap plans if needed, archive validated files so they are not
  reviewed again. Use when the user says validate-plans, /validate-plans,
  archive validated plans, or check whether my plans were implemented.
when-to-use: "validate-plans, /validate-plans, archive validated plans, did my plans land, plan intent review"
user-invocable: true
---

# validate-plans

Review **this session’s Host+Worktree** sandbox plans still in `$SANDBOX/plans/`.
Planning-only (sandbox writes). Do **not** implement app source. Do **not** spawn.

Cite, do not paste: `AGENT_CONTEXT.md`, `AGENT_MANDATES.md` §2, `dedicated-planner.md`.

## Roles

- **Planner** and **orchestrator:** may run this skill.
- **Coder** and **master:** **refuse** unless the human explicitly says to run
  `/validate-plans` anyway.

## Identity

- **Host** = `git rev-parse --show-toplevel`
- **Worktree** = `AGENT_CONTEXT.md` **Agent ID**

Ignore a plan file unless both `Host:` and `Worktree:` in its header match.
Ignore `$SANDBOX/historical-plans/`.
Ignore basenames listed in `$SANDBOX/validate-plans-ignore/<Worktree>.txt`
(create the dir/file if needed; one basename per line). That list is **only
for this Worktree** — do not stamp a global skip on the plan file.

## Unstamped files

Plans in `$SANDBOX/plans/` missing `Host:` or `Worktree:`: **list in chat**.
Ask which to validate this turn, which to append to **this** ignore file,
which to stamp as this Host+Worktree (**only** if the human says it is theirs).
Do not guess.

## No cap

Process every matching file still in `plans/`.

## Classify

| Status | Action |
|--------|--------|
| DRAFT / APPROVED, not landed | Report **pending**. No gap plan unless the human asks. |
| BLOCKED / SUPERSEDED | Report. Archive only if the human confirms obsolete. |
| CODE LANDED (or git/eng-log shows land) and **Aim** + contract OK | Add `Validated: YYYY-MM-DD Worktree=…` and `mv` to `$SANDBOX/historical-plans/`. |
| Landed but gaps vs **Aim** (chat context may add; Aim is the floor) | Report + write new DRAFT gap plan(s) with this Host+Worktree. One plan if the delta is small; split if the land went badly wrong. Do not archive the source until the human accepts the gap plan(s) **or** says archive anyway. |

Do **not** move plans to `historical-plans/` at CODE LANDED. This skill archives.

## Forbidden

- App / tracked non-sandbox edits
- Guessing another Host or Worktree
- Treating recaps or compact summaries as land proof
- `resume_from` of execute children

## Output (chat)

Pending list; validated+archived list; ignore-list additions; gap plan path(s).
