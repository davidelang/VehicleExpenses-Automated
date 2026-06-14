## COMPLIANCE & EXECUTION GUARDRAILS (STANDARD BLOCK v2026-06-14 — DO NOT MODIFY THIS SECTION)

- This plan is the authoritative scope for the turn. Implement *precisely and only* the observable changes described in the "Phased Small-Step Execution" section below. No additional refactors, cleanups, "improvements," or scope creep.

- Execution start (after explicit user magic approval that names *this exact sandbox plan path*): re-read this plan + current-state.md (perform hygiene prune first). Update TODO.md as the very first action.

- Use narrow forensic `read_file` (offset/limit focused on the exact change site) + targeted grep verification before and after every edit.

- Before each `./build_app`: `git add` the changed source file(s) + `current-state.md` + `TODO.md`.

- current-state.md updates: 1-2 concise facts/pointers per step only (after pruning older completed items to a rolled summary line).

- All sandbox writes use the absolute path `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/`. Never use `..`.

- Harness `~/.grok/sessions/.../plan.md` receives *only* a minimal process log entry that references this exact sandbox plan path (roll prior bulk to historical-plans/ if needed).

- At the very end, after the final successful build + post-forensic verification: output the exact marker "**END OF EXECUTION TURN. Awaiting new directive or plan approval before any further source changes or investigation that leads to edits.**" followed by "results ready to test" (include the new tag).

- Full standing rules (bi-modal workflow, handoff boundaries, 3-3-3 strikes, allowed reset contexts only with preflight via `./get-builds-tag.sh`, no deployment, ICRS or raw pixel coordinates, etc.): see the live `AGENT_MANDATES.md`, `MULTI_AGENT_USER_INSTRUCTIONS.md`, and `new_grok_agent_prompt` (re-read on every fresh launch, after compaction, and at start of new cycles).
