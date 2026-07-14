# AGENTS.md — Project Instructions Bootstrap

This file (AGENTS.md) is the entry point for agent CLIs in this multi-agent VehicleExpenses-automated project.

## Immediate Reads (on every start / after /compact)
1. Read `./AGENT_CONTEXT.md` — identity, branch/role, sandbox. (If missing, use template / git status; orchestration root has its own AGENT_CONTEXT.md.)
2. Read CLI overlay: `./GROK.md` or `./GEMINI.md`.
3. Read `./AGENT_MANDATES.md` (shared core).
4. **If Master** (`run-grok-master` / master worktree): also read **`MASTER_AGENT_MANDATE.md`** fully.
5. Confirm **`pwd` once** — then never `cd … && ./helper` (breaks allow-lists → approval thrash).

**Do NOT re-read system configuration files on every turn.**

## Launchers (role → command)
| Launcher | OS user | Role |
|----------|---------|------|
| `./run-grok-orchestrator` | ai-orchestrator | Meta rules / brain (orchestration branch) |
| `./run-grok-master` | ai-coder | **Execute plan**; PR review/merge |
| `./run-grok-planner` | ai-planner | Long-lived planning; **owns new cycles** |
| `./run-grok-coder` | ai-coder | Implement in agent-N |
| `./run-grok` | dlang | Bare process-break session |

Skills: `/prepare-local-pr`, `/master-merge`. Disabled in project config: `pr-babysit`, `execute-plan`, `design`, `check-work`. `/code-review` only when user explicitly wants ambitious restructure (separate planned turn).

## Key File Disambiguation
| File | Purpose | Notes |
|------|---------|-------|
| AGENT_CONTEXT.md | Role/branch/geography | Per worktree |
| AGENT_MANDATES.md | Shared core | Bi-modal, reset, coords, TODO/eng-log, cwd |
| MASTER_AGENT_MANDATE.md | Master merge/execute | Masters only |
| GROK.md / GEMINI.md | CLI overlays | Thin |
| new_agent_prompt | Startup ack + STOP | All |
| standard-plan-compliance-block.md | Execution gates | **Cite by path; do not paste** |
| project-facts.md | Orientation map | Discovery candidates; merge prunes |
| TODO.md | Future backlog | todo-append / todo-close only |
| ENGINEERING_LOG.md | Current turn | append-to-engineering-log only |
| ve-env | Human shell umask/groups | `source ./ve-env` |
| .grok/config.toml + hooks/ | Permissions + skills.disabled | |
| dev-ai-interaction/ | Sandbox | Absolute path preferred |
| docs/specs/ | Specs | PERMISSIONS_MODEL, coords |

## Plans Directory Rule
`dev-ai-interaction/plans/` — current designated plan. Completed → `historical-plans/`. Do not start work from historical or non-designated files unless user names the exact path. Harness session plan.md is process log only. project-facts.md = orientation only (see AGENT_MANDATES).

## Worktree deploy / copy rule (blocks build_app if violated)
`./build_app` fails on **uncommitted tracked** changes. If you copy tracked files into another worktree (`cp`, hot patch, partial sync), **commit on that branch** (or use `./update-rules.sh`, which commits). Untracked/gitignored binaries (e.g. `ve-refresh-shell`) need no commit. See AGENT_MANDATES "Deploying / copying into another worktree".

## Coordinates Policy
ICRS or raw pixel integers only. Normalized 0.0–1.0 per-axis is obsolete.

## Git Reset Rules
See AGENT_MANDATES.md — three contexts + `./get-builds-tag.sh` preflight only.

## Next Steps After Reading
- Fresh launch: Mandate Acknowledgment from `new_agent_prompt`, then STOP & WAIT.
- Implementation only after magic approval naming `dev-ai-interaction/plans/<name>-plan.md`.
- Planning help = research + better plan file, not source changes/builds.
- Sandbox writes: `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/`.

Welcome. Report your role, branch, and mandates understanding now.
