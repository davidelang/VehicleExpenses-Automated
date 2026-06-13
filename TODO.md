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

# Pump experiment Set C red box pixel histogram + near-containment merging rule (approved primary plan: /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/plans/pump-experiment-set-c-redbox-histogram-and-merging-20260612-plan.md)
- Execution started after explicit approval of the sandbox plan (revised lean version focused only on this turn's work).
- Re-reads of the approved plan + current-state.md + this TODO.md (done).
- Phase 0: Updated this TODO.md (this is the mandatory first action per the plan and mandates; no .kt source edits before it).
- Background preserved from state (valley push column structure for hists, CC via connectedComponentsWithStats in Set C, decimal OCR + >=2 digit filter, exact red nesting filter, blue retract).
- Follow the approved plan's Phased Small-Step Execution exactly:
  - Phase 1: Extend generateHistogramB64 with optional mask param. Capture redboxHistC in the Set C polarity probe block (using the existing red mask + masked calcHist on the pushed mat). Forensic read_file after edit + surrounding.
  - Phase 2: Implement the 40px near-containment extend-then-delete rule in the Set C blueRects (per-red overlapping hunks min/max) and orangeRects (same-row) logic (and retracted), using IcrsMath for pixel-space 40px check. Forensic read after.
  - Phase 3: Update pBuildHtmlRowDynamic Set C special case to display the redboxHistC (labeled "Redbox Hist (pixels in red boxes)") in the column (e.g. in the hists section). Forensic read after.
  - Phase 4: Re-verify current-state, git add the .kt + current-state.md (explicit), run ./build_app (pass files), verify success + new branch-scoped tag. Forensic re-reads on .kt post-build.
  - Phase 5: Full forensic re-reads. Final ./build_app (clean). Update current-state with COMPLETE + tag + handoff. Explicit message with the exact plan path + "results ready to test. **END OF EXECUTION TURN**". Complete stop.
- See the approved sandbox plan (the dev-ai path) for full (lean) Context (the work for this turn only), Approach, Critical Files, Reuse, detailed Phased steps, and Verification. No obsolete data.
- All constraints: ICRS/raw pixel only (no 0-1), forensic before/after every edit, ./build_app milestones, no deployment, no .. paths, primary artifact this plan, local state updated, etc.
- Verification per plan: builds clean; Set C column shows raw + the pushed image (small number of brightness values, visibly not binarized) + before/after hists + PD/ocr; A/B/root unchanged; JSON has new keys under Set C images; current-state + this TODO reflect.
- See the full approved sandbox plan (the executed path above) for Context, Recommended Approach (vs. display-only, bin-trials revival, etc.), Critical Files, exact Reuse list, detailed Verification, and Handoff Requirements. Any post-handoff feedback starts *new* turn (new plan file + directive).
- Local plan hygiene + current-state continuity followed. No historical plans or sessions/plan.md read/used as source.

# Pump experiment Set B red-only image + full annotations (approved primary plan: /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/plans/pump-experiment-set-b-red-only-and-full-annotations-20260612-plan.md)
- Execution started after explicit approval of the sandbox plan.
- Re-reads of the approved plan + current-state.md + this TODO.md (done).
- Phase 0: Updated this TODO.md (this is the mandatory first action per the plan and mandates; no .kt source edits performed before it).
- Background preserved from state (Set B red filter + blue retract + ocr, the 40px merging rule from prior C work, decimal OCR + >=2 digit filter, exact red nesting, etc.).
- Follow the approved plan's Phased Small-Step Execution exactly:
  - Phase 1: In the if (flowName == "Set B") block (right after the explicit doCrossScaleRedboxFilter on pdHunksRawTotal), create red-only anns from the raw reds only and redOnlyB64 snapshot; store as branch.images["PD_red_only"]. Keep all subsequent retracted blue, full aPd, baseB64, ocrLinesB, "PD", and pd_ocr_html *exactly* as before. Forensic read_file after edit on the full B block.
  - Phase 2: In pBuildHtmlRowDynamic (the subBranches.forEach for Paddle td), add if (name == "Set B") special case to emit red-only labeled + full labeled (as before) + extraOcr (pd_ocr_html). Leave the existing C special and generic paths untouched. Forensic read after.
  - Phase 3: Update comments in the B viz block and builder to document the two images (red-only for clean red inspection post filter/merging; full as is happening now). Forensic read after.
  - Phase 4: Re-verify current-state, git add the .kt + current-state.md (explicit), run ./build_app (pass files), verify success + new branch-scoped tag (get-builds-tag.sh + describe). Forensic re-reads on .kt post-build. (3-3-3 strikes + preflight approved reset only if needed.)
  - Phase 5: Full forensic re-reads on all edited sites. Final ./build_app (clean). Update current-state.md (re-read first) with COMPLETE + last tag + handoff. Explicit user message including the exact plan path + "results ready to test. **END OF EXECUTION TURN**". Complete stop.
- See the approved sandbox plan (the dev-ai path) for full Context (the display modification for Set B to allow redbox merging inspection), Approach (minimal, preserves "as is" for full), Critical Files, Reuse, detailed Phased steps, and Verification.
- All constraints: ICRS/raw pixel only, forensic before/after every edit, ./build_app milestones, no deployment, no .. paths, primary artifact this plan, local state updated, etc.
- Verification per plan: builds clean; Set B column in report shows red-only image (clean reds post filter, no blue/orange) + full annotations image (as before) + ocr html; A/C/other unchanged; JSON has "PD_red_only" under Set B; user can inspect whether redbox merging (current filter) matches expectation.
- See the full approved sandbox plan for Context, Recommended Approach, Critical Files, exact Reuse list, detailed Verification, and Handoff Requirements. Any post-handoff feedback starts *new* turn (new plan file + directive).
- Local plan hygiene + current-state continuity followed. No historical plans or sessions/plan.md read/used as source.

# Pump experiment rotation sign fix for Set A and B (approved primary plan: /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/plans/pump-experiment-rotation-sign-a-b-fix-20260612-plan.md)
- Execution started after explicit approval of the sandbox plan.
- Re-reads of the approved plan + current-state.md + this TODO.md (done).
- Phase 0: Updated this TODO.md (this is the mandatory first action per the plan and mandates; no .kt source edits performed before it).
- Background preserved from state (Set B red-only/full images from prior turn which provide clean view to verify rotation on reds, valley push, red filter, 40px merging rule in C, decimal OCR + >=2 digit filter, exact red nesting, blue retract, etc.).
- Follow the approved plan's Phased Small-Step Execution exactly:
  - Phase 1: In the common deskew/tilt selection (the when after deskewRes = OdometerOcrUtils.calculateAverageTextAngle, ~318-323), change "Set B" to -deskewRes.paddleCppAngle and else (A) to -deskewRes.angle. Leave the "Set C" line exactly as-is (its current effective direction is the one the user says "looks right"). Update the explanatory comment block immediately above it (~315-317) to document the correction (A and B now use negated values so the applied rotation matches the direction that makes C look correct on the images, including the clean red-only for B). The rotate call and metadata["tilt"] stay identical. Forensic read_file after the edit on the full tilt selection + comment + surrounding deskew paragraph.
  - Phase 2 (optional/minor): If any transitional procA/procB/procC stub comments still describe the old tilt choices, lightly update their descriptions to match the corrected signs. Forensic read after.
  - Phase 3: Re-verify current-state.md, git add the .kt + current-state.md (explicit), run ./build_app (pass the files), verify success + new branch-scoped tag (get-builds-tag.sh + describe). Forensic re-reads on the edited tilt/comment area post-build. (3-3-3 strikes + preflight approved reset only if needed.)
  - Phase 4: Full forensic re-reads on the tilt area + any stub comments. Final ./build_app (clean). Update current-state.md (re-read first) with COMPLETE + last tag + handoff. Explicit user message including the exact plan path + "results ready to test. **END OF EXECUTION TURN**" (remind to re-run limited experiment; Set A/B images including clean red-only for B should now have the correct rotation direction matching current C; per-set tilts in report/JSON will show the corrected applied values). Complete stop.
- See the approved sandbox plan (the dev-ai path) for full Context (user observation that A/B are backwards while C's negation of B looks right; clean red view from prior turn helps verification), Approach (minimal sign flip on the two branches that were wrong, preserve C's effective value), Critical Files, Reuse (the when, rotate, metadata["tilt"], perSetTilts), detailed Phased steps, and Verification.
- All constraints: ICRS/raw pixel only, forensic before/after every edit, ./build_app milestones, no deployment, no .. paths, primary artifact this plan, local state updated, etc.
- Verification per plan: builds clean; after fix the processed images for A and B (full PD and B's clean red-only) have the rotation direction that makes text upright the same way current Set C images do; "Tilt per set" and JSON reflect the corrected applied tilts for A/B; C visuals and all non-rotation behavior identical to before.
- See the full approved sandbox plan for Context, Recommended Approach, Critical Files, exact Reuse list, detailed Verification, and Handoff Requirements. Any post-handoff feedback starts *new* turn (new plan file + directive).
- Local plan hygiene + current-state continuity followed. No historical plans or sessions/plan.md read/used as source.

# Pump experiment red 3sides nesting for B (approved primary plan: /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/plans/pump-experiment-red-3sides-nesting-for-b-20260612-plan.md)
- Execution started after explicit approval (includes the inspection of the user's JSON: row 3 Set B has 18 raw ICRS reds in Paddle Raw; current exact filter keeps ~5 (user sees 6 in the red-only image); among kept, the pair kept[2] (large l~-0.155 t~-0.232 w0.60 h0.196) and kept[4] (l~-0.019 t~-0.186 w0.288 h0.154) has 3 sides inside with exactly 12px protrude on 4th side (<=40px) -- this should have been extended+deleted by the 3sides algo but wasn't by exact).
- Re-reads of plan + state + TODO (done).
- Phase 0: Updated this TODO.md (mandatory first action; no .kt before it).
- Background preserved (red-only for B from prior turn used for this inspection, rotation fix, 40px 3sides only in C blue/orange until now, etc.).
- Follow the approved plan exactly:
  - Phase 1: In doCrossScaleRedboxFilter (after the existing exact sequential "kept" loop), add the 3sides +40px extend (copy the run{} logic from the blue code: pixel Rect via IcrsMath, insides.count==3, min/max extend on out dim for PumpHunk/RectF, then contained cleanup pass). Update the filter comment to document both exact and the 3sides case. The final list (post both) is what gets used for redAnns in B (red-only and PD) and red source for C. Forensic read on the filter + calls + the 3sides reference in blue.
  - Phase 2 (light): update comments at B call site or global filter if they only mention "exact"/"nested".
  - Phase 3: Build (re-verify state, git add .kt + current-state explicit, ./build_app with files, verify tag). Forensic re-reads post build.
  - Phase 4: Full forensics. Final build (clean). Update state with COMPLETE + tag. Handoff with exact plan path + "results ready to test. **END OF EXECUTION TURN**" (remind to re-run; red-only for Set B like row 3 will now have the 12px pair merged via extend+delete, fewer reds as expected).
- See the approved sandbox plan for the full inspection data from the JSON, the before/after counts, the specific 12px pair, and the fix (port to red filter so nesting removal works for the reds the user is inspecting in Set B via red-only).
- All constraints followed.
- Verification: the red filter now also does 3sides, so for the reported case the pair will be merged, red count in red-only drops, matching user expectation from the "new 3 sides enclosed algorithm".
- See the full approved plan for Context (with the 18 list and pair), Approach (enhance the shared red filter with the proven 3sides logic), etc.
- Local hygiene + continuity followed. No historical as source.

# Pump experiment fix 3sides merging overlap 40px (approved primary plan: /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/plans/pump-experiment-fix-3sides-merging-overlap-40px-limit-20260612-plan.md)
- Execution started after explicit user approval ("approved, execute this plan").
- Re-reads of *exactly* the approved plan + current-state.md + this TODO.md (done).
- Phase 0: Updated this TODO.md (this is the mandatory first action per the plan and mandates; no .kt source edits performed before it).
- Background preserved from state (20-box identical "Paddle Raw" input across reports from prior analysis, red-only for B, previous 3sides addition that introduced the loose predicate, blue retract, valley push, decimal OCR + >=2 digit filter, exact red nesting, CC big/little via connectedComponentsWithStats, blue retract via expandByUniformity, ICRS + raw pixel only, etc.).
- Follow the approved plan's Phased Small-Step Execution exactly:
  - Phase 1: Add the `qualifiesFor3SidesNearExtend(cR: android.graphics.Rect, oR: android.graphics.Rect): Boolean` helper (centralizes the fixed test: count==3 on edges + protrusion_px <=40 in pixel space from the cR/oR + hasOverlap on the protruding axis e.g. oR.right > cR.left for left). Update the three 3-sides sites (doCrossScaleRedboxFilter post-kept block, blueRects run{}, orangeRects run{}) to use `if (qualifiesFor3SidesNearExtend(cR, oR))` instead of bare count==3. Keep extend + cleanup identical. Update related comments for accuracy. Forensic read_file immediately after the edit(s) on helper + the three sites + comments + call sites.
  - Phase 2 (light optional): Refresh any other comments mentioning the old loose phrasing. Forensic after.
  - Phase 3: Re-verify current-state.md, git add .kt + current-state.md (explicit), run ./build_app (pass files), verify success + new tag. Forensic re-reads post-build.
  - Phase 4: Full forensic re-reads. Final ./build_app (clean). Update current-state.md (re-read first) with COMPLETE + last tag + handoff. Explicit message with the exact plan path + "results ready to test. **END OF EXECUTION TURN**" (remind to re-run Limited Experiment (Golden Subset); compare new red-only for row 3 Set B vs 14-51-15 report — fewer erroneous merges, higher red count for the 90 bad pairs / example cases; intended small-protrusion+overlap cases still merge; C blues/oranges also improved).
- See the approved sandbox plan (the dev-ai path) for full Context (user diagnosis + 20-box/90-bad-pairs simulation data + concrete example), Approach (targeted helper for the predicate), Critical Files, exact Reuse list, detailed Phased steps, and Verification.
- All constraints followed: ICRS/raw pixel only (no 0-1), forensic before/after every edit, ./build_app milestones, no deployment, no .. paths, primary artifact this plan, local state + TODO updated, etc.
- Verification per plan: builds clean; the "test" (3-sides decision) now requires real overlap + <=40px; red-only (Set B) and blue/orange (C) will correctly avoid over-merging on the 20-box cases; user to visually confirm via re-run.
- Local plan hygiene + current-state continuity followed. No historical as source.

# Pump experiment Set C per-redbox histograms + 3x images (approved primary plan: /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/plans/pump-experiment-set-c-per-redbox-histograms-3x-images-20260612-plan.md)
- Execution started after explicit approval ("approved").
- Re-reads of *exactly* the approved plan + current-state.md + this TODO.md (done).
- Phase 0: Updated this TODO.md (this is the mandatory first action per the plan and mandates; no .kt source edits performed before it).
- Background preserved from state (valley push + before/after hists + redboxHistC combined for C from prior plans, red filter/3sides, branch.images/metadata serialization to JSON reports, ICRS/pixel rects, etc.).
- Follow the approved plan's Phased Small-Step Execution exactly:
  - Phase 1: Enlarge before/after (raw/pushed snapshots 225->675; hist bmp sizes in generateHistogramB64 *3: 62x100->186x300 and 100x60->300x180). Forensic read after.
  - Phase 2: In Set C probe block (keep combined mask for polarity), add per-redbox loop over pdHunksRawTotal: per-mask, per-histB64 to branch.images["redboxHistC_${i}"], compute h/w/area (pixels) + 64-bin histBins, collect into redboxDataC JSONArray, store as branch.metadata["redboxDataC"]. Update comment. Forensic after.
  - Phase 3: In builder Set C display, replace single rbox with per-red labeled hists (using redboxDataC for labels); keep/enhance raw/pushed/hB/hA table (now 3x res). Update Set C flow comment. Forensic after.
  - Phase 4: Re-verify current-state, git add .kt + current-state.md (explicit), ./build_app (pass files), verify tag. Forensic re-reads post-build.
  - Phase 5: Full forensic re-reads. Final ./build_app (clean). Update current-state (re-read first) with COMPLETE + tag + handoff. Explicit message with exact plan path + "results ready to test. **END OF EXECUTION TURN**" (remind to re-run Limited Experiment; inspect new Set C column for per-red labeled hists + visibly larger before/after images/hists; check pump_results JSON under Set C for redboxHistC_N b64s + redboxDataC with h/w/area/bins).
- See the approved sandbox plan (the dev-ai path) for full Context (verbatim user request + current combined logic), Approach (per-box masks + structured data + 3x sizes), Critical Files, exact Reuse list, detailed Phased steps, and Verification.
- All constraints followed: ICRS/raw pixel only, forensic before/after every edit, ./build_app milestones, no deployment, no .. paths, primary artifact this plan, local state + TODO updated, etc.
- Verification per plan: builds clean; Set C column shows per-red hists labeled with h/w/area (replacing combined), before/after images at 3x res; JSON has per-red b64s + numeric data for analysis; other behavior (polarity, PD, A/B, valley) unchanged.
- Local plan hygiene + current-state continuity followed. No historical as source.

# Pump experiment Set C sorting timing blue expansion (approved primary plan: /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/plans/pump-experiment-set-c-sorting-timing-blue-expansion-fixes-20260612-plan.md)
- Execution started after explicit approval ("approved").
- Re-reads of *exactly* the approved plan + current-state.md + this TODO.md (done).
- Phase 0: Updated this TODO.md (this is the mandatory first action per the plan and mandates; no .kt source edits performed before it).
- Background preserved from state (per-redbox hists + 3x from prior plan, red filter, 3sides fix, valley push, CC blue in Set C, alignment Set E expansion references, existing timings, ICRS/pixel, etc.).
- Follow the approved plan's Phased Small-Step Execution exactly:
  - Phase 1: In pBuildHtmlRowDynamic Set C branch, parse rdata, sort by area desc (largest first), build as <table> 3 cols with per cell <img> + stacked <small>h=${hh}<br>w=${ww}<br>area=${aa}</small>. Update related comment. Forensic read after.
  - Phase 2: In header metaHtml build, filter entries to skip keys containing "redboxDataC" or "DataC". Forensic after.
  - Phase 3: In Set C td table for raw/pushed/hB/hA, change max-width from 60% to e.g. 120% (twice current), add "(2x)" labels. Forensic after.
  - Phase 4: Add timing points (t0 = System.currentTimeMillis() etc.) in procC / Set C ifs: before/after valley, red probe + per red hists, blue creation, 3sides+retract, orange, discovery, PD snap, total. Store as branch.metadata["t_xxx_ms"]. Update comments. Forensic after.
  - Phase 5: In Set C blue section (after redBoxes + vSW/hSW), replace the blackOut/findAll + overlapping hunks for blueRects with loop over reds calling NativeImageUtils.expandByValleyDiagnostic(workspace.p.mat, redPixRect, 0.40f) (or char aware), convert to ICRS for blueRects. Note bg adaptation (post-invert). Keep 3sides/retract/orange. Remove/comment old logic. Update long comment. Forensic after on blue block + comment.
  - Phase 6: Re-verify current-state, git add .kt + current-state.md (explicit), ./build_app (pass files), verify tag. Forensic re-reads post-build.
  - Phase 7: Full forensic re-reads. Final ./build_app (clean). Update current-state (re-read first) with COMPLETE + tag + handoff. Explicit message with exact plan path + "results ready to test. **END OF EXECUTION TURN**" (remind to re-run Limited Experiment on photos with many reds; inspect Set C for sorted 3-wide stacked per-red hists (largest area first), clean meta, 2x dash, timings in meta/JSON; check pump_results JSON for new t_ keys + still per-red data; verify blue boxes look better/fewer errors from valley expand; compare timings for bottlenecks).
- See the approved sandbox plan (the dev-ai path) for full Context (verbatim multi-bullet user request + code grounding from pump/alignment), Approach (5 targeted changes), Critical Files, exact Reuse list, detailed Phased steps, and Verification.
- All constraints followed: ICRS/raw pixel only, forensic before/after every edit, ./build_app milestones, no deployment, no .. paths, primary artifact this plan, local state + TODO updated, etc.
- Verification per plan: builds clean; Set C column has sorted 3-wide stacked per-red hists (largest area first), no verbose redboxDataC in meta header, twice larger before/after dash images, many t_ timings in JSON, blue now via Set E valley expand (fewer errors); other behavior (A/B, valley, red filter, PD, ocr) unchanged. Per-red data from prior remains.
- Local plan hygiene + current-state continuity followed. No historical as source.

# Pump experiment lot of granular timings + histogram impl details (approved primary plan: /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/plans/pump-experiment-lot-of-granular-timings-histogram-impl-details-20260612-plan.md)
- Execution started after user approval ("this plan is approved").
- Re-reads of *exactly* the approved plan (full content re-read at start of execution) + current-state.md (worktree root) + this TODO.md (done).
- Phase 0 (mandatory): Updated this TODO.md (this is the mandatory first action per the plan and mandates; no .kt source edits performed before it). git status clean check performed.
- Background preserved from state (per-redbox hists 3x/sorted/3-wide/stacked from prior + redboxDataC with n=30 pre-filter entries (h/w/area/histBins for analysis), valley push for C, blue derivation via expandByValleyDiagnostic (adapted from alignment Set E for post-invert "white on black"), 3sides near-containment (overlap+<=40px fixed in doCross + blue/orange), red-only PD for B, rotation sign fix for A/B, decimal OCR + >=2 digit filter in reports, big/little via cv::connectedComponentsWithStats in nativeBlackOut/findAll, first batch of t_ (tFlowStart/t_valley/t_per_red_hists/t_red_probe/t_blue_creation/t_total + per-scale t_pd), ICRS/raw pixel only, branch.metadata/images serialization to pump_results JSON, generateHistogramB64 with mask support). Plus exact research answers: histogram data (redboxDataC + redboxHistC_*) created in Kotlin (calls to Imgproc.calcHist + manual Canvas drawRect loop in generate); NOT a crop (full Mat.zeros(workspace.p.mat.size()) + rectangle on perMask passed with original mat); manual loops yes (for (i in 1..62) drawRect for the b64 visuals); native hist path separate/not used for these.
- Follow the approved plan's Phased Small-Step Execution exactly:
  - Phase 1: Add common high-level timers + context (t_setup_ms after tFlowStart + buffer prep/discoveryDetails; t_deskew_ms after deskew/tilt; t_discovery_wrapper_ms around 4-scale discovery; t_filter_ms after doCross calls + n_reds_after_filter; t_pd_snapshot_ms after final takeSnapshot for PD; t_ocr_ms after ocrLines + pd_ocr_html; n_reds_at_probe etc. at natural points). Forensic read_file on per-flow lambda top + deskew + discovery + filter + snapshot + ocr sites immediately after the edits.
  - Phase 2: Granular sub-timers inside Set C early probe (t_probe_start at if (flow=="Set C") entry; t_polarity_run_ms after its runPaddle; before per-red forEach: n_reds_at_probe + t_per_red_loop_start; inside forEach: accumulators around mask zeros/rect (t_per_red_mask_create_ms), around generateHistogramB64 call (t_per_red_generate_b64_ms covering the manual for-loop drawRect + b64), around second calcHist for bins (t_per_red_bins_calc_ms); after forEach: t_per_red_loop_overhead_ms + keep existing t_per_red_hists_ms/t_red_probe_ms; around polarity calc + decision + possible invert: t_polarity_decision_ms + t_invert_if_needed_ms; wrap other generateHistogramB64 call sites (e.g. histBeforeC) with lightweight t_ for globals. Add/update comments next to every t_ write citing the exact code (mask zeros vs crop, the for (i in 1..62) in generate, "Kotlin not C", "pre-filter 30 reds on this example"). Forensic read_file on entire probe block (~620-690) + generateHistogramB64 after edits.
  - Phase 3: Granular sub-timers inside Set C blue/orange (keep tBlueStart + t_blue_creation_ms as outer for the block; around NativeImageUtils.calculateHistogramWithThresholdH(hRes) at ~1012: t_blue_native_hist_ms; around per-red expandByValleyDiagnostic loop: t_blue_valley_expands_ms; around 3sides pass: t_blue_3sides_ms; around retract (expandByUniformity): t_blue_retract_ms; similar for orange hunks/retract). Forensic re-read of the blue block (~987-1110) + comments.
  - Phase 4: Build milestone - re-verify current-state.md (read first), explicit git add .kt + current-state.md, ./build_app (pass files), verify success + new branch-scoped tag. Forensic re-reads on all timer sites + generate + probe + blue post-build. Update top Set C flow comment (~241) and procC stub with summary of new granular keys + the histogram creation answers (Kotlin, full-mat+mask no crop of data, manual drawRect loop for b64 visuals, separate native path not used here).
  - Phase 5: Full forensic re-reads (plan, current-state tail, every edited timer write + its comment, generateHistogramB64, the two calcHist sites in probe, the native hRes call site). Final clean ./build_app (nothing to commit). Update current-state.md (re-read first) with COMPLETE + last tag + summary (list the ~25+ new t_ keys + n_* context values; exact answers to the Kotlin/C + crop/loop questions with short code quotes; "the next run's pump_results JSON will have enough granular data for A/B gap attribution and full C probe/blue decomposition in a single run; no further data-gathering turn required"). Explicit user message including the exact plan path `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/plans/pump-experiment-lot-of-granular-timings-histogram-impl-details-20260612-plan.md` + "results ready to test. **END OF EXECUTION TURN**" (remind to re-run Limited Experiment (Golden Subset); inspect new t_* (and kept prior) + n_reds_* + redboxDataC (still 30) in JSON under Set A/B/C for row 0 and others; per-red sub-timers show mask/calc/generate (manual loop) breakdown; blue sub-timers show native hist vs valley vs 3sides vs retract; totals now explainable; A/B non-inference phases visible for first time). Complete stop.
- See the approved sandbox plan (the exact dev-ai path above) for full Context (verbatim user query "better to have a lot of granular timing data rather than having to do another turn to gather the data" + the two histogram questions + jq+code research findings), Recommended Approach (comprehensive 25+ granular list with substeps inside per-red + blue + common phases + context; why over fewer-timers or crop alternatives), Critical Files, Existing Functions/Utilities to Reuse, detailed Phased Small-Step Execution (forensic + build), Verification (end-to-end: one run's JSON has full attribution without needing another turn; comments document the answers), and Handoff Requirements (non-negotiable).
- All constraints followed exactly: re-read *exactly* the designated sandbox plan + current-state as first execution steps; TODO.md updated FIRST before any .kt; forensic read_file before/after *every* modification + post-build; explicit git add + ./build_app (milestone + final) creating branch-scoped tag; 3-3-3 strikes if needed (approved reset contexts only with preflight); no deployment; ICRS or raw pixel integers only (no normalized 0-1); relative paths preferred for tool args except absolute sandbox for the plan artifact; jq priority (used in research); harness session plan.md kept as short log only (primary is the sandbox file); current-state.md (local untracked per-branch) used for continuity; STOP after handoff (any further feedback = new planning cycle with new plan + Directive).
- Verification per plan: builds clean with tag; every edit forensically verified by read; the *next single run's* pump_results JSON (row 0 + others) will contain 25+ t_ + n_reds_at_probe=30 etc. so A/B ~4s gap after 2560 inference is attributed (setup/deskew/wrapper/filter/snapshot/ocr), and C numbers (probe ~4.8s sub-broken into mask/calc/generate (manual loops) + polarity + blue native/valley/3sides/retract) are fully decomposable and cross-checkable against t_total_flow_ms + kept prior t_per_red etc. No behavior change to images/boxes/OCR/redboxDataC/valley/3sides/blue derivation. Histogram questions answered in code comments + plan. Self-explanatory data in JSON.
- Local plan hygiene + current-state continuity followed. No historical-plans/ or sessions/ plan.md read/used as source for implementation. Primary approved artifact is the sandbox plan at the dev-ai path named in the approval.
