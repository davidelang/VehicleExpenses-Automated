# AGENTS.md — Grok Project Instructions Bootstrap

Grok scans the worktree git root for project instructions. This file (AGENTS.md) is the entry point for Grok CLI in this multi-agent VehicleExpenses-automated project.

## Immediate Reads (on every start / after /compact)
1. Read `./AGENT_CONTEXT.md` — your identity, current branch/role, sandbox location, and agent-specific notes.
2. Read `./GROK.md` — Grok CLI thin overlay (tool mappings, phase gating via SwitchMode/plan mode, etc.).
3. Read `./AGENT_MANDATES.md` — the **shared core** for *all* agent CLIs (bi-modal workflow, STOP & WAIT, forensic verification + build_app, 3-3-3 strikes, three git reset contexts with preflight, ICRS or raw pixel coordinates only, no deployment + shared hardware, per-branch tags, old plans directory rule, re-read after compaction, etc.).

**Do NOT re-read .grok/ config or system files on every turn** (they are already in context via the harness).

## Key File Disambiguation (Shared Inventory)
| File                  | Purpose                                      | Read by                  | Notes |
|-----------------------|----------------------------------------------|--------------------------|-------|
| AGENT_CONTEXT.md     | Per-agent state (ID, branch, role, geography) | All agents              | Created once per worktree from template |
| AGENT_MANDATES.md    | Shared CLI-neutral core mandates            | All agents (Grok, Gemini, Antigravity, ...) | Authoritative for bi-modal, reset, coords, forensic, etc. |
| GROK.md              | Grok-specific thin overlay                  | Grok CLI only           | Tool names (Read/Write/StrReplace/Shell/Task/SwitchMode), SwitchMode for plan mode |
| GEMINI.md            | Gemini-specific thin overlay                | Gemini CLI only         | (Legacy/parallel) |
| new_grok_agent_prompt| Fast-track launch report + STOP & WAIT      | Grok (via run-grok)     | Forces plan mode + report on first load |
| .grok/config.toml + hooks/ | Project permissions, PreToolUse hard stops | Grok harness (native)  | Checked-in; merges with ~/.grok/ ; plan-mode sandbox enforcement |
| dev-ai-interaction/  | Sandbox (plans, analysis scripts, logs)     | All (via absolute or ./ symlink) | Only writable area in plan mode for most work |
| docs/specs/          | Authoritative specs (ISOTROPIC_COORDINATE_SPEC.md is source of truth for coords) | Reference             | ICRS / raw pixel only |

## Plans Directory Rule (Historical Only)
`dev-ai-interaction/plans/` contains finished, abandoned, or in-progress work (usually by another agent). 
- Move completed plans into `dev-ai-interaction/plans/old/` upon finishing.
- **Do not** start new work from any plan in `plans/` (or `plans/old/`) unless the user has *explicitly* directed you to a specific plan file.

## Coordinates Policy (Project-Wide)
The only valid coordinate systems are **ICRS** (Isotropic Center-Relative Space — radial shortest-edge normalization) and **raw pixel integers**.
Normalized 0.0–1.0 (per-axis) is obsolete and must be corrected wherever it appears in docs, code comments (except known-good historical or internal math), or persistent storage.

## Git Reset Rules (Three Contexts — CRITICAL)
See AGENT_MANDATES.md for the exact three allowed contexts + mandatory preflight (verify the branch-scoped tag exists before `git reset --hard`).

Never use HEAD^, HEAD~, arbitrary SHAs, or other-branch tags.

## Next Steps After Reading
- If this is a fresh launch or post-compaction: immediately produce the Mandate Acknowledgment report from `new_grok_agent_prompt` (or equivalent in GROK.md) and enter plan mode / STOP & WAIT.
- For implementation: only after explicit user approval of a plan.
- Always use the absolute sandbox `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/` when writing research artifacts.

This project uses physical copies of the shared brain (delivered by `git worktree add` from master tip + hotfixed via `update-rules.sh` run from the orchestration root). 

Welcome. Report your role, branch, and current mandates understanding now.