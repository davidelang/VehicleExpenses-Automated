# current-state.md (agent-1, branch: fix-pump-experiment)

- Branch: fix-pump-experiment
- Current builds tag / HEAD: fix-pump-experiment/builds @ dedad38e (chore: Synchronize agent rules and infrastructure). Confirmed by git describe --tags and ./get-builds-tag.sh.
- Status: Planning (post major user reset hygiene cycle). Local untracked per-branch state file (must be read first on every fresh launch or new cycle per AGENT_MANDATES hygiene rules + current-state Content and Hygiene Rules).
- Progress: current-state.md revised (pruned + rewritten) to minimal accurate facts+pointers only, matching the post-reset code snapshot and git state. (User performed major reset abandoning 40+ commits to recover from broken app state after prior agent turns; old detailed execution narratives, specific g<sha> tag claims, and END OF EXECUTION TURN handoffs for the abandoned commits no longer exist in history and have been removed here.)

- Code snapshot (forensic from targeted reads/grep on 2026-06-14; ExperimentPumpScreen.kt is 2276 LOC):
  - 5 flows: "Set A", "Set B", "Set C", "Set D", "Set E" (flows list inside runPumpExperiment + PumpBranch tree for reporting).
  - C/E special: valleyPushToPeaks (replaces stretch) + rawC/pushedC + histBeforeC/histAfterC captures + t_valley_ms etc. (if (flowName == "Set C" || flowName == "Set E") ~302).
  - Red dedup (common): doCrossScaleRedboxFilter (PumpHunk version, ~594; exact containment + 3sides+40px qualify) and doCrossScaleRedboxFilterPixel (integer-pixel redRects working lists, ~527; O(2N) sweep X then Y + exact 3sides extend, no repeated ICRS in hot path).
  - C/E post-prune 6 for display/JSON hists: pdHunksRawTotal clear + re-capture on the filtered 6 after common filter ("Per-redbox histograms for *display* + JSON (C/E) now captured post-prune on the filtered 6"; "histograms on line 1 still show 30" addressed; early probe kept only for polarity + n_reds_at_probe ~707+).
  - Dispatch started per PUMP_EXPERIMENT_FLOWS.md intent: flowProcessors = listOf(procA, procB, procC, procD, procE); call flowProcessors[i](...) (~786). procB/C/D/E are empty stub lambdas whose comments claim the target ("B proc ... actual per-set special ... now in extracted helpers called from the thin if (B||D) body"; "if (B||D) and else if (C||E) bodies are now only calls (hoists for C). Mechanical extraction for the tangled ifs complete"; "thin ifs + proc delegates now drive per-set special logic; old body scaffolding cleaned."; "see thin if (B||D) + doBOrD* helpers").
  - In reality, the per-set special behavior (B/D: red-only + retracted+OCR/PD; C/E: valley/3sides/retract/orange/PD/OCR on the 6) is still largely inline in the main per-photo/per-flow loop after the (no-op) proc call. No top-level doBOrD*/doCOrE* thin helper functions with call sites in this snapshot — comments describe intended end-state from the in-progress work at the time of the break.
  - Other present: BufferSet (experimentRecSet1024x48 for OCR + 320x48), ICRS (IcrsMath) + PumpHunk for hunks/rects, performHunkRecognition, getFinal + takeCrop, extensive per-red/scale t_* metadata + hist/JSON builder, stackVertically, doCrossScaleRedboxFilter calls on raw/exp/max, granular timings (t_setup, t_deskew, t_discovery_wrapper, t_filter, t_ocr etc.).
  - PUMP_EXPERIMENT_FLOWS.md documents the desired (array of procs for dispatch; no hard-coded if(flowName) for behavior selection).

- Explicit note on reset: User did major reset (abandoning 40+ commits) because the app was broken and prior turns could not clean it. All the detailed "forensic ... build at gXXXX", "COMPLETE for the approved plan at dev-ai.../pump-experiment-*-plan.md", "optimizations in the split helpers doCOrEPrepareHunksAndValleyInputs", "post-prune re-capture fix for first image", etc. claims from current-state.md (pre this hygiene) referred to commits and intermediate states that no longer exist on this branch. They are removed.

- Open questions / remaining (drawn from last pre-reset user feedback + the actual code at this builds snapshot):
  - First photo/row for C/E never completes in some iterations (post-prune 6 hists re-capture + early probe exposed degenerate 0-size crops or targetW=0 in OCR for bad-aspect tiny derived boxes; guards added in some states for rw/rh <2 and targetW <=0).
  - Full YUV direct for hists visuals (per-red for C/E + before/after per accumulated directives; current is still largely generateHistogramB64 + manual ARGB Canvas loops; comments note "YUV direct... per plan" in places but capture does not fully use BufferSet + compressYuvToBase64 like takeSnapshot).
  - End-to-end pixel primary for red working (good progress with doCrossScaleRedboxFilterPixel + redPixelRects in places for valley/expand; O(N) boundary; no PumpHunk for red working lists inside helpers; but common paths still rebuild pdHunks* lists and some ICRS roundtrips remain for compatibility/anns).
  - Gap between source comments claiming "mechanical extraction complete" / "thin ifs + extracted helpers" / "proc delegates" (and prior plan handoffs) vs. actual stub procs + inline special logic in the loop, and vs. the full list in PUMP_EXPERIMENT_FLOWS.md + D/E opt plans (pixel Rects everywhere for red, 4px + 1024x48 aspect dedicated OCR, prune6 before other processing, YUV, no repeated ICRS, D/E full mirrors, etc.). Some implemented in comments or partial; builder E hists comment acknowledges "per plan".

- Key Decisions (newest): 2026-06-14 post-reset context hygiene (prune inaccurate history; produce accurate snapshot for cheap continuity). Fresh sandbox plan written first as primary deliverable (per mandates).

- See the approved plan at /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/plans/revise-current-state-after-user-reset-20260614-plan.md (standard structure + verbatim STANDARD BLOCK; requires forensic read before/after edits, TODO.md first action, git add current-state + TODO before ./build_app, exact END marker at handoff).

- Last hygiene step: current-state.md pruned + rewritten to match reality; TODO.md updated with hygiene item (first execution action); ./build_app performed to lock.

