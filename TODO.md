# TODO

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
- State files must be **per-branch** and live **in the local worktree directory** as **untracked** files (e.g. `current-state.md` or `.agent-state/current-state.md` at the worktree root, not in the shared dev-ai-interaction/ sandbox). Agent instructions require reading the local current-state.md first on every fresh launch or new cycle + the user-designated sandbox plan file.
- Planning phase is the **interactive strategic layer**: user may give rich problem descriptions, direction, and iterative feedback. Agent responds by revising the *plan document* in dev-ai-interaction/ (not source). Only after the user issues the exact magic approval phrasing naming a specific sandbox plan path does mechanical execution begin.
- Update bootstrap files (new_grok_agent_prompt point 5 + Sandbox Plan File paragraph, AGENT_MANDATES.md, AGENTS.md, MULTI_AGENT_USER_INSTRUCTIONS.md) + .gitignore so that when the user explicitly directs a current-turn plan, agents create/revise fresh plan files under dev-ai-interaction/ and use the local untracked per-worktree state file for continuity. The "historical reference only" rule applies only to old/abandoned plans from other agents/cycles.
- Demo during execution: create local untracked current-state.md in the current worktree root with minimal structure. Primary plan documents stay in the sandbox.
- See the approved plan at dev-ai-interaction/interactive-strategic-planning-and-continuity-plan.md for full details, critical files (new_grok_agent_prompt, AGENT_MANDATES.md, AGENTS.md, MULTI_AGENT_USER_INSTRUCTIONS.md, .gitignore), and verification. Pre-turn state: orchestration branch; follows handoff from the previous robust cycle enforcement plan; no app source changes.

# Pump experiment B and C boxes (approved primary plan: /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/pump-experiment-b-and-c-red-nested-filter-and-blue-retract-20260612-plan.md)
- Execution started after approval.
- Forensic reads of filter call site, Set B block, Set C block, blue derivation (~913), Native expandByUniformity (done).
- Verify/add explicit nested filter application in Set B block for redAnns and blue source (shared filter at 731 applies to B's pdHunksRawTotal; add explicit per user feedback that it may have been removed from B).
- Same explicit for C before redBoxes/blue loop.
- Implement blue retract for Set C (after blueRects union from CC hunks per red): use NativeImageUtils.expandByUniformity on binMat to get retractedRect for tight text fit (retract when hit limit with no text).
- Similar retract for B's blue from pdHunksExpTotal.
- Update comments; strengthen filter if needed.
- ./build_app milestones; final forensic, build, user verification run (inspect B and C reports for de-nested reds, retracted tight blues).
- See the approved sandbox plan for full Context (including B vs C filter investigation and JSON check: error format, source analysis), Approach, Critical Files, Reuse, Phased steps, and Verification. CC memory note in state. Previous ocr decimal/2-digit plan also in state.

# Pump experiment Set C valley histogram push (approved primary plan: /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/plans/pump-experiment-set-c-valley-histogram-push-20260612-plan.md)
- Execution started after explicit user directive: "execute /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/plans/pump-experiment-set-c-valley-histogram-push-20260612-plan.md"
- Phase 0 (mandatory first): Re-read *exactly* the designated sandbox plan file (full) + current-state.md (worktree root) + this TODO.md (done). Updated this TODO.md (this is the first action per plan + mandates; no .kt source edits performed before it).
- Background preserved from state: big/little filter (cv::connectedComponentsWithStats details), OCR decimal + >=2 digit filter, blue retract via expandByUniformity, red nesting (exact no-inset sequential filter on raw/exp/max so filtered reds do not exist for blues).
- Follow the approved plan's Phased Small-Step Execution exactly:
  - Phase 1: Add valleyPushToPeaks (OdometerOcrUtils.kt) near stretch funcs. 64-bin + smooth + findValleyMidpoints for valley centers + robust peaks (adapt from automaticContrastStretch) + 256 LUT (basin/ push out from valley to peak grays) + in-place remap on mat (not binarize; output has small # distinct brightness = # peaks). Return before bins. Forensic read_file after edit + surrounding.
  - Phase 2: Integrate in ExperimentPumpScreen.kt (stretch site ~294): for "Set C" capture rawC + histBeforeC (pre), call valleyPushToPeaks (replaces stretch), capture pushedC + histAfterC. A still does root after/hist2. Forensic read after.
  - Phase 3: Update pBuildHtmlRowDynamic (Set C td ~1370s) to render raw + pushed (few brightness) + before/after hists (mini table or stack, labeled) inside the column td, above existing PD + pd_ocr_html. Update comments (top flow desc, stretch comment, doValleyForC stub, old_bin_trials block). Forensic read after. A/B columns untouched.
  - Phase 4: Re-verify current-state, git add *only* the two .kt + current-state.md (explicit, no TODO), run ./build_app (pass files), verify success + new branch-scoped tag (get-builds-tag.sh + describe). Forensic reads on .kt post-build. (3-3-3 strikes + preflight approved reset only if needed.)
  - Phase 5: Full forensic re-reads on all edited sites. Final ./build_app (clean). Update current-state.md (re-read first) with COMPLETE + last tag + handoff. Explicit user message including plan path + "results ready to test. **END OF EXECUTION TURN**". Complete stop.
- All constraints: ICRS/raw pixel only (no 0-1), forensic before/after every edit, ./build_app milestones, no deployment, no .. paths, primary artifact this plan, local state updated, etc.
- Verification per plan: builds clean; Set C column shows raw + the pushed image (small number of brightness values, visibly not binarized) + before/after hists + PD/ocr; A/B/root unchanged; JSON has new keys under Set C images; current-state + this TODO reflect.
- See the full approved sandbox plan (the executed path above) for Context, Recommended Approach (vs. display-only, bin-trials revival, etc.), Critical Files, exact Reuse list, detailed Verification, and Handoff Requirements. Any post-handoff feedback starts *new* turn (new plan file + directive).
- Local plan hygiene + current-state continuity followed. No historical plans or sessions/plan.md read/used as source.
