# TODO

## Active Execution: Revert Binning and Restore Diagnostic Images
- [ ] **EXECUTE:** Restore Pass A/B/C images and revert histogram binning to 2.
  - [ ] Update `ExperimentAlignmentScreen.kt`: Revert `binSize` to 2 in `generateDualHistogramB64`.
  - [ ] Update `ExperimentAlignmentScreen.kt`: Restore Pass A, B, C images in the HTML report.
  - [ ] **VERIFY:** Confirm Pass A/B/C visibility and bin-2 histogram display in HTML.
  - [ ] Verify build with `./build_app`.

- [x] Refactor Agent Workspace Syncing
- [x] Fix Sandbox Policy Permissions
- [x] Refine update-rules.sh Robustness
- [x] Cleanup Reports on Device
- [x] Enforce Git Reset and Validation Rigor
- [x] Visual Diagnostics & Uniform Scaling
- [x] Character-Aware Expansion & Pipeline Refinement (Set H)
- [x] Fix Pump Experiment NV21 Crops
