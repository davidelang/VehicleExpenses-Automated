# TODO

## Active Execution: Modular JNI Pipeline (Set H Experiment)
- [x] **EXECUTE:** Add 5 new granular JNI functions (zero changes to existing functions).
  - [x] `NativeImageUtils.cpp`: Add `nativeFilterComponents` (in-place component filtering on binary Mat)
  - [x] `NativeImageUtils.cpp`: Add `nativeCalculateHistogramWithThreshold` (histogram + vSW/hSW with explicit threshold)
  - [x] `NativeImageUtils.cpp`: Add `nativeExpandBounds` (vertical walk + initial snapping for pitch detection input)
  - [x] `NativeImageUtils.cpp`: Add `nativeCalculatePitch` (valley detection + pitch/anchorMode/bestShift)
  - [x] `NativeImageUtils.cpp`: Add `nativeAlignGrid` (character-aware horizontal expansion using pitch + vSW mass check)
  - [x] `NativeImageUtils.kt`: Bind all 5 new JNI functions
  - [x] `ExperimentAlignmentScreen.kt`: Replace Set H bin-trials orchestration to use the new modular functions
  - [x] **BUILD:** SUCCESSFUL — tweak-odo-ocr/builds @ a7047806


- [x] Refactor Agent Workspace Syncing
- [x] Fix Sandbox Policy Permissions
- [x] Refine update-rules.sh Robustness
- [x] Cleanup Reports on Device
- [x] Enforce Git Reset and Validation Rigor
- [x] Visual Diagnostics & Uniform Scaling
- [x] Character-Aware Expansion & Pipeline Refinement (Set H)
- [x] Fix Pump Experiment NV21 Crops
