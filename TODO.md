# TODO

## Active Execution: Native Histogram & Dual Metadata (Feature 1.3)
- [ ] **EXECUTE:** Implement dual metadata labels for Red and Orange boxes.
  - [ ] Refactor `nativeCalculateHistogramB64` in C++ to return a 2-element ObjectArray (B64, meta).
  - [ ] Update Kotlin JNI binding to handle `Array<Any>?`.
  - [ ] Update HTML report to show metadata labels for both Red and Orange box histograms.
  - [ ] **VERIFY:** Explicitly read files to confirm dual label logic.
  - [ ] Verify build with `./build_app`.

- [x] Refactor Agent Workspace Syncing
- [x] Fix Sandbox Policy Permissions
- [x] Refine update-rules.sh Robustness
- [x] Cleanup Reports on Device
- [x] Enforce Git Reset and Validation Rigor
- [x] Visual Diagnostics & Uniform Scaling
- [x] Character-Aware Expansion & Pipeline Refinement (Set H)
- [x] Fix Pump Experiment NV21 Crops
