# TODO

## Active Execution: Create Set J (Connected Components Speedup)
- [ ] Implement `nativeBlackOutLargeAndSmallComponentsH` in `NativeImageUtils.cpp`.
- [ ] Add JNI declaration and Kotlin wrapper `blackOutLargeAndSmallComponentsH` in `NativeImageUtils.kt`.
- [ ] Add `set_j` configuration to `pipelines` and `useCharAware` checks in `ExperimentAlignmentScreen.kt`.
- [ ] Update `runBinTrialsPaddle` in `ExperimentAlignmentScreen.kt` to handle `pipelineKey == "set_j"`, consolidating scrubbing and negation passes, reusing `vSW_red` and `hSW_red`, and drawing annotations.
- [ ] Verify `./build_app` compilation success.
