# TODO

## Active Execution: Set E Stability & Set H Crop Histograms
- [ ] **EXECUTE:** Fix grayscale contamination in binarization trials & implement ROI crops for histograms
  - [ ] `ExperimentAlignmentScreen.kt`: Update `runBinTrialsPaddle` signature to take `masterBuffer` and `vehicleId`
  - [ ] `ExperimentAlignmentScreen.kt`: Pull fresh raw grayscale crop from `masterBuffer` at the start of each trial iteration
  - [ ] `ExperimentAlignmentScreen.kt`: Binarize and flip raw grayscale at the winner threshold upon loop completion to set the winning state correctly
  - [ ] `ExperimentAlignmentScreen.kt`: Define ROI crops for red (and orange) boxes to run histograms on crops instead of full mats
  - [ ] **BUILD:** Verify compile and run via `./build_app`



- [x] Refactor Agent Workspace Syncing
- [x] Fix Sandbox Policy Permissions
- [x] Refine update-rules.sh Robustness
- [x] Cleanup Reports on Device
- [x] Enforce Git Reset and Validation Rigor
- [x] Visual Diagnostics & Uniform Scaling
- [x] Character-Aware Expansion & Pipeline Refinement (Set H)
- [x] Fix Pump Experiment NV21 Crops
