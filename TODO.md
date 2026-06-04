# TODO

## Active Execution: Native Histogram Rendering (Feature 1)
- [ ] **EXECUTE:** Implement native C++ histogram rendering (no ARGB_8888).
  - [ ] Implement `matToBase64` and `renderHistogramB64` in C++.
  - [ ] Update JNI to `nativeCalculateHistogramB64` returning a Base64 string.
  - [ ] Update Kotlin to call native renderer and display at 1:1 scale.
  - [ ] **VERIFY:** Explicitly read files to confirm logic and 2px tic marks.
  - [ ] Verify build with `./build_app`.

- [x] Refactor Agent Workspace Syncing
- [x] Fix Sandbox Policy Permissions
- [x] Refine update-rules.sh Robustness
- [x] Cleanup Reports on Device
- [x] Enforce Git Reset and Validation Rigor
- [x] Visual Diagnostics & Uniform Scaling
- [x] Character-Aware Expansion & Pipeline Refinement (Set H)
- [x] Fix Pump Experiment NV21 Crops
