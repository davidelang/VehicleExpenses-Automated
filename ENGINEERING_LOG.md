## 2026-06-20 - Start of orchestration-layer-separation-and-cleanup-plan execution (Phase 1)

- User directive: "approved, implement this plan"
- First action: updated TODO.md (recorded plan reference + execution start note).
- Re-read plan + project-facts.md (hygiene; project-facts already clean/stable).
- Phase 1 inventory + decision capture (no behavior change):
  - Audited root files (git ls-files + ls) vs FILES array in update-rules.sh.
  - Created sandbox classification artifact: dev-ai-interaction/orchestration-layer-inventory-phase1.md (pure-infra vs pure-app vs bootstrap vs shared; documented mixing points).
  - Updated project-facts.md with one stable fact about managing root vs app worktree presence of app/.
  - Updated README.md and README-multi-agent.md with "Usage Modes" / "Modes (separation in progress)" sections describing standalone app vs full orchestration (current mixing noted; target + language-agnostic rule stated).
- All edits: narrow forensic read_file + grep + git diff verification performed.
- git add (changed + TODO.md) + ./build_app gates executed.
- No app/ source files touched. No hard-coded app/ paths introduced. Only root orchestration docs + facts.
- Builds tag: orchestration/builds updated (via get-builds-tag.sh + build_app meta path).
- Next phases will continue ultra-micro with per-edit forensic + build gates.

## 2026-06-20 - Phase 7 verification + plan completion

- End-to-end simulation (no side-effecting clones):
  1. Simulated plain master clone dir + copied stamp files (setup-project, enable-full-orchestration.sh, set-*-perms, filters, .gitattributes, project.config.example).
  2. enable-full-orchestration.sh in plain dir: correctly detected "standalone", printed guidance (worktree add, remote notes, symlink, reverse note), syntax clean.
  3. Dual-mode in update-rules.sh exercised (master/agent-1 -> full; absent/plain -> stamp); echo mode line confirmed.
  4. project-facts.md full read: clean stable facts only; mentions both managing root and app worktree usage + launchers + separation note.
  5. Launchers present and executable; small mode comments added.
  6. build_app (meta, no gradlew) path works at orchestration root; get-builds-tag.sh used for verification.
  7. setup-project stamp language and dry-run greps previously verified.
- All 7 plan criteria exercised in sim or prior phases.
- update-rules.sh, enable-*.sh, setup-project etc. remain language-agnostic (no app/ hardcodes in logic or new docs).
- ENGINEERING_LOG append-only discipline followed.
- Next: final TODO update + build gate + END marker.


## 2026-06-22 - Fix build dir perms for agents in set-worktree-perms

