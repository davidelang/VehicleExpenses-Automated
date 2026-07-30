# Contributing

Pull requests welcome!

- **Build / environment setup (plain clone + multi-worktree):** [docs/ENVIRONMENT_SETUP.md](docs/ENVIRONMENT_SETUP.md)
- **Architecture & permissions:** [docs/specs/ARCHITECTURE.md](docs/specs/ARCHITECTURE.md), [docs/specs/PERMISSIONS_MODEL.md](docs/specs/PERMISSIONS_MODEL.md)
- **Illustrated user manual (HTML for browsers, Markdown is edit source):** [docs/reference/USER_MANUAL_BUILD.md](docs/reference/USER_MANUAL_BUILD.md) — after editing `docs/user-manual.md`, run `./scripts/render-user-manual.sh` and commit HTML + assets

## Agent Collaboration (Grok, Gemini CLI, Antigravity)

This project uses a multi-agent orchestration setup with physical copies of the shared brain.

**Primary references for all agents (read these first):**
- `AGENT_CONTEXT.md` (in the worktree root) — your identity, branch, role, sandbox.
- `AGENT_MANDATES.md` — shared core mandates for *all* CLIs (bi-modal workflow with hard plan-mode barrier + STOP & WAIT, forensic audit + `./build_app` validation after every edit, 3-3-3 strike rule, three approved `git reset` contexts with preflight, ICRS or raw pixel coordinates **only**, absolute no-deployment + shared hardware, per-branch tags, old plans directory rule, re-read after compaction, etc.).
- **`docs/reference/UI_COMPATIBILITY.md`** — **mandatory** Compose UI compatibility (units/labels, fontScale, icons, Vico compose-only, trip≠fill inventory, photo bandwidth). Cite by path in plans; do not paste. Mandates section points here.
- Agent-specific overlay: `GROK.md` (for Grok CLI) or `GEMINI.md` (for Gemini CLI).

**Key modern rules (replacing all prior web-UI / cut-paste / sidebar instructions):**
- Use the CLI launchers from the worktree root: `../run-grok`, `../run-gemini`, or the Antigravity equivalent.
- Planning/research: enter plan mode (or equivalent hard barrier). No edits to tracked app files. Sandbox writes only inside `dev-ai-interaction/`.
- After any plan proposal: STOP & WAIT for explicit user approval before execution.
- Execution: implement *only* the approved plan. First action: update `TODO.md`. After every modification: forensic `read_file` on the changed lines + `./build_app` success. 
- Git reset: only the three approved contexts (uncommitted HEAD; `<branch>/builds` or `builds` for recovery with preflight tag check). Never HEAD^ / arbitrary SHAs.
- No deployment: never run `./deploy`, `gradlew installDebug`, or `adb install` (shared device; user performs deploys).
- Coordinates: ICRS (Isotropic Center-Relative Space) or raw pixel only. Normalized 0.0–1.0 is obsolete.
- `dev-ai-interaction/plans/`: historical only. Move completed plans to `old/`. Do not dive into plans/ for new work unless explicitly told.
- After compaction: immediately re-read AGENT_CONTEXT.md + your overlay + AGENT_MANDATES.md + active plan.

See the full files listed above for complete details. The orchestration root (on the `orchestration` branch) is the development and `update-rules.sh` push source. Master branch tip must be correct for new `setup_agent` worktrees.

(Obsolete web-UI Grok collaboration instructions removed as part of Grok CLI parallel setup and doc reconciliation.)
