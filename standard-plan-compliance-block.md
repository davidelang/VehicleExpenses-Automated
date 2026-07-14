## COMPLIANCE & EXECUTION GUARDRAILS (STANDARD BLOCK v2026-07-13 — REFERENCE ONLY; DO NOT PASTE INTO PLANS)

Plans must **cite this file by path** (one line). Do **not** paste this block into plan documents.

- **Scope:** Implement only observable changes in the plan's **Phased Execution**. No scope creep.

- **Per-phase gates** (details: `AGENT_MANDATES.md`):
  - **Start:** Re-read approved plan + `project-facts.md` (hygiene); first action `./append-to-engineering-log` (never ritual TODO). **No `cd … &&` on helpers** — cwd fixed after startup.
  - **Each phase:** Phase-only edits → forensic read/grep → `git add` (sources + `ENGINEERING_LOG.md` if appended) → successful `./build_app` before the next phase.
  - **Granularity:** Coherent independently verifiable phases (~3–8 typical). Finer only after end of inning (3 outs) via End of Inning Report.
  - **Recovery:** Reset only via `./get-builds-tag.sh` preflight. **3rd out:** inning-end report before replan.

- **Hygiene:** `project-facts.md` = orientation facts (candidates when discovery was needed); `TODO.md` future-only via `todo-append`/`todo-close`; `ENGINEERING_LOG` via `./append-to-engineering-log` only; sandbox `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/`.

- **Handoff:** After final build + verification, emit exactly: `**END OF EXECUTION TURN. Awaiting new directive or plan approval before any further source changes or investigation that leads to edits.**` then `results ready to test (new tag: ...)`. Master runs Compliance Checker (intent match primary).

- **Standing rules:** `AGENT_MANDATES.md`, `MULTI_AGENT_USER_INSTRUCTIONS.md` (re-read on launch, compaction, new cycle).