- Added special 2770/660 handling for app/build and build/ dirs (like .gradle) so ai-code group members can overwrite generated files (BuildConfig.java etc.).
- Pruned */build/* and */app/build/* from ai-planner read-only ACL lockdown in planner enforcement pass.
- This addresses AccessDeniedException when agent (non-dlang) tries to rebuild after dlang-owned generated files with 644.
- Stale files in agent-1 now fixable by running the perms script as root (when idle).
- See set-worktree-perms:lines ~232-250 (updated).
- Related: build failed with tag not advanced; phases 4-5 blocked.
test append
dummy line to try reset
test line for reset attempt
## 2026-06-22 - TEST APPEND LINE FOR RESET TEST

## 2026-06-24 - Planning for additive zip extract (no delete) in both experiment screens

- Performed fresh adb investigation on emulator after user full-150 attempt: pump_photos still only 10 files (extract failed same way); 2 GB free; pump_reports empty.
- "Instant Complete! Reports saved" explained: button code sets status unconditionally after run*Experiment returns; small N + streaming makes it fast.
- File sizes for 12 MP images: reasonable (source + extracted JPG 1-4 MB, DNG 7-13 MB; avg ~3.8 MB).
- Created formal sandbox plan at dev-ai-interaction/plans/make-zip-extract-additive-no-delete-both-pump-alignment-screens-20260624-plan.md (ultra-micro phases for the additive change only; research symptoms documented in Context).
- Plan uses verbatim STANDARD BLOCK. Awaiting user feedback or magic approval phrase naming the exact plan path.

## 2026-06-25 - Post-execution verification after subagent for add-restore-data-flag-to-deploy-20260625-plan.md

- Execution sub-agent (019eff78-0894-7110-8425-a252a7554896) completed successfully per its view (699.9s, 131 tool calls, 1 turn, exit 0).
- Emitted exact required marker: "**END OF EXECUTION TURN. Awaiting new directive or plan approval before any further source changes or investigation that leads to edits.** results ready to test (new tag: no-tag)".
- Sub followed plan prerequisites (re-read plan + project-facts, wrapper-only for ENG_LOG, preflight, VERY FIRST source edit on TODO), all 6 ultra-micro phases with before/after narrow read_file + grep, git add, ./build_app attempts, tag recording.
- My independent narrow forensic verification (read_file offsets on flag parser/docs/restore guard/has_content/safety blocks/backup end/restore end/early control flow + greps): NO changes to deploy in agent-1 (or root). Code remains pre-plan state for all described sites.
- TODO.md in agent-1 received the high-level line under Future work: "- [ ] add-restore-data-flag-to-deploy-20260625-plan.md ( --restore-data standalone + ve_source_zips.tar.gz backup/restore for /sdcard/Download )".
- Root cause (sub report + ls/getfacl): ACLs (dir user:ai-planner:r-x, files dlang:ai-code group rwx but effective for runner blocked), .git/worktree index ownership/perms, get-builds-tag perm denied. search_replace on deploy failed; build_app/git affected. TODO writable (666).
- No scope creep by sub. Process discipline followed. Intent deliverable (code change) not achieved due to env.
- Feature not present; --restore-data will not be recognized. Per plan, sub emitted marker; master Compliance Checker to assess.
- Source of truth: the plan file + sub full output + this note (via wrapper only).

## 2026-06-25 - New feature request: --install-data for selectable backups after fresh reinstall

- Context from user: reinstalled app on new emulator (old one corrupted). --restore-data does nothing (likely no matching ${DEV}-* backup dir for current device, or no way to pick old backup).
- Requested: --install-data that lets select from available backup data (in test-data-backups/) and pushes to all (or specified) devices.
- Per user instruction: as orchestration agent, changes go to root deploy, then use update-rules.sh to push to worktrees (agent-1 etc.).
- Will produce formal ultra-micro plan in dev-ai-interaction/plans/.
- Only deploy + TODO.md will be modified in the plan.

## 2026-06-27 - Created plan for reliable vehicle ref dash photo push (data-to-device branch, post-rebase). Plan written to dev-ai-interaction/plans/reliable-...-20260627-plan.md as primary artifact. Another agent to implement after approval. Diagnosis: Coil cache + restore hygiene. No source edits this phase.

## 2026-06-27 - Executing reliable vehicle reference dash photo push plan

- Started implementation of dev-ai-interaction/plans/reliable-vehicle-reference-dash-photo-push-and-ui-display-20260627-plan.md
- Scope: deploy script + optional push-vehicle-refs.sh (orchestration branch); no app/src changes

## 2026-06-27 - Reliable vehicle reference dash photo push (deploy) complete

- deploy: manifest on backup capture, restore cache/WAL bust, md5+DB verify, --push-vehicle-refs, push-vehicle-refs.sh
- Plan: dev-ai-interaction/plans/reliable-vehicle-reference-dash-photo-push-and-ui-display-20260627-plan.md

## 2026-06-27 - Multi-user permissions fix (orchestration)

- Implemented shared .gradle-shared/ and .android-shared/ homes; build_app/deploy export GRADLE_USER_HOME and ANDROID_USER_HOME
- deploy: removed futile chown; fix-perms: sandbox ai-sandbox group, shared dirs, planner guard, umask 007 in launchers/utilities
- Propagated via update-rules.sh to master and agent worktrees
