# TODO

## Active Execution: First-Class Pre-Rolling Image Snapshot via `takeSnapshot`
- [ ] Add `plainPreRollingB64` first-class field to `TrialData` inside `runBinTrialsPaddle`.
- [ ] Initialize `tPlainPreRollingB64` variable and capture pre-rolling snapshot using `OcrUtils.takeSnapshot` in Set J block.
- [ ] Pass `tPlainPreRollingB64` to `TrialData` constructor calls.
- [ ] Render the pre-rolling image in HTML from the `plainPreRollingB64` property.
- [ ] Update winner metadata map with `best_plain_pre_rolling`.
- [ ] Verify `./build_app` compilation success.
