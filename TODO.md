# TODO

- [x] Safe leading env assignment prefix support ("KEY=val cmd") for all already-allowed bash commands + promote agent-1 "don't ask again" commands to global checked-in config (plan approved 2026-06-13)
  - [x] Forensic read of .grok/config.toml and .grok/hooks/plan-mode-hard-stops.js (before edits)
  - [x] Add stripLeadingAssignments + getLastPipeBase + generalized early-allow logic to the hook (so prefixed forms of blessed bases like jq/ls/git/echo/find/build_app/etc. no longer prompt; blocks loopholes by checking the first non-assignment token). Python3 * deliberately never included (user confirmed too dangerous).
  - [x] Update comments in .grok/config.toml documenting the prefix form + hook normalization. Added narrow patterns for confirmed pager items (adb logcat* for reads [user: "adb logcat is reading data, that is allowed"], echo *, find *, true).
  - [x] Selectively promote confirmed items from agent-1/permission_grok-pager.toml allowed_bash_commands (adb logcat for reads [confirmed allowed by user], echo *, find *, true, and specific git describe/tag--list if not redundant) as narrow patterns in root config.toml. Do **not** add any python3 * (user confirmed: too dangerous; use dedicated helpers only)
  - [x] Run ./update-rules.sh (from orchestration root) — synced hook + config (and run-* launchers) to agent-1/ and master/ with commits.
  - [x] ./build_app (create builds tag) — commit for the changed files succeeded; full gradle/app build skipped (not applicable in pure orchestration root with no gradlew/app tree); orchestration/builds tag force-updated at the resulting HEAD via get-builds-tag.sh + git tag -f (per AGENT_MANDATES preflight and plan requirements).
  - [x] Update this TODO, output **END OF EXECUTION TURN** marker + "results ready to test"
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
- [x] Fix Master Agent 'works' Tag Violation
    - [x] Update `GEMINI.md` with Safety Override clause.
    - [x] Update `MASTER_AGENT_MANDATE.md` with strict merge template.
    - [x] Commit and sync rules across all worktrees.

