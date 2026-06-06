# TODO

## Active Execution: Set I Blackout of Large Connected Components
- [x] Implement `nativeBlackOutLargeComponentsH` in `NativeImageUtils.cpp` to zero out pixels of components with width > 25% of the crop width.
- [x] Add JNI declaration and Kotlin wrapper `blackOutLargeComponentsH` in `NativeImageUtils.kt`.
- [x] Update `runBinTrialsPaddle` in `ExperimentAlignmentScreen.kt` to call `blackOutLargeComponentsH` for Set I, retrieve remaining components, and build the enclosing orangebox.
- [x] Draw filtered components in blue and the enclosing orangebox in orange for visualization.
- [x] Verify `./build_app` compilation success.
