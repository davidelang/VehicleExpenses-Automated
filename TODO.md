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
- [x] Refine Git Reset and Approval Policy
    - [x] Update `.gemini/policies/auto-saved.toml` with tiered policies (HEAD allowance, 'ask' for other resets, 'deny' for catch-all git).
    - [x] Commit and sync rules across all worktrees.
- [x] Recommend jq for JSON Parsing
    - [x] Update `GEMINI.md` with jq recommendation.
    - [x] Update `.gemini/system.md` with jq recommendation.
    - [x] Commit and sync rules across all worktrees.
- [x] Fix jq and Whitespace Permissions
    - [x] Update `plans.toml` with robust whitespace regex.
    - [x] Update `auto-saved.toml` to allow `jq` in Plan Mode.
    - [x] Commit and sync rules across all worktrees.
- [x] Resolve jq Plan Mode Block
    - [x] Update `auto-saved.toml` with high-priority regex for jq.
    - [x] Commit and sync rules across all worktrees.
- [x] Refactor jq Rule and Whitelist ls
    - [x] Update `auto-saved.toml` to use commandPrefix for jq and add ls.
    - [x] Commit and sync rules across all worktrees.

- [x] Implement Gap-Connecting Filter (Take 3)
    - [x] Update TODO.md
    - [x] Implement simplified nativeConnectSegmentsH in NativeImageUtils.cpp
    - [x] Update JNI Mapping in NativeImageUtils.kt
    - [x] Integrate into ExperimentAlignmentScreen.kt for set_j
    - [x] Forensic Audit and Build Validation


- [x] Implement Gap-Connecting Filter (Take 4 - Welding)
    - [x] Update TODO.md
    - [x] Implement aggressive welding logic in NativeImageUtils.cpp
    - [x] Forensic Audit and Build Validation


- [x] Relax Rolling Filter Horizontal Restriction
    - [x] Update TODO.md
    - [x] Update pairing threshold in NativeImageUtils.cpp to 1.0 * vSW
    - [x] Forensic Audit and Build Validation


- [x] Implement Gap-Connecting Filter (Take 5 - Iterative Precise)
    - [x] Update TODO.md
    - [x] Implement iterative 1px welding logic in NativeImageUtils.cpp
    - [x] Forensic Audit and Build Validation


- [ ] Implement Gap-Connecting Filter (Take 6 - Deep Hook)
    - [x] Update TODO.md
    - [ ] Implement robust deep-hook welding in NativeImageUtils.cpp
    - [ ] Forensic Audit and Build Validation
