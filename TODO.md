# TODO

- [x] Safe leading env assignment prefix support ("KEY=val cmd") for all already-allowed bash commands + promote agent-1 "don't ask again" commands to global checked-in config (plan approved 2026-06-13)
  - [x] Forensic read of .grok/config.toml and .grok/hooks/plan-mode-hard-stops.js (before edits)
  - [x] Add stripLeadingAssignments + getLastPipeBase + generalized early-allow logic to the hook (so prefixed forms of blessed bases like jq/ls/git/echo/find/build_app/etc. no longer prompt; blocks loopholes by checking the first non-assignment token). Python3 * deliberately never included (user confirmed too dangerous).
  - [x] Update comments in .grok/config.toml documenting the prefix form + hook normalization. Added narrow patterns for confirmed pager items (adb logcat* for reads [user: "adb logcat is reading data, that is allowed"], echo *, find *, true).
  - [x] Selectively promote confirmed items from agent-1/permission_grok-pager.toml allowed_bash_commands (adb logcat for reads [confirmed allowed by user], echo *, find *, true, and specific git describe/tag--list if not redundant) as narrow patterns in root config.toml. Do **not** add any python3 * (user confirmed: too dangerous; use dedicated helpers only)
  - [x] Run ./update-rules.sh (from orchestration root) — synced hook + config (and run-* launchers) to agent-1/ and master/ with commits.
  - [x] ./build_app (create builds tag) — commit for the changed files succeeded; full gradle/app build skipped (not applicable in pure orchestration root with no gradlew/app tree); orchestration/builds tag force-updated at the resulting HEAD via get-builds-tag.sh + git tag -f (per AGENT_MANDATES preflight and plan requirements).
  - [x] Update this TODO, output **END OF EXECUTION TURN** marker + "results ready to test"
- [x] Refactor Agent Workspace Syncing
    - [x] Update `setup_agent.sh` to remove hard links and protections.
    - [x] Update `update-rules.sh` to push updates and commit to all worktrees.
    - [x] Validate changes by audit and build.
- [x] Fix Sandbox Policy Permissions
    - [x] Update `.gemini/policies/plans.toml` with whitespace tolerance.
    - [x] Update `.gemini/policies/auto-saved.toml` to cleanup mode-based restrictions.
    - [x] Commit and sync rules across all worktrees.
- [x] Refine update-rules.sh Robustness
    - [x] Update `update-rules.sh` to break links and handle read-only targets.
    - [x] Re-run sync and verify inodes.
- [x] Cleanup Reports on Device
    - [x] Modify `fetch_latest_reports.py` to remove old reports from device.
    - [x] Commit changes.
- [x] Enforce Git Reset and Validation Rigor
    - [x] Update `.gemini/policies/auto-saved.toml` to restrict `git reset`.
    - [x] Update `GEMINI.md` to mandate forensic audits.
    - [x] Update `.gemini/system.md` to reflect new rigor.
    - [x] Commit and sync rules across all worktrees.
- [x] Refine Git Reset and Approval Policy
    - [x] Update `.gemini/policies/auto-saved.toml` with tiered policies (HEAD allowance, 'ask' for other resets, 'deny' for catch-all git).
    - [x] Commit and sync rules across all worktrees.
- [x] Recommend jq for JSON Parsing
    - [x] Update `GEMINI.md` with jq recommendation.
    - [x] Update `.gemini/system.md` with jq recommendation.
    - [x] Commit and sync rules across all worktrees.
- [x] Fix jq and Whitespace Permissions
    - [x] Update `plans.toml` with robust whitespace regex.
    - [x] Update `auto-saved.toml` to allow `jq` in Plan Mode.
    - [x] Commit and sync rules across all worktrees.
- [x] Resolve jq Plan Mode Block
    - [x] Update `auto-saved.toml` with high-priority regex for jq.
    - [x] Commit and sync rules across all worktrees.
- [x] Refactor jq Rule and Whitelist ls
    - [x] Update `auto-saved.toml` to use commandPrefix for jq and add ls.
    - [x] Commit and sync rules across all worktrees.
- [x] Fix Master Agent 'works' Tag Violation
    - [x] Update `GEMINI.md` with Safety Override clause.
    - [x] Update `MASTER_AGENT_MANDATE.md` with strict merge template.
    - [x] Commit and sync rules across all worktrees.

