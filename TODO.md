# TODO

- [ ] Execution of pump-experiment-make-ml-column-data-driven-20260616-plan.md (VERY FIRST SOURCE EDIT this TODO after re-read current-state.md (hygiene prune first per rules) + FULL plan read (abs) + all prior failure logs read + record lessons on identical first-action skips; compliance re-read of STANDARD BLOCK first 10 lines + last 5 current-state + git --porcelain no .kt + grep TODO pre (no match); Phase 0: narrow forensic read_file on pBuildHtmlHeader (1804/15) + pBuildHtmlRowDynamic (1815/80 focus ML td ~1838) + targeted grep for ML th/td/?: ''/unconditional... ; update TODO first (this) + verifs + baseline git add + ./build_app; then Phases 1-3 ultra-micro per plan (row ML td to data-driven if/else blank, optional header, final forensic); all gates/forensic/grep before/after every edit, git add .kt + current-state.md + TODO.md before each build; scope strictly the ML column emission in pBuildHtmlHeader and pBuildHtmlRowDynamic — ZERO edits to procs or other files or logic; 3-3-3 + anti-doom + literal preflight only; end with exact END marker + results ready (tag) + this plan path)
  - [x] Phase 0: re-read current (prune/roll), plan, TODO, mandates, standard, 6 logs; narrow forensic header 1804/15 + row 1815/80 +1837/5 + procs read-only + targeted grep ML th/td/?: ""/no name ifs; gates 100% (porcelain no kt, TODO pre no match); update TODO first; baseline git add + build success.
  - [x] Phase 1: row ML td to data-driven if containsKey+nonempty (narrow 1837/5 + grep before/after; old ?: gone); no other changes. git add + build.
  - [x] Phase 2: header th read/grep (1808/5); no edit (row fix primary; ths stay for alignment/structure consistency per plan minimal; no column omission). git add + build.
- [ ] Execution of pump-experiment-eliminate-set-name-conditionals-20260616-plan.md (VERY FIRST SOURCE EDIT this TODO after re-read current-state.md (hygiene prune first per rules) + FULL plan read (abs) + all prior failure logs read + record lessons on identical first-action skips; compliance re-read of STANDARD first 10 lines + last 5 current-state + git --porcelain no .kt + grep TODO pre (no match); Phase 0: narrow forensic read_file on report builder (~1814-1891) + the five proc* (~897/1187/1001/1332/723) + hoisted (~591-718) + targeted grep for name==/flowName==/dupe comments... ; update TODO first (this) + verifs + baseline git add + ./build_app; then Phases 1-6 ultra-micro per plan (procB dupe cleanup, procD dupe cleanup, procC/procE dupe cleanups, procA if(flowName) removal, report builder name-if to key-presence, final forensic); all gates/forensic/grep before/after every edit, git add .kt + current-state.md + TODO.md before each build; scope strictly the elimination of set-name conditionals and dupe wrappers in the five procs + report builder — ZERO edits to ExperimentPumpScreen.kt outside the described name-conditional/dupe sites, ZERO to shared helpers or other logic; 3-3-3 + anti-doom + literal preflight only; end with exact END marker + results ready (tag) + this plan path)
  - [x] Phase 0: re-read current-state (hygiene prune), plan, TODO, mandates, standard, 6 logs; narrow forensic baseline reads (report ~1814, procs ~897/1001/1187/1332/723, helpers ~591) + targeted grep name/flow/dupe; gates pass (porcelain clean no .kt, TODO grep no match, compliance re-reads); update TODO first (this); verifs; git add .kt + state + TODO; ./build_app (baseline).
  - [x] Phase 1: procB dupe wrapper removal (narrow 995/10 before/after + grep)
  - [x] Phase 2: procD dupe wrapper removal (narrow 1326/10 before/after + grep)
  - [x] Phase 3: procC/procE dupe cleanups
  - [x] Phase 4: procA if(flowName) removal (narrow 731/5 before/after + grep)
  - [x] Phase 5: report builder name-if -> key-presence (multiple narrow reads + grep)
  - [x] Phase 6: final narrow forensic re-reads + grep zero remaining name/flow/dupe; state+TODO 1-2 facts; git add + build; END marker

- [x] Execution of pump-experiment-eliminate-rectf-icrs-float-math-set-a-20260616-plan.md (VERY FIRST SOURCE EDIT this TODO after re-read current-state.md (hygiene prune first per rules) + FULL plan read (abs) + all prior failure logs read + record lessons on identical first-action skips; compliance re-read of STANDARD first 10 lines + last 5 current-state + git --porcelain no .kt + grep TODO pre (no match); Phase 0: narrow forensic reads of procA (720/180) + ML ICRS site (774) + red rebuilds (839) + getFinal call from A (883) + getFinal/expand/takeCrop sites + targeted grep for IcrsMath/RectF/float... ; update TODO first (this) + verifs + baseline git add + ./build_app; then Phases 1-5 ultra-micro per plan (ML ICRS in procA only, red collection/rebuild for A only, getFinal+expand/takeCrop exercised by A, viz for A, final verif); all gates/forensic/grep before/after every edit, git add .kt+state+TODO before each build; scope strictly Set A only (procA red+ML paths + exercised getFinal/expand/takeCrop for A data) — ZERO edits to ExperimentPumpScreen.kt outside the described A sites, ZERO edits to PumpHunk/runDiscoveryPaddle/other procs/B-E paths/global filters; 3-3-3 + anti-doom + literal preflight only; end with exact END marker + results ready (tag) + this plan path)
  - [x] Phase 4: viz read/grep + state+TODO update (A final crops integer clean)
  - [x] Phase 5: final forensic re-reads + grep zero bad ICRS/RectF/float in A red+ML+getFinal/expand/takeCrop; state+TODO; final build + END marker (tag) + plan path.

- [ ] Execution of optimize-deskew-and-valley-lut-plan.md
  - [ ] Phase 1: Update TODO.md (Completed)
  - [ ] Phase 2: Implement Valley Push Native LUT in OdometerOcrUtils.kt
  - [ ] Phase 3: Add split deskew functions to OdometerOcrUtils.kt
  - [ ] Phase 4: Update ExperimentPumpScreen.kt to call new deskew functions
  - [ ] Phase 5: Verification & Build

- [x] Execution of pump-experiment-reorder-valley-push-20260615-plan.md
  - [x] Phase 0: Preflight & Audit
  - [x] Phase 1: Reorder Set C (`procC`)
  - [x] Phase 2: Reorder Set E (`procE`)
  - [x] Phase 3: Final Verification

- [x] Execution of pump-experiment-takesnapshot-buffer-target-size-logging-20260615-plan.md
- [x] Execution of pump-experiment-fix-coordinate-system-error-plan.md
- [x] Safe leading env assignment prefix support
- [x] Refactor Agent Workspace Syncing
- [x] Fix Sandbox Policy Permissions
- [x] Refine update-rules.sh Robustness
- [x] Cleanup Reports on Device
- [x] Enforce Git Reset and Validation Rigor
- [x] Refine Git Reset and Approval Policy
- [x] Recommend jq for JSON Parsing
- [x] Fix jq and Whitespace Permissions
- [x] Resolve jq Plan Mode Block
- [x] Refactor jq Rule and Whitelist ls
- [x] Fix Master Agent 'works' Tag Violation
