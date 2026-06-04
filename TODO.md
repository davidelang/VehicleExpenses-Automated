# TODO

## Active Execution: Fix Histograms via Kotlin Renderer Reversion
- [ ] **EXECUTE:** Restore reliable histogram rendering by reverting to Kotlin-side canvas drawing.
  - [ ] Update `NativeImageUtils.cpp`: Remove C++ rendering, return raw `hArr`/`vArr` (128 elements).
  - [ ] Update `NativeImageUtils.kt`: Handle the raw array return in JNI, store in metadata map.
  - [ ] Update `ExperimentAlignmentScreen.kt`: Implement 128-bucket `generateDualHistogramB64` and update HTML loops.
  - [ ] **VERIFY:** Confirm histogram visibility in generated HTML reports.
  - [ ] Verify build with `./build_app`.

- [x] Refactor Agent Workspace Syncing
- [x] Fix Sandbox Policy Permissions
- [x] Refine update-rules.sh Robustness
- [x] Cleanup Reports on Device
- [x] Enforce Git Reset and Validation Rigor
- [x] Visual Diagnostics & Uniform Scaling
- [x] Character-Aware Expansion & Pipeline Refinement (Set H)
- [x] Fix Pump Experiment NV21 Crops
