# TODO

## Active Execution: Use Red Box Stats for Filtering
- [/] **EXECUTE:** Refactor noise filtering to use unexpanded Red Box vSW/hSW.
  - [x] Update `NativeImageUtils.cpp`: Implement `maxVal` cap in `getPeak` lambda to prevent vertical stroke peaks.
  - [x] Update `NativeImageUtils.cpp`: Return `vSW_red` and `hSW_red` at indices 13 and 14 of `s[16]`.
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
