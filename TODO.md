# TODO

## Active Execution: Remove Set H & Set I, Cleanups, and Redefine Failing Subset
- [x] Redefine `FAILING_SUBSET` with the 11 Set J failures in `ExperimentAlignmentScreen.kt`.
- [x] Remove Set H and Set I configuration and conditional blocks in `ExperimentAlignmentScreen.kt`.
- [x] Remove unused JNI wrappers (`expandBounds`, `calculatePitch`, `alignGrid`, `expandBoundsH`, `calculatePitchH`, `alignGridH`, `blackOutLargeComponentsH`) in `NativeImageUtils.kt`.
- [x] Remove unused native implementations in `NativeImageUtils.cpp`.
- [x] Remove before-cleaning annotated image and orange box histogram generation from `runBinTrialsPaddle` in `ExperimentAlignmentScreen.kt`.
- [x] Verify `./build_app` compilation success.
