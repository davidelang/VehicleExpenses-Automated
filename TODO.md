# TODO

## Active Execution: Clean Primary Image Before Detect/Histogram/Pitch
- [ ] **EXECUTE:** Replace Step 4 cleaning thumbnail block with in-place clean + re-detect + re-histogram
  - [ ] `ExperimentAlignmentScreen.kt`: Apply modes 1 & 2 in-place on `odoBuffer.p.mat`
  - [ ] `ExperimentAlignmentScreen.kt`: Re-detect on cleaned image → `tRawB2`
  - [ ] `ExperimentAlignmentScreen.kt`: Re-histogram on cleaned image → `vSW_clean`, `hSW_clean`
  - [ ] `ExperimentAlignmentScreen.kt`: Steps 5-7 use cleaned image and fresh metrics throughout
  - [ ] `ExperimentAlignmentScreen.kt`: Remove `cleanB64s`, `charaware_img_a/b/c` from metadata and HTML
- [ ] **BUILD:** Run `./build_app`
- [ ] **TEST:** Hand off to user for manual validation
- [ ] **VALIDATE:** Review logs to confirm no crash and improved pitch detection
