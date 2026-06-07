# TODO

## Active Execution: Physical Data-Driven Filter Fix
- [ ] Tighten rolling filter alignment to 0.1 * vSW and add height gate <!-- id: 9 -->
- [ ] Fix vertical filter logic with deterministic 80% narrowness check and remove percentiles <!-- id: 10 -->

## Completed Tasks
- [x] Revert removal of aggressive 50% padding due to Line 5 regression <!-- id: 8 -->
- [x] Remove aggressive 50% padding from horizontal and vertical wide filters <!-- id: 7 -->
- [x] Implement global matching for horizontal/vertical wide filters with anti-nibbling constraints <!-- id: 6 -->
- [x] Fix data corruption in buffer J alignment by surgicalizing wide filters (fix vertical filter logic) <!-- id: 5 -->
- [x] Exclude `"best_plain_pre_rolling"` from serialization keys in `ExperimentAlignmentScreen.kt` (lines 875 and 1983).
- [x] Extract and display `"best_plain_pre_rolling"` in the `Bin` stage report rendering of `ExperimentAlignmentScreen.kt" (lines 1960-1974).
- [x] Run `./build_app` to compile and create a new git tag.
