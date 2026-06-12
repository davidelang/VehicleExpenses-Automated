# TODO

- [ ] Pump Experiment: Refactor to array of per-flow processor functions (user-approved pattern: "an array of functions that you can iterate over is fine") + deliver Set C (pump-only + alignment Set J valley bin-test: 64bin hist, findValleyMidpoints, per-midpoint THRESH_BINARY, independent detect/redbox(+1/nest per scale + cross filter) on each binarized, stacked composite in Set C column via stackVertically, best version for Paddle result+crops). No modifications to pBuild* reporting.
    - Locked builds point (per user note + mandates): a8f17a0e6330441c9b7a6fa7fde1a957d497f236 (fix-pump-experiment/builds) — state after stackVertically + runPaddleDiscovery helper + redbox ports + partial Set C guards built. In this turn: ONLY git checkout . (or restore/HEAD reset for uncommitted) for discards; never reset --hard past a8f17a0e or to older tag. New successful ./build_app advances the builds tag (new "do not reset past" point).
    - Approved plan (from session plan.md): Refactor removes the tangled if/when/guards/dupe discovery inside flows.forEach by introducing a list/array of processor functions (one per set, in matching order with flows) that are iterated (forEachIndexed/zip). Each processor body is a clean linear "list what needs to be done for that path" (its transform, tilt, discovery, extraction, viz) calling shared helpers; no flowName conditionals inside the processors. Set C valley implemented cleanly in its processor. Shared helpers extracted first (filter, param'd getFinal etc). Old inline dupe removed as logic moves. docs/PUMP_EXPERIMENT_FLOWS.md updated for the new "array of processors" addition pattern.
    - Execution rules (strict): 
      1. First action: update this TODO.md (done in this edit).
      2. Small targeted search_replace ONLY (one logical piece at a time).
      3. Re-verify with read_file (offset/limit on target region) immediately BEFORE every search_replace.
      4. After every search_replace: immediate forensic read_file on edited blocks + context (confirm no corruption, A/B paths unchanged where applicable, new structure, valley matches spec, braces/scopes).
      5. ./build_app after each logical piece (or group that forms a buildable unit); success locks progress.
      6. On build fail in turn: git checkout . only to restore to last built state (post locked a8f17a0e initially), then tiny fix + re-verify + build. Follow 3-3-3.
      7. Handoff ("results ready to test") only after final build + full verification per plan; then STOP. Feedback = new planning cycle + fresh directive before more source changes.
      8. No .. paths, no deploy, ICRS/raw only, update plan.md only for strategy (already done), sandbox at /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/ if extra artifacts.
    - Phased small steps (per approved plan):
      - Phase 1: Extract 1-2 shared helpers (doCrossScaleRedboxFilter; param-accepting getFinal/takeCrop). Forensic before/after, build.
      - Phase 2: Introduce flowProcessors list (3 lambdas/skeletal) + forEachIndexed dispatch (old body may stay temporarily). Forensic + build (key lock point).
      - Phase 3: Implement Set C processor (full valley: hist 64 on deskewed no-stretch, midpoints via findValleyMidpoints, per-bin threshold/swap/clear pd*/runPaddleDiscovery/filter/version snapshot via getAnns+take/stackVertically/best pd* set for result + only Paddle path). Forensic + build.
      - Phase 4: Move Set B logic into its processor slot, remove B-specific ifs/dupe discovery remnants. Forensic + build.
      - Phase 5: Move Set A, remove remaining conditionals/old inline, handle first/A root snaps (after/hist2) inside A processor or pre-dispatch. Forensic + build.
      - Phase 6: Tiny cleanups, update docs/PUMP_EXPERIMENT_FLOWS.md (array-of-processors pattern + Set C bin-test example). Forensic + build.
      - Final: Full forensic reads of processors/dispatch/valley/helpers, ./build_app, handoff for manual test (golden subset) + report inspection (Set C stacked composites with post-filter reds per version, best result used, A/B 100% unchanged incl. stretch/single-PD/ML-for-A, tilt per set, no ML cols for B/C).
    - Reuses (per plan): OdometerOcrUtils (findValleyMidpoints, automaticContrastStretch (A only), calculateAverageTextAngle, rotate, processPaddleHeatmap, consolidate); kt locals (prepareScale, runDiscoveryPaddle with +1/nonNested redbox from Set J, stackVertically (locked), runPaddleDiscovery (locked)); shared (IcrsMath, merge..., stitch/group/expand/findBest/performHunkRecognition, OcrUtils.takeSnapshot/bitmapToBase64); Pump* types, NativePaddle, BufferSet (all in kt). Do not touch pBuildHtmlHeader/pBuildHtmlRowDynamic.
    - Success = clean build + report matches Set C spec (stacked binarized versions + filtered reds, best for numeric) + A/B identical to pre-refactor + clean processor array with linear per-path bodies + docs updated. Further user feedback starts new turn (re-plan).
    - (Historical valley-insert sub-plan superseded by this approved refactor plan; follow the phases above exactly.)
    - User clarification on "an array of functions that you can iterate over is fine" (2026-06-11): When the message was sent, the thinking was about separate named functions like "setAmlkit", "setApaddle", "setBpaddle" (which would still hard-code the names at the call sites). The intent was to have an array (list) of functions/lambdas (or references to them), paired by index or zip with the flows list, and iterate the array to invoke the appropriate one for each flow. This way the per-flow logic lives inside its entry in the array (clean linear steps for that path, no if (flowName == "Set X") inside the per-path code), and the "which function for which name" is just the array order/index (no ugly hard-coded name strings scattered in the logic or call sites). The current code has the flowProcessors listOf (A/B/C lambdas) + forEachIndexed dispatch; the C entry contains the full valley bin-test. The dispatch site had temp dupe setup code (causing conflicting val declarations) and an early call (before the list val in source, causing unresolved). Cleaning: remove dupe setup/early call (small), add the processor call right after the list def in the per-flow body (after list in source, after setup in execution), guard the old path set for C (temp, with comment; old body to be removed in later phase when the array fully replaces the tangle). This activates the valley for C (composite in column from the processor, path protected). Matches the "array to iterate" to avoid hard-coding names.

- [x] Refactor Agent Workspace Syncing

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
