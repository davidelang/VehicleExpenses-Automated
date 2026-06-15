# current-state.md (agent-1, branch: fix-pump-experiment)

- Branch: fix-pump-experiment
- Current builds tag / HEAD: fix-pump-experiment/builds @ d36c6e8f . Confirmed by git describe --tags and ./get-builds-tag.sh.
- Status: Execution for approved plan (post hygiene). Local untracked per-branch state file (read first on cycle per AGENT_MANDATES).
- Progress: Execution started for granular-dupe-retirement plan. (Older facts rolled per hygiene; see plan file + git since 3f5f0c1c.)
- Active plan: /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/plans/pump-experiment-duplicate-granular-retirement-20260615-plan.md (granular retirement + anti-doom rule; 3-3-3 policy)
- Key Decisions (recent): 2026-06-15 Execution start: re-read current-state (hygiene prune/collapse), read full plan + failure log; TODO.md first source edit (new top item recording plan+granular+3-3-3+anti-doom). Pre-turn builds: 3f5f0c1c.
- Step 2 (Phase 0 forensic): narrow reads (offset/limit) of proc stubs (lines ~759), dispatch ~786, pre-proc C/E ~708, full remnant ~790+ (C_old else + doBOrD*/doCOrE* defs ~1039/1186 inside), t* decls (tFlowStart~281 tDeskew~329 tDiscoveryWrapper~785 etc), local funs (stack~355 getFinal~389 runPaddle~431), hoisted comments. Anti-doom: reviewed failure log (repeated scope/hoist/suspend/compile errors without adaptation on retirement).
- Step 3 (Phase 0.3 build): edited suspend sigs on procA-E (forensic narrow read+grep before/after confirmed "suspend ("); git add kt+states+TODO + build_app success per plan (before every build). New builds tag 7d50a6b2. 
- Step 4 (Phase 0.4 hoist): inserted Phase 0 hoist block + vars (tDiscoveryWrapperStart, tProbeStart etc) before proc defs; changed tDiscovery decl to reassign (forensic narrow read+grep before/after + plan comment). 1-2 lines/step. Builds still 7d50a6b2 (hoist pre-build).
- Step 5 (Phase 0.4 build): git add kt+current+TODO + build_app after hoist edit; success. New builds tag d36c6e8f.
- Step 6 (Phase 0.5 fun hoist): hoisted getAnns fun def early before procA (small; forensic read/grep before/after; plan comment; doBOrD*/doCOrE* left per "if not hoisted, include copies at dupe"; other stack/runPaddle/getFinal already early). 
- Step 7 (Phase 0.5 build): git add kt+current+TODO + build_app after getAnns hoist edit; success. New builds tag 22e843db.
- Step 8 (Phase 0.6 other vis): hoisted processedScales var early before procs (forensic before/after; reinit in remnant; other lists like pdHunks*/mlBlocksRaw/scales/experimentRec* already early or top). 
- Step 9 (Phase 0 final build + confirm): git add + build_app success after last vis hoist. New builds tag 374bd8d5. Narrow read + grep confirm: all key names in to-be-dupe logic (scales, pdHunks*Total, mlBlocksRaw, processedScales, experimentRec*, getAnns, doCross*, stackVertically, runPaddleDiscovery, getFinal, t* hoisted, getAnns) declared/hoisted before proc= positions (visible to procs post-retirement of remnant).
- Phase 0 prep complete; all hoists/suspends upfront per failure lessons. Builds tag 374bd8d5. Ready Phase 1 dupe.
- Phase 1 procA: forensic reads/grep of body/preproc/remnant/closed-over + procA stub. Replaced stub with full dupe + val flowName="Set A" + aliases. Forensic after. Build failed (unresolved do* in pasted B/C branches; symptom per failure log scope/hoist). Anti-doom followed: re-read log, listed 3 different minimal repairs, applied 1 (excise B/C from procA paste only). Forensic after repair. git add + build success. New tag 5b13223e.
- Phase 2 procB: narrow forensic read of procB stub + B-specific (if B in remnant ~1706, doBOrD defs). Grep closed-over. Mechanical replace procB stub with full dupe + val flowName="Set B" + aliases + B branch + copies of doBOrD* (to resolve). Forensic read/grep after (full dupe + "Set B" confirmed in procB). 
- Phase 2 procB build: glue/trim repair (anti-doom, different: glue vals + excise C + trim ocr in copy); build success after. New tag f7b38fc7. Ready procC.
- Phase 2 procC: forensic read stub + C branch. Replace with val flowName="Set C" + dupe + doC copy (abbrev). Repair: reorder copy before call. Build success. New tag 148e5df6.
- Phase 2 procD: replace with val flowName="Set D" + dupe (B mirror). Repair: reorder doB copies before if. Build success. New tag a83c0f14.
- Phase 2 procE: replace with val flowName="Set E" + dupe (C mirror). Repair: reorder doC copy before if. Build success. New tag 65add516.
- All 5 procs duplicated with own val flowName="Set X" at top + logic. Builds at each. Ready Phase 3 post-dupe prep. Current builds 65add516.
- Phase 3: narrow reads of 5 procs + remnant; minimal prep comment added ("post-dupe prep complete; ... ready for granular retirement"). Forensic + build success. New tag ee3202a5. Ready Phase 4 granular retirement.
- Phase 4 tiny 1: disabled outer if (C_old) to false (forensic before/after + grep). git add + build success. New tag b67778db.
- Re-exec failure rolled: duplication re-exec (prior plan) cancelled by harness on repeated Phase 4 errors (no new repairs); reverted to pre-dupe clean. See dev-ai-interaction/implementation-failure-logs/2026-06-15-duplication-reexec-doom-loop-failure.md (reviewed at start per new plan rule). Ready for granular steps.

