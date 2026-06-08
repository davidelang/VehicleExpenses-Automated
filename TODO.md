# TODO

- [x] Refactor Agent Workspace Syncing
    - [x] Update `setup_agent.sh` to remove hard links and protections.
    - [x] Update `update-rules.sh` to push updates and commit to all worktrees.
    - [x] Validate changes by audit and build.
- [x] Fix Sandbox Policy Permissions
    - [x] Update `.gemini/policies/plans.toml` with whitespace tolerance.
    - [x] Update `.gemini/policies/auto-saved.toml` to cleanup mode-based restrictions.
    - [x] Commit and sync rules across all worktrees.
- [x] Refine update-rules.sh Robustness
    - [x] Update `update-rules.sh` to break links and handle read-only targets.
    - [x] Re-run sync and verify inodes.
- [x] Cleanup Reports on Device
    - [x] Modify `fetch_latest_reports.py` to remove old reports from device.
    - [x] Commit changes.
- [x] Enforce Git Reset and Validation Rigor
    - [x] Update `.gemini/policies/auto-saved.toml` to restrict `git reset`.
    - [x] Update `GEMINI.md` to mandate forensic audits.
    - [x] Update `.gemini/system.md` to reflect new rigor.
    - [x] Commit and sync rules across all worktrees.

- [x] Merge and Integrate tweak-odo-ocr
    - [x] Sync PR document to shared dev-ai-interaction/PRs/
    - [x] Merge tweak-odo-ocr branch into master (--no-ff)
    - [x] Verify build success via ./build_app
    - [x] Update global 'works' tag
    - [x] Update ENGINEERING_LOG.md with merge details
    - [x] Verify GEMINI.md mandate update

- [x] Merge and Integrate fix-whitespace
    - [x] Sync PR document to shared dev-ai-interaction/PRs/
    - [x] Merge fix-whitespace branch into master (--no-ff)
    - [x] Verify build success via ./build_app
    - [x] Update global 'works' tag
    - [x] Update ENGINEERING_LOG.md with merge details

## Completed fix-whitespace Tasks
- [x] Whitespace & Style Auto-Fix
    - [x] Reset and Sync branch with master.
    - [x] Re-audit updated codebase.
    - [x] Re-apply automated fixes.
    - [x] Re-run audit to verify zero violations.
    - [x] Run `./build_app` to verify build integrity.

## Completed tweak-odo-ocr Tasks
- [x] Tighten rolling filter alignment to 0.1 * vSW and implement combined bounding box height gate <!-- id: 11 -->
- [x] Revert vertical filter to strict contiguous narrow run logic and remove unauthorized 80% ratio <!-- id: 12 -->
- [x] Tighten rolling filter alignment to 0.1 * vSW and add height gate <!-- id: 9 -->
- [x] Fix vertical filter logic with deterministic 80% narrowness check and remove percentiles <!-- id: 10 -->
- [x] Revert removal of aggressive 50% padding due to Line 5 regression <!-- id: 8 -->
- [x] Remove aggressive 50% padding from horizontal and vertical wide filters <!-- id: 7 -->
- [x] Implement global matching for horizontal/vertical wide filters with anti-nibbling constraints <!-- id: 6 -->
- [x] Fix data corruption in buffer J alignment by surgicalizing wide filters (fix vertical filter logic) <!-- id: 5 -->
- [x] Exclude `"best_plain_pre_rolling"` from serialization keys in `ExperimentAlignmentScreen.kt` (lines 875 and 1983).
- [x] Extract and display `"best_plain_pre_rolling"` in the `Bin` stage report rendering of `ExperimentAlignmentScreen.kt` (lines 1960-1974).
- [x] Run `./build_app` to compile and create a new git tag.
