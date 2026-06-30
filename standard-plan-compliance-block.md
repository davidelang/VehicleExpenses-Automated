## COMPLIANCE & EXECUTION GUARDRAILS (STANDARD BLOCK v2026-06-30 — DO NOT MODIFY THIS SECTION)

- **Scope:** Implement only observable changes in **Phased Execution** below. No scope creep.

- **Per-phase gates** (details: `AGENT_MANDATES.md` — Bi-Modal Workflow, Baseball Rule, Git Reset Rules):
  - **Start:** Re-read this plan + `project-facts.md` (hygiene); `./append-to-engineering-log` as first action.
  - **Each phase:** Phase-only edits → narrow forensic read/grep → `git add` (sources + `ENGINEERING_LOG.md`) → successful `./build_app` before the next phase.
  - **Granularity:** Coherent, independently verifiable phases (~3–8 typical). Finer steps only in a **revised plan after end of inning** (3 outs), using the End of Inning Report as input.
  - **Recovery:** Only reset to tag from last successful phase (`./get-builds-tag.sh` preflight). **3rd out:** write inning-end report to `implementation-failure-logs/` before any replan.

- **Hygiene:** `project-facts.md` = stable facts only; `ENGINEERING_LOG` via `./append-to-engineering-log` only; sandbox writes under `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/`.

- **Handoff:** After final build + verification, emit exactly: `**END OF EXECUTION TURN. Awaiting new directive or plan approval before any further source changes or investigation that leads to edits.**` then `results ready to test (new tag: ...)`. Master runs Compliance Checker (intent match primary).

- **Standing rules:** `AGENT_MANDATES.md`, `MULTI_AGENT_USER_INSTRUCTIONS.md`, `new_grok_agent_prompt` (re-read on launch, compaction, new cycle).