# TODO

## Active Execution: Use Red Box Stats for Filtering
- [ ] **EXECUTE:** Refactor noise filtering to use unexpanded Red Box vSW/hSW.
  - [ ] Update `NativeImageUtils.cpp`: Calculate Red Box vSW/hSW first and use for Pass A/B/C.
  - [ ] Update `ExperimentAlignmentScreen.kt`: Display filter limits (w<X, h<Y) in diagnostic labels.
  - [ ] **VERIFY:** Confirm Pass A/B/C filtering effectiveness and label accuracy in HTML.
  - [ ] Verify build with `./build_app`.

- [x] Refactor Agent Workspace Syncing
- [x] Fix Sandbox Policy Permissions
- [x] Refine update-rules.sh Robustness
- [x] Cleanup Reports on Device
- [x] Enforce Git Reset and Validation Rigor
- [x] Visual Diagnostics & Uniform Scaling
- [x] Character-Aware Expansion & Pipeline Refinement (Set H)
- [x] Fix Pump Experiment NV21 Crops
