# TODO

## Active Execution: Set I Blackout of Large Connected Components
- [ ] Implement `nativeBlackOutLargeComponentsH` in `NativeImageUtils.cpp` to zero out pixels of components with width > 25% of the crop width.
- [ ] Add JNI declaration and Kotlin wrapper `blackOutLargeComponentsH` in `NativeImageUtils.kt`.
- [ ] Update `runBinTrialsPaddle` in `ExperimentAlignmentScreen.kt` to call `blackOutLargeComponentsH` for Set I, retrieve remaining components, and build the enclosing orangebox.
- [ ] Draw filtered components in blue and the enclosing orangebox in orange for visualization.
- [ ] Verify `./build_app` compilation success.
