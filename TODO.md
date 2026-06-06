# TODO

## Active Execution: Create Set J (Connected Components Speedup)
- [x] Implement `nativeBlackOutLargeAndSmallComponentsH` in `NativeImageUtils.cpp`.
- [x] Add JNI declaration and Kotlin wrapper `blackOutLargeAndSmallComponentsH` in `NativeImageUtils.kt`.
- [x] Add `set_j` configuration to `pipelines` and `useCharAware` checks in `ExperimentAlignmentScreen.kt`.
- [x] Update `runBinTrialsPaddle` in `ExperimentAlignmentScreen.kt` to handle `pipelineKey == "set_j"`, consolidating scrubbing and negation passes, reusing `vSW_red` and `hSW_red`, and drawing annotations.
- [x] Verify `./build_app` compilation success.