# Meta plan (approved 2026-06-12 on orchestration branch): Robust Plan/Execute Cycle
- Primary deliverable of every planning phase must be a fresh, clean task-specific plan document written to dev-ai-interaction/plans/ (e.g. <task>-<date>-plan.md) using the standard structure (Context, Recommended Approach, Critical Files with paths, Reusable utilities with exact locations, Phased steps with forensic+build, Verification). User approves by explicit path reference + directive (e.g. "approved the plan at dev-ai-interaction/XXX-plan.md for the following..."). Agent must re-read exactly that designated file as first step in execution.
- Harness ~/.grok/sessions/.../plan.md (the process log whose path appears in plan-mode reminders) is *not* the work plan. Use it only for short log entries referencing the sandbox plan path. At start of new cycle (or post-handoff), roll any prior superseding/historical bulk to dev-ai-interaction/historical-plans/harness-plan-archive-....md first, then prepend only the minimal current-cycle header (prepending to, not even superseding, old content).
- Subagent separation supported and recommended for complex work: in planning (main stays in plan mode), optionally spawn_subagent (type "plan" or via "design" skill) with narrow prompt whose *only* job is research + write one new sandbox plan file + return its path. After user approval of that specific sandbox plan path, main (or spawned "implement"/execution subagent with the approved plan content injected) executes precisely, with TODO first, forensic read before/after every edit, ./build_app, exact END OF EXECUTION TURN marker + "results ready to test", then complete stop. Subagents blocked in plan mode per existing rules.
- Post-handoff / new cycle start: Happy path is exit the CLI and relaunch via run-grok (forces fresh Mandate report + enter_plan_mode + STOP per the launcher + new_grok_agent_prompt). Fallback: agent at every handoff writes short dev-ai-interaction/.post-handoff-gate.txt; user does `cat dev-ai-interaction/.post-handoff-gate.txt` then appends request (one-liner ritual). The new tracked MULTI_AGENT_USER_INSTRUCTIONS.md (at root, synced by update-rules.sh to master/agent worktrees) is the authoritative human reference.
- In new_grok_agent_prompt Mandate report: agent must confirm "I created/wrote the task plan to dev-ai-interaction/<name>.md", "harness session plan kept concise with roll if needed", "post-handoff will use relaunch or wrote the short gate file".
- See approved plan (this session's harness plan.md + the produced MULTI_AGENT_USER_INSTRUCTIONS.md) for full details, critical files, and verification. Pre-turn state: orchestration branch; session plan at /home/dlang/.grok/sessions/%2Fhome%2Fdlang%2Fgit%2FVehicleExpenses-automated/019ebbe2-611e-77e1-a312-f7cd1412096e/plan.md; no app source changes in scope.

# Meta plan (approved 2026-06-12 on orchestration branch): Interactive Strategic Planning + Continuity
- Stable orientation facts live in the tracked `project-facts.md` at each worktree root. It must contain only layout / "where things live" facts that remain true after branch merge + new worktree for different effort. Agents read it first on launch/new cycle (in addition to the user-designated sandbox plan).
- Planning phase is the **interactive strategic layer**: user may give rich problem descriptions, direction, and iterative feedback. Agent responds by revising the *plan document* in dev-ai-interaction/ (not source). Only after the user issues the exact magic approval phrasing naming a specific sandbox plan path does mechanical execution begin.
- Update bootstrap files, AGENT_MANDATES.md, AGENTS.md, MULTI_AGENT_USER_INSTRUCTIONS.md, .grok/config.toml, and .gitignore to reflect that `project-facts.md` is now tracked, planners may edit it in plan mode (alongside TODO.md), and it is strictly limited to stable facts (no branch/tag/current-plan). The untracked current-state.md name is legacy.
- Primary plan documents stay in the sandbox; project-facts.md is for enduring location facts only.
- See the approved plan at dev-ai-interaction/interactive-strategic-planning-and-continuity-plan.md for full details, critical files (new_grok_agent_prompt, AGENT_MANDATES.md, AGENTS.md, MULTI_AGENT_USER_INSTRUCTIONS.md, .gitignore), and verification. Pre-turn state: orchestration branch; follows handoff from the previous robust cycle enforcement plan; no app source changes.

# Current cycle
- [x] make-zip-extract-additive-no-delete-both-pump-alignment-screens-20260624-plan: remove deleteRecursively from extract in both experiment screens (additive ZIP extract)
- [x] clear-jsonfrag-per-row-20260623-plan: stream JSON per row + immediate frag delete
- [x] pump-unzip-button-flatten-fix-and-first10-all-nonthumb-zips-to-device-20260623-plan: flatten pExtractZipToPhotos + deploy script --make-zips/--push-zips
- [x] replace-mask-mat-with-direct-rect-run-walking-20260623-plan: direct H hist walk, no coverage mask
- [x] integrate-stop-bufferset-realloc-from-master-20260623-plan: cherry-pick capacity-reuse nativeResize + HIST_DIAG from master
- [x] log-mat-headers-to-diagnose-hist-setsize-crash-20260623-plan: MAT_HEADER dumps on crop/hist path

# Future work
- [x] Separate the orchestration layer (run-*, update-rules.sh, set-*-perms, setup-project, .grok/ config/hooks, permission model, worktree management, multi-agent brain) from the application source (app/, master + feature branches) into distinct trees/concerns. This will allow project-facts.md (and other facts) to be scoped appropriately per tree without overlap.
  - Approved plan: dev-ai-interaction/plans/orchestration-layer-separation-and-cleanup-plan.md
  - Execution completed (user: "approved, implement this plan"). All 7 phases + forensic gates + sim verification.
  - No app/ source touched; language-agnostic; only orchestration-infra + docs + bootstrap.
  - New: enable-full-orchestration.sh (stampable opt-in); dual-mode update-rules (stamp vs full); explicit one-time stamp comments in setup-project; docs + migration notes.
  - See ENGINEERING_LOG.md and dev-ai-interaction/orchestration-layer-inventory-phase1.md for details.
  - Results ready to test (new tag via final build).
