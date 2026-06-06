# TODO

## Active Execution: Pass A/B/C Image Elimination & Reusable JSON Buffer
- [ ] Completely remove Pass A/B/C image generation, filtering, and base64-encoding from `NativeImageUtils.cpp` (nativeExpandByCharacterAwareDiagnostic) and shrink returned array size from 9 to 6.
- [ ] Update `NativeImageUtils.kt` to handle 6 elements and remove `charaware_img_a/b/c` metadata properties.
- [ ] Implement JSON helper functions (`appendJsonValue`, `appendJsonObject`, `appendJsonArray`, `escapeJsonString`) in `ExperimentAlignmentScreen.kt`.
- [ ] Integrate pre-allocated 16MB StringBuilder in `ExperimentAlignmentScreen.kt`'s `runExperimentAlignment`.
- [ ] Implement JSON helper functions in `ExperimentPumpScreen.kt`.
- [ ] Integrate pre-allocated 16MB StringBuilder in `ExperimentPumpScreen.kt`'s `runExperimentPump`.
- [ ] Verify `./build_app` compilation success.
