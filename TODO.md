# TODO

## Active Execution: SW-Aware Thickness Gate in expandBounds
- [ ] `NativeImageUtils.cpp`: add `vSW`/`hSW` params to `nativeExpandBounds`; update `isValleyEB`, add `hasDepthInDir` + `hasDepthVertDir`; update vertical walk and horizontal edge logic
- [ ] `NativeImageUtils.kt`: add `vSW: Float, hSW: Float` to `external fun` and wrapper
- [ ] `ExperimentAlignmentScreen.kt`: pass `vSW_clean, hSW_clean` to `expandBounds` call
- [ ] `./build_app` succeeds
