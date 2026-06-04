# TODO

## Active Execution: Modular JNI Pipeline (Set H Experiment)
- [ ] **EXECUTE:** Add 5 new granular JNI functions (zero changes to existing functions).
  - [ ] `NativeImageUtils.cpp`: Add `nativeFilterComponents` (in-place component filtering on binary Mat)
  - [ ] `NativeImageUtils.cpp`: Add `nativeCalculateHistogramWithThreshold` (histogram + vSW/hSW with explicit threshold)
  - [ ] `NativeImageUtils.cpp`: Add `nativeExpandBounds` (vertical walk + initial snapping for pitch detection input)
  - [ ] `NativeImageUtils.cpp`: Add `nativeCalculatePitch` (valley detection + pitch/anchorMode/bestShift)
  - [ ] `NativeImageUtils.cpp`: Add `nativeAlignGrid` (character-aware horizontal expansion using pitch + vSW mass check)
  - [ ] `NativeImageUtils.kt`: Bind all 5 new JNI functions
  - [ ] `ExperimentAlignmentScreen.kt`: Replace Set H bin-trials orchestration to use the new modular functions
  - [ ] **BUILD:** Verify with `./build_app`


- [x] Refactor Agent Workspace Syncing
- [x] Fix Sandbox Policy Permissions
- [x] Refine update-rules.sh Robustness
- [x] Cleanup Reports on Device
- [x] Enforce Git Reset and Validation Rigor
- [x] Visual Diagnostics & Uniform Scaling
- [x] Character-Aware Expansion & Pipeline Refinement (Set H)
- [x] Fix Pump Experiment NV21 Crops
