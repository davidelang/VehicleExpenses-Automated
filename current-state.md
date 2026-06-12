# current-state.md (agent-1, branch: fix-pump-experiment)
- Agent: Grok (this worktree)
- Current cycle: Execution of user-approved plan (primary artifact at sandbox path below). Started after plan review/approval.
- Approved plan (primary, per AGENT_MANDATES): /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/pump-experiment-set-b-c-ocr-reporting-20260612-plan.md
  - Changes:
    1. Numeric/"digits" detection in Set B and Set C OCR reporting must support '.' (for decimal pump cost/volume readings). Leverage existing ALLOWED_DIGITS_DECIMAL in NativePaddleEngine.
    2. In ocr reports (pd_ocr_html text + associated visual boxes for B/C), do not show any box whose result has fewer than 2 digits.
- Background memory (user directive): Prior research on big/little filter (object detection on binarized images for quick fill odo and Set C): core call is `cv::connectedComponentsWithStats(*mat, labels, stats, centroids, 8)` (labels = per-pixel int32 component IDs; stats CV_32S with CC_STAT_* for w/h/left/top/area). See nativeBlackOutLargeAndSmallComponentsH (first CC + large black via labels runs + refresh CC + small cull) and nativeFindAllComponentsH in app/src/main/cpp/NativeImageUtils.cpp; wrappers in NativeImageUtils.kt; calls in OcrHarness.kt:247 and ExperimentPumpScreen.kt (Set C block). Requires prior binarization. Never start new work from plans/ or historical-plans/.
- Status: Execution COMPLETE. All phases followed.
  - Engine: recognizeNumericDecimal added + built (uses ALLOWED_DIGITS_DECIMAL).
  - Screen: All 4 numeric calls in B/C digits passes switched to decimal version.
  - Filter: ocrLinesB and ocrLinesC now only include boxes with >=2 digits (using .count { isDigit() } >= 2, reusing existing idiom). Comments updated.
  - Builds: Multiple successful (tags updated: fix-pump-experiment/builds at commits g0e71f4dd, g5550855e, final run).
  - Forensic: Multiple read_file before/after every edit + final re-verification reads confirmed exact changes.
  - Verification: Code + build inspection confirms . support in numeric for pump reports and filtering of <2 digit boxes from ocr reports. Full UI run of experiment (to inspect generated HTML) to be done by user via app "Run Limited Experiment (Golden Subset)" or full (photos with decimals will show the improvements in B/C ocr html sections).
- Local plan hygiene: Harness sessions plan.md kept minimal (short log only). Primary plan at dev-ai sandbox path. current-state.md and TODO.md updated.
- Notes: Turn handed off. See approved plan for details. CC memory note preserved in plan for future. After handoff: any feedback starts new planning cycle (new plan document + explicit Directive before edits).
- Last ./build_app: final clean run (no new tracked changes; tag reference updated to current). Version example from prior: fix-pump-experiment-start-60-g5550855e
- Handoff: Changes for the approved plan are ready for testing. **END OF EXECUTION TURN**

# New cycle start (2026-06-12)
- New turn: Starting fresh planning for blue box retract logic and nested red box appearance in Set C (and B) OCR viz/reporting.
- User directive: blue boxes should retract to tight fit around the text (expand out, but retract back in when hitting limit with no text until find text).
- Concern: many blue boxes making it look like lots of (nested) red boxes; thought nested reds were eliminated.
- Task: Check the specified alignment_results JSON for Set C red boxes and nesting; report findings; create plan to fix the blue retract and address red nesting appearance in reports.
- First action: read local current-state (done); explored code for blueRects derivation and red filter; located and inspected the JSON (it appears to have error/stacktrace structure rather than full pump results data; analyzed source logic instead).
- Plan will be written to dev-ai-interaction/ per rules.
- Status: Execution COMPLETE for this approved plan.
- Forensic reads (phase 1/10): done on filter call, B/C blocks, blue derivation, Native expandByUniformity.
- JSON: error format (no data); source analysis confirmed shared filter applies to B (not removed); explicit added for B and C.
- Changes implemented: explicit nested filter calls/comments in Set B and C blocks; blue retract using expandByUniformity (retracted rects for anns/OCR in B from exp, C from CC union) for tight text fit (retract on no content).
- Builds: milestone + final successful (tag references updated, e.g. g3b3d4ab0).
- Forensic final reads: confirmed all changes (retracted lists, explicit filters, retract logic with expandByUniformity).
- Verification: code + builds confirm de-nested reds (explicit filter) and retracted tight blues for B and C reports. Full UI run/inspection of experiment reports (B and C PD images + ocr html) to be done by user with relevant photos to visually confirm (per plan phase 12).
- Handoff: see below. After this turn's final build + "results ready to test" + END marker: complete. Feedback = new planning cycle (new plan + Directive).
- See approved plan path for full details. Previous ocr plan, CC memory, and this cycle's planning/execution notes in state.

