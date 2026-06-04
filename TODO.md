# TODO

## Active Execution: Native Histogram Rendering (Feature 1.2)
- [ ] **EXECUTE:** Fix Base64 encoder logic and switch to PNG.
  - [ ] Rewrite `base64_encode` in C++ to use independent loop variables.
  - [ ] Update `matToBase64` to use lossless PNG encoding.
  - [ ] Update Kotlin HTML tags to `image/png` MIME type.
  - [ ] **VERIFY:** Explicitly read `NativeImageUtils.cpp` to confirm the `k` index fix.
  - [ ] Verify build with `./build_app`.

- [x] Refactor Agent Workspace Syncing
- [x] Fix Sandbox Policy Permissions
- [x] Refine update-rules.sh Robustness
- [x] Cleanup Reports on Device
- [x] Enforce Git Reset and Validation Rigor
- [x] Visual Diagnostics & Uniform Scaling
- [x] Character-Aware Expansion & Pipeline Refinement (Set H)
- [x] Fix Pump Experiment NV21 Crops
