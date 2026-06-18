## COMPLIANCE & EXECUTION GUARDRAILS (STANDARD BLOCK v2026-06-14 — DO NOT MODIFY THIS SECTION)

- This plan is the authoritative scope for the turn. Implement *precisely and only* the observable changes described in the "Phased Small-Step Execution" section below. No additional refactors, cleanups, "improvements," or scope creep.

- Execution start (after explicit user magic approval that names *this exact sandbox plan path*): re-read this plan + current-state.md (perform hygiene prune first). Update TODO.md as the very first action.

- Use narrow forensic `read_file` (offset/limit focused on the exact change site) + targeted grep verification before and after every edit.

- **Mandatory ultra-micro phased execution with per-phase success gates**: The plan's "Phased Small-Step Execution" section must decompose the work into as many explicitly named, ultra-small phases as needed to keep each phase the smallest practical observable unit of work that can be forensically verified in isolation and completed with a successful `./build_app`. There is no target number of phases — the goal is maximum safe granularity (more smaller phases is better than fewer larger ones, as long as each is a meaningful, independently verifiable and buildable step). Each phase performs the smallest observable edit, followed **immediately** by narrow forensic `read_file` (offset/limit) + targeted grep before/after + `git add` (changed tracked sources + TODO.md) + a **confirmed successful `./build_app`** (new branch-scoped builds tag recorded and noted in current-state.md). No edits for the next phase may begin until the current phase's `./build_app` has succeeded. On any failure, trouble, or partial reset, only the tag from the most recent successful phase's `./build_app` (obtained via the mandatory `./get-builds-tag.sh` preflight) may be used.

- Before each `./build_app`: `git add` the changed tracked source file(s) + `TODO.md`. (current-state.md is updated as required by the plan but is deliberately untracked/gitignored and must never be `git add`ed.)

- current-state.md updates: 1-2 concise facts/pointers per step only (after pruning older completed items to a rolled summary line).

- All sandbox writes use the absolute path `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/`. Never use `..`.

- Harness `~/.grok/sessions/.../plan.md` receives *only* a minimal process log entry that references this exact sandbox plan path (roll prior bulk to historical-plans/ if needed).

- At the very end, after the final successful build + post-forensic verification: output the exact marker "**END OF EXECUTION TURN. Awaiting new directive or plan approval before any further source changes or investigation that leads to edits.**" followed by "results ready to test" (include the new tag).

- An independent Compliance Checker sub-agent will *always* be run by the master after you emit the marker. The checker's *primary* job is to analyze whether the actual code changes solve the problem stated in the plan and deliver the required observable results (intent/functional match). Process discipline is secondary. Only on a PASS for the primary criterion will associated implementation-failure-logs entries for this plan be cleaned up. Reference this in your plan's verification section.

- Full standing rules (bi-modal workflow, handoff boundaries, 3-3-3 strikes, allowed reset contexts only with preflight via `./get-builds-tag.sh`, no deployment, ICRS or raw pixel coordinates, always-run independent checker focused on whether code changes solve the plan's stated problem, planner startup failure detection, cleanup on checker success, etc.): see the live `AGENT_MANDATES.md`, `MULTI_AGENT_USER_INSTRUCTIONS.md`, and `new_grok_agent_prompt` (re-read on every fresh launch, after compaction, and at start of new cycles).