# Additional user feedback during review of the red-nested-filter implementation (2026-06-12)
- "what '2px inset pixel-space check' even if they perfectly overlap one should be removed, no spacing needed. did you invent a requirement that wasn't in the plan?"
- "the purpose of filtering the nested red boxes out is that they are redundent. It makes zero sense to filter them out and then turn around and sue the filterd out red boxes to create blue boxes. If they are filtered out, they should be removed as redundent and not exist after the filter"
- Response/fix applied:
  - The 2px inset (and comment "Small inset tolerance in final pixel space (cross-scale nesting can appear after mapping)") was *pre-existing code* (ported from alignment Set J +1/inset de-nest inside runDiscoveryPaddle per scale); not invented for this plan. The approved plan referenced "the inset contains filter" and explicitly allowed "strengthen if needed (e.g. adjust inset, or use a more robust overlap check)".
  - Updated filter to exact containment (no inset/spacing). For perfect overlaps/duplicates: keep one (first in encounter order), drop redundants. Changed to sequential "kept" accumulation (original one-pass against full original list could drop *all* for mutual cases like identical rects).
  - Added `doCrossScaleRedboxFilter` calls on pdHunksExpTotal and pdHunksMaxTotal immediately after the one on rawTotal (global, before the if-Set-B/C viz blocks). + comment: "Propagate the dedup... Filtered reds must not 'exist' for downstream blue derivation." This ensures that once a raw red is filtered out as redundant, its per-scale expansions (exp/max) are also removed, so blues/oranges (in Set B from exp/max, in Set C derivation from post-filter redBoxes + CC hunks) are only created from the kept non-redundant reds.
  - The explicit re-filter on raw in B/C blocks (added earlier per plan) remains for the local redBoxes/redAnns vars; now the derived lists are also cleaned.
- Comments updated in filter and call site to reflect exact user-specified intent.
- Forensic re-reads of edited sections confirm.
- This addresses the redundancy purpose directly. The filter now truly removes redundants completely so they do not "exist after the filter" for blue creation.
- Next: ./build_app (milestone), final forensic, handoff for user to re-inspect the pump report JSON + reports for Set B/C (should now have fewer spurious blues from cleaned redundants; red count after filter should match expectation of ~5-10 for cases with overlap).
- Builds will create new tag. END after user verification.

# New cycle start (2026-06-12) — Valley histogram push for Set C (new turn, new plan)
- This is a new turn, new plan (user: "This is a new turn, new plan for set C, instead of the current histogram contrast stretch, I want to display the raw image, then use the histogram to look for the center of valleys and push the values out from there until they hit a peak. the result will not be binarization, but it will be an image that has only a small number of brighness values. display that image and the before and after histograms in the set C column").
- First actions (per mandates + AGENTS.md): read new_grok_agent_prompt + AGENT_CONTEXT.md + GROK.md + AGENT_MANDATES.md + current-state.md (this file, done at cycle + re-verified before edit). Entered plan mode. Researched (reads/greps of critical ExperimentPumpScreen.kt + OdometerOcrUtils.kt only; no TODO.md; no historical-plans/; no sessions/ plan.md). Produced Mandate Acknowledgment (in thinking + process). Wrote *fresh* sandbox plan as first concrete deliverable (absolute path below).
- Primary plan artifact (per AGENT_MANDATES "Sandbox Plan File as the Primary..." + new_grok_agent_prompt): /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/plans/pump-experiment-set-c-valley-histogram-push-20260612-plan.md
  - Standard structure followed (Context, Recommended Approach, Critical Files, Reuse, Phased Execution with forensic+build, Verification, Handoff).
  - Key: new valleyPushToPeaks in OdometerOcrUtils (reuses findValleyMidpoints + 64-bin/smooth/peak patterns from automaticContrastStretch); replace stretch for C only + capture raw/pushed + before/after hists to branch.images; special-case Set C td in pBuildHtmlRowDynamic to render the 4 visuals (raw, pushed-few-brightness, hist before, hist after) inside column (framing existing PD/ocr). No changes to B/A or core blue/CC/polarity/filter logic beyond the transform swap.
- Local state updated (this edit) for per-branch continuity. Harness sessions plan.md untouched (short log only; forbidden to read/reference per rules).
- Background memory preserved: big/little filter (cv::connectedComponentsWithStats via blackOutLargeAndSmall + findAll + rolling in NativeImageUtils.cpp + wrappers; used in Set C CC path); OCR decimal (recognizeNumericDecimal + ALLOWED_DIGITS_DECIMAL) + >=2 digit filter in ocrLinesB/C; blue retract (expandByUniformity on bin/workspace); red nesting (exact no-inset sequential doCrossScaleRedboxFilter on raw+exp+max; filtered reds do not exist for blue derivation).
- Status: Planning phase complete. Plan written to designated sandbox path. Awaiting explicit user approval (magic phrasing naming the exact dev-ai-interaction/plans/...-plan.md path + directive to proceed). No tracked source edits performed (plan mode + exemptions used only for sandbox plan + current-state.md).
- After approval: re-read *exactly* the designated plan file first, then current-state.md, then exec phases (TODO first, forensic every edit, milestone ./build_app, final handoff with **END OF EXECUTION TURN** + "results ready to test"). Feedback after handoff = brand new turn (new plan file).
- Plan name uses 20260612 per today's date + "new plan" directive; fresh file (not revision of any prior).