# Meta plan (approved 2026-06-12 on orchestration branch): Robust Plan/Execute Cycle
- Primary deliverable of every planning phase must be a fresh, clean task-specific plan document written to dev-ai-interaction/plans/ (e.g. <task>-<date>-plan.md) using the standard structure (Context, Recommended Approach, Critical Files with paths, Reusable utilities with exact locations, Phased steps with forensic+build, Verification). User approves by explicit path reference + directive (e.g. "approved the plan at dev-ai-interaction/XXX-plan.md for the following..."). Agent must re-read exactly that designated file as first step in execution.
- Harness ~/.grok/sessions/.../plan.md (the process log whose path appears in plan-mode reminders) is *not* the work plan. Use it only for short log entries referencing the sandbox plan path. At start of new cycle (or post-handoff), roll any prior superseding/historical bulk to dev-ai-interaction/historical-plans/harness-plan-archive-....md first, then prepend only the minimal current-cycle header (prepending to, not even superseding, old content).
- Subagent separation supported and recommended for complex work: in planning (main stays in plan mode), optionally spawn_subagent (type "plan" or via "design" skill) with narrow prompt whose *only* job is research + write one new sandbox plan file + return its path. After user approval of that specific sandbox plan path, main (or spawned "implement"/execution subagent with the approved plan content injected) executes precisely, with TODO first, forensic read before/after every edit, ./build_app, exact END OF EXECUTION TURN marker + "results ready to test", then complete stop. Subagents blocked in plan mode per existing rules.
- Post-handoff / new cycle start: Happy path is exit the CLI and relaunch via run-grok (forces fresh Mandate report + enter_plan_mode + STOP per the launcher + new_grok_agent_prompt). Fallback: agent at every handoff writes short dev-ai-interaction/.post-handoff-gate.txt; user does `cat dev-ai-interaction/.post-handoff-gate.txt` then appends request (one-liner ritual). The new tracked MULTI_AGENT_USER_INSTRUCTIONS.md (at root, synced by update-rules.sh to master/agent worktrees) is the authoritative human reference.
- In new_grok_agent_prompt Mandate report: agent must confirm "I created/wrote the task plan to dev-ai-interaction/<name>.md", "harness session plan kept concise with roll if needed", "post-handoff will use relaunch or wrote the short gate file".
- See approved plan (this session's harness plan.md + the produced MULTI_AGENT_USER_INSTRUCTIONS.md) for full details, critical files, and verification. Pre-turn state: orchestration branch; session plan at /home/dlang/.grok/sessions/%2Fhome%2Fdlang%2Fgit%2FVehicleExpenses-automated/019ebbe2-611e-77e1-a312-f7cd1412096e/plan.md; no app source changes in scope.

# Meta plan (approved 2026-06-12 on orchestration branch): Interactive Strategic Planning + Continuity
- Stable orientation facts live in the tracked `project-facts.md` at each worktree root. It must contain only layout / "where things live" facts that remain true after branch merge + new worktree for different effort. Agents read it first on launch/new cycle (in addition to the user-designated sandbox plan).
- Planning phase is the **interactive strategic layer**: user may give rich problem descriptions, direction, and iterative feedback. Agent responds by revising the *plan document* in dev-ai-interaction/ (not source). Only after the user issues an explicit magic approval phrasing naming a specific sandbox plan path does mechanical execution begin.
- Update bootstrap files, AGENT_MANDATES.md, AGENTS.md, MULTI_AGENT_USER_INSTRUCTIONS.md, .grok/config.toml, and .gitignore to reflect that `project-facts.md` is now tracked, planners may edit it in plan mode (alongside TODO.md), and it is strictly limited to stable facts (no branch/tag/current-plan). The untracked current-state.md name is legacy.
- Primary plan documents stay in the sandbox; project-facts.md is for enduring location facts only.
- See the approved plan at dev-ai-interaction/interactive-strategic-planning-and-continuity-plan.md for full details, critical files (new_grok_agent_prompt, AGENT_MANDATES.md, AGENTS.md, MULTI_AGENT_USER_INSTRUCTIONS.md, .gitignore), and verification. Pre-turn state: orchestration branch; follows handoff from the previous robust cycle enforcement plan; no app source changes.

- [x] Executing plan dev-ai-interaction/plans/add-restore-data-flag-to-deploy-20260625-plan.md: add `--restore-data` standalone restore + source-zip backup/restore in `deploy`.
- [x] Executing plan add-install-data-for-selectable-backup-restore-20260625-plan.md: `--install-data` for picking any backup dir + push to devices.

- [x] De-abstract Set G for Quick Fill: copy pump cost/vol logic inline into OcrHarness.runPumpCostVolPipeline (no runSetGCostVolExtraction / skipDeskew); plan: dev-ai-interaction/plans/de-abstract-set-g-for-quick-fill-copy-logic-20260627-plan.md
- [x] Executing plan dev-ai-interaction/plans/fix-arm-direct-int8-write-no-copy-plus-proper-arm-emulator-20260630-130648-plan.md: ARM bindOutputInt8 before run (no copyTensor); x86 helper unchanged; emulator-5556 arm64 AVD setup doc.
- [x] Executing plan dev-ai-interaction/plans/fix-arm-phone-crash-after-direct-bind-plus-x86-zero-20260630-150000-plan.md: separate ARM input/output buffers; int u_threshold CC; zero-safe post-process + pump guards.
- [x] Executing plan dev-ai-interaction/plans/fix-uint8-conversion-for-int8-buffer-20260630-151214-plan.md: long-lived buf holds uint8 0-255; ARM post-run ^128 convert; read without xor.
- [x] Executing plan dev-ai-interaction/plans/add-full-tensor-minmax-diagnostics-and-fix-arm-output-bind-crash-20260630-175835-plan.md: FLOAT/INT8_TENSOR_FULL diags; ARM copy post-run (no output bindOutputInt8).
- [x] Executing plan dev-ai-interaction/plans/migrate-pump-g-family-hybrids-from-agent5-clean-history-pr-20260702-plan.md: migrate G-family / Quick-Fill / hybrid pump changes from agent-5 to agent-6 with clean PR-ready history.
- [x] Executing plan dev-ai-interaction/plans/disable-experiment-sets-a-b-c-f-h-20260702-plan.md: disable Sets A/B/C/F/H in pump experiment; keep D, E, G, G-, G--, I.
- [x] Merge branch `int8-paddle-processing`: production `uint8_fp16_u8` Paddle path, `prod_u8fp16` models, tailored arm64 JNI (2026-07-10).
- [x] Merge branch `operational-improvements`: Quick Fill reliability, reports redesign, expenses entry/edit, launcher icon, volume unit policy, CSV/Sheets parity, PR review fixes (2026-07-11).
- [x] Merge branch `fix_syncing_and_settings` into master: multi-destination sync/settings, TabularShare, rclone photos, expense multi-vehicle schema, self-host docs, post-PR review hardening; merge hygiene per `MASTER_AGENT_MANDATE.md` §2 (2026-07-13).

# Future work
- [x] Separate the orchestration layer (run-*, update-rules.sh, set-*-perms, setup-project, .grok/ config/hooks, permission model, worktree management, multi-agent brain) from the application source (app/, master + feature branches) into distinct trees/concerns. This will allow project-facts.md (and other facts) to be scoped appropriately per tree without overlap.
  - Approved plan: dev-ai-interaction/plans/orchestration-layer-separation-and-cleanup-plan.md
  - Execution completed (user: "approved, implement this plan"). All 7 phases + forensic gates + sim verification.
  - No app/ source touched; language-agnostic; only orchestration-infra + docs + bootstrap.
  - New: enable-full-orchestration.sh (stampable opt-in); dual-mode update-rules (stamp vs full); explicit one-time stamp comments in setup-project; docs + migration notes.
  - See ENGINEERING_LOG.md and dev-ai-interaction/orchestration-layer-inventory-phase1.md for details.
  - Results ready to test (new tag via final build).

- Approved execution of update-rules-eng-log-todo-wrapper-mandates-20260627-plan.md (special rules for ENGINEERING_LOG and TODO file type handling via wrappers)

- [ ] Executing plan dev-ai-interaction/plans/host-paddle-precision-verification-and-smoke-tests-20260701-plan.md — Phase 1 doc done; Phase 2–4: conversion verify, host_precision_smoke + explicit input×output matrix (host_precision_smoke_explicit_outputs.py).

## Backlog (paddle / not urgent)
- [ ] True LITE_BUILD_TAILOR for **x86_64** emulator (space only; prod-path speed matches fat kernels)
- [ ] True LITE_BUILD_TAILOR for **armeabi-v7a** or drop the ABI (space only if kept)

- [x] Executing plan dev-ai-interaction/plans/settings-hygiene-debug-quickfill-localization-20260712-plan.md — Settings hygiene: remove OCR threshold, Debug Quick Fill + retention/report, currency/volume system defaults, expense local-keep toggle.
- [x] Executing plan dev-ai-interaction/plans/settings-sync-shell-spreadsheet-photo-config-20260712-plan.md — Settings sync shell: destination model/store, spreadsheet/photo config screens, summary rows + stub Sync now.

- [x] Executing plan dev-ai-interaction/plans/sync-schema-identity-timestamps-tombstones-cloudmanifest-20260712-plan.md — DB v9: SyncIdentity, originDeviceId/updatedAt/tombstones, cloudManifest, repo stamps, CSV/Sheets headers.
- [x] Executing plan dev-ai-interaction/plans/google-sheets-bidirectional-sync-20260712-plan.md — Real Google Sheets bidirectional sync: auth, Sheets API, LWW coordinator, Sync now + background worker (photo backup out of scope).

- [x] Executing plan dev-ai-interaction/plans/sync-identity-auth-dedupe-cleanup-20260712-plan.md — syncId identity, auth recovery, dedupe cleanup (Phases 1–14 complete).

- [x] Executing plan dev-ai-interaction/plans/google-drive-photo-backup-cloudmanifest-20260712-plan.md — Google Drive photo backup + cloudManifest (Phases 1–19 complete).

- [x] Executing plan dev-ai-interaction/plans/vehicle-sheet-crops-landmarks-and-photo-destid-fix-20260712-plan.md — Sheet sync crops/landmarks; stop Drive landmarks; CloudManifest getFileId destId fallback (Phases 1–10 complete).

- [x] Executing plan dev-ai-interaction/plans/post-sync-vehicle-definition-rehydration-20260712-plan.md — Post-sync vehicle definition rehydration: no-stamp asset updates, LWW definition overlay, post-sync photo download, Manage Vehicles rehydrate (Phases 1–7 complete).

- [x] Executing plan dev-ai-interaction/plans/background-sync-worker-hilt-reliability-20260712-plan.md — Hilt worker factory + WM single init + bootstrap/reschedule/consent (Phases 1–6 complete)

- [x] Executing plan dev-ai-interaction/plans/sheet-oldest-first-and-incremental-sync-20260712-plan.md — sheet oldest-first + incremental sync, photo path preserve, FULL/PENDING_ONLY photo modes, 15-min background interval (Phases 1–9 complete)

- [x] Executing plan dev-ai-interaction/plans/cloudmanifest-multi-dest-pending-and-remint-20260712-plan.md — Cloud Manifest multi-dest pending + remint semantics (Phases 1–5 complete)

- [x] Executing plan dev-ai-interaction/plans/vehicle-rename-fuel-sheet-tab-migrate-20260712-plan.md — Vehicle rename → Fuel sheet tab rename/migrate (Phases 1–6 complete)

- [x] Executing plan dev-ai-interaction/plans/multi-google-destinations-20260712-plan.md — Multiple Google Sheets + Google Drive photo destinations (Phases 1–7 complete)

## Backlog (sync / settings / data model)
- [x] Multi-currency normalization: persist currency on fuel/expense rows at save (Room v11, sheet/CSV Currency column, row-aware reports); no offline FX. Future: optional conversion at sync when rates available.
- [x] Sheet tab rename when vehicle name changes: fuel tabs are named by vehicle name; renaming a vehicle must rename/migrate the corresponding Google Sheet tab (and any CSV export naming) without losing rows or breaking sync identity.
- [x] Expense multi-vehicle + multi-photo **datastore/sync schema** (Room v12 `vehicleSyncIdsJson`, `ExpensePhotoUrls` / `ExpenseVehicleSyncIds`, tabular + photo backup); **UI deferred** (multi-select vehicles, multi-page camera).
- [ ] Expense multi-vehicle picker UI + multi-page receipt capture UX (schema ready in datastore/sync).
- [ ] Import expense receipts from email and/or file pickers (not only camera / gallery).
- [x] Executing plan dev-ai-interaction/plans/csv-export-import-sheet-parity-20260712-plan.md — CSV export/import parity with Google Sheets layout (Vehicles + per-vehicle Fuel tabs + Expenses) (Phases 1–6 complete).
- [x] Executing plan dev-ai-interaction/plans/multi-currency-row-persist-20260712-plan.md — persist currency on fuel/expense rows (Room v10→v11) (Phases 1–6 complete).
- [x] ~~Find/cleanup orphaned cloud images~~ — **cancelled / no longer needed** (manifest + pending rules; not a follow-on).
- [ ] Field-level conflict resolution UI (beyond whole-row LWW by updatedAt) for multi-device edits to different columns of the same fill.
- [x] Executing plan dev-ai-interaction/plans/rclone-photo-storage-backend-20260713-plan.md — rclone photo storage backend (full librclone AAR, PhotoSyncBackend, RCLONE provider) (Phases 0–8 complete)
- [x] Executing plan dev-ai-interaction/plans/google-dest-browse-pick-create-ui-20260713-plan.md — Google Sheets/Drive browse-pick-create UX (Phases 1–6 complete)
- [x] Executing plan dev-ai-interaction/plans/rclone-config-create-ui-20260713-plan.md — in-app rclone remote create/manage UI (Phases 1–6 complete)
- [x] Executing plan dev-ai-interaction/plans/photo-backend-onedrive-and-other-label-20260713-plan.md — Google Drive / OneDrive / S3 / Other photo destinations: 4-way picker, managed S3 remote, universal test contract, Other kind groups + denylist, build_photo.sh (AAR rebuild optional)
