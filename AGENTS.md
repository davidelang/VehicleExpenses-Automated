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
| dev-ai-interaction/  | Sandbox (including plans/ for current active plans, analysis scripts, logs)     | All (via absolute or ./ symlink) | Only writable area in plan mode for most work |
| docs/specs/          | Authoritative specs (ISOTROPIC_COORDINATE_SPEC.md is source of truth for coords) | Reference             | ICRS / raw pixel only |

## Plans Directory Rule (Historical Only)
`dev-ai-interaction/plans/` contains current-turn active plans (the one explicitly designated by the user for this turn) as well as the historical-plans/ sibling for finished/abandoned work (usually by another agent). 
- In every planning phase the first concrete deliverable **must** be a fresh task plan written to a new file directly under `dev-ai-interaction/plans/` (standard structure; see the "Sandbox Plan File as the Primary Approved Artifact" subsection in AGENT_MANDATES.md and the explicit user magic/forbidden phrases + rituals in the tracked `MULTI_AGENT_USER_INSTRUCTIONS.md`).
- "Being helpful," "proactive," or "efficient" during planning means research, idea suggestion, and plan improvement — **not** source changes or builds.
- In addition, each worktree maintains its own **local untracked per-branch state file** (`current-state.md` or `.agent-state/current-state.md` in the worktree root; gitignored). On cycle start the agent must read the local current-state.md (this worktree) first for cheap continuity, in addition to the user-designated sandbox plan file.
- The harness `~/.grok/sessions/.../plan.md` is the process log only (short entries referencing the sandbox plan path; roll old bulk to historical-plans/ then minimal prepend — prepending to, not even superseding).
- Completed plans are moved to `dev-ai-interaction/historical-plans/`.
- **Do not** start new work from any plan in `historical-plans/` (or any old/ subdirs) or from a non-designated file in `plans/` unless the user has *explicitly* designated the single current plan file (in dev-ai-interaction/plans/) for *this* turn by full path.

## Coordinates Policy (Project-Wide)
The only valid coordinate systems are **ICRS** (Isotropic Center-Relative Space — radial shortest-edge normalization) and **raw pixel integers**.
Normalized 0.0–1.0 (per-axis) is obsolete and must be corrected wherever it appears in docs, code comments (except known-good historical or internal math), or persistent storage.

## Git Reset Rules (Three Contexts — CRITICAL)
See AGENT_MANDATES.md for the exact three allowed contexts + mandatory preflight (verify the branch-scoped tag exists before `git reset --hard`).

Never use HEAD^, HEAD~, arbitrary SHAs, or other-branch tags.

## Next Steps After Reading
- If this is a fresh launch or post-compaction: immediately produce the Mandate Acknowledgment report from `new_grok_agent_prompt` (or equivalent in GROK.md) and enter plan mode / STOP & WAIT.
- For implementation: only after explicit user approval of a plan. Approval must reference the exact `dev-ai-interaction/plans/<name>-plan.md` path (see MULTI_AGENT_USER_INSTRUCTIONS.md for the required magic phrasing). The approved plan is always the designated sandbox file, not the harness session plan.md.
- In planning, "being helpful/proactive/efficient" means research, suggesting ideas, and making the written plan document (in dev-ai-interaction/plans/) better — it does **not** mean making source changes or running builds. Do not call exit_plan_mode until the user has had full discussion on the written plan file and signals it is ready for presentation. Rejection or continued feedback = "revise the plan document", not "abandon". For complex work, prefer the sub-agent pattern: spawn a narrow Planning Sub-agent for research + plan doc iteration (it must not call exit_plan_mode), then an Execution Sub-agent after user approval of the written file. The main agent orchestrates and reviews. Create the fresh sandbox plan file as the primary artifact under `dev-ai-interaction/plans/` (see updated Plans Directory Rule + AGENT_MANDATES "Sandbox Plan File..." subsection). Read the local `current-state.md` in the worktree root first for per-branch continuity. Use relaunch via run-grok after handoff for the cleanest new cycle, or the short gate file written by the agent.
- Always use the absolute sandbox `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/` when writing research artifacts.

This project uses physical copies of the shared brain (delivered by `git worktree add` from master tip + hotfixed via `update-rules.sh` run from the orchestration root). 

Welcome. Report your role, branch, and current mandates understanding now.