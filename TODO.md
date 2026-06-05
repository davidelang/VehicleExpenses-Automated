# TODO

## Active Execution: Set H Decoupled SW-Aware Valley Snapping & Metadata Retention
- [x] Revert `nativeExpandBounds` and its Kotlin wrapper `expandBounds` back to their original signatures (pre-SW-aware) to preserve Set E.
- [x] Implement `nativeCalculateHistogramWithThresholdH` in C++ with height-bounded peak search `[4, H * 0.30]`. Add Kotlin wrapper.
- [x] Implement `nativeExpandBoundsH` in C++ with lookahead retraction and expansion. Add Kotlin wrapper.
- [x] Implement `nativeCalculatePitchH` in C++ using `hSW * 0.5` valley threshold. Add Kotlin wrapper.
- [x] Implement `nativeAlignGridH` in C++ and Kotlin.
- [x] Update `TrialData` in `ExperimentAlignmentScreen.kt` to store `metadata`.
- [x] Update `runBinTrialsPaddle` to propagate the winner's correct metadata and bypass the redundant diagnostic run at the end.
- [x] Clean up Set H pipeline steps in `ExperimentAlignmentScreen.kt` to call the new `*H` variants.
- [x] Verify `./build_app` succeeds
