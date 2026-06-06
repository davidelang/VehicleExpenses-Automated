# TODO

## Active Execution: First-Class Pre-Rolling Image Snapshot via `takeSnapshot`
- [x] Add `plainPreRollingB64` first-class field to `TrialData` inside `runBinTrialsPaddle`.
- [x] Initialize `tPlainPreRollingB64` variable and capture pre-rolling snapshot using `OcrUtils.takeSnapshot` in Set J block.
- [x] Pass `tPlainPreRollingB64` to `TrialData` constructor calls.
- [x] Render the pre-rolling image in HTML from the `plainPreRollingB64` property.
- [x] Update winner metadata map with `best_plain_pre_rolling`.
- [x] Verify `./build_app` compilation success.
