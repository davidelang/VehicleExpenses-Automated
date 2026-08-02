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

## 2026-06-28 - Created debug dump plan for vehicle DB + image file checksums at startup
- Wrote fresh plan to dev-ai-interaction/plans/debug-dump-vehicle-db-and-file-checksums-20260628-plan.md
- Plan addresses the exact gap: adb sees "correct" files/DB but UI on phone vs emulator differs; dump will show what the app actually loads from Room + real md5 of the pointed-to vehicle_ref_*.jpg files.
- Follows all mandates: standard structure, verbatim compliance block, ultra-micro phases with per-phase forensic read + build_app.
- Ready for designation by user for the implementing agent (e.g. agent-3).

## 2026-06-28 - Manually pushed correct phone database and photos to emulator

- Fetched fresh database and JPEGs from the Pixel 6 phone.
- Ran wal_checkpoint(truncate) locally on host to merge WAL changes into vehicle_expenses.db.
- Manually pushed the database and JPEGs to the emulator's private folders using adb streaming (dd in run-as).
- Cleared the SQLite WAL/SHM files and Coil image cache on the emulator.
- Launched the app, navigated to "Manage Vehicles", and verified that both Honda and Ford Van show the correct reference photo and crop coordinates.
- Did not make any source code changes. Stale deploy WAL-handling bug logged in my execution findings for future work.

## 2026-06-28 14:35 - Execution start for approved plan reduce-redundancy-in-prompts-plans-rely-on-eng-log-state-20260628-plan.md (with timestamp granularity tweak to minute precision in log headers)

## 2026-06-28 14:40 - Added append-to-engineering-log to approved helper scripts list (per user request). Patch created in dev-ai-interaction/add-append-to-approved-helpers.patch for config.toml and plan-mode-hard-stops.js. (Note: direct edit via search_replace blocked by fs perms in current context; patch provided for application.)

## 2026-06-29 - Executing Rules and Scripts Cleanup Plan

- Initiating execution of dev-ai-interaction/plans/rules-and-scripts-cleanup-plan.md.
- Scope: Refactoring GEMINI.md, AGENTS.md, new_agent_prompt, run-grok launchers, creating run-antigravity scripts, and removing the legacy run-gemini launcher.

## 2026-06-30 - Execution start: planning-policy-baseball-rule-and-block-slim

Approved plan: dev-ai-interaction/plans/planning-policy-baseball-rule-and-block-slim-20260630-plan.md. Baseball Rule, slim STANDARD BLOCK v2026-06-30, plan-style guide, inning-end report template.

## 2026-06-30 - Planning policy executed: Baseball Rule + slim STANDARD BLOCK

Completed planning-policy-baseball-rule-and-block-slim-20260630-plan. Tracked: AGENT_MANDATES.md, standard-plan-compliance-block.md v2026-06-30, AGENTS.md, MULTI_AGENT_USER_INSTRUCTIONS.md, OPERATIONAL_HANDBOOK.md. Sandbox: plan-style-guide.md, inning-end-report-template.md. update-rules.sh synced worktrees. Tag: orchestration/builds @ 0822cf11.

## 2026-07-13 - Fix startup instruction mismatch (Task 1)

- User approved the harness plan.md for startup fix.
- Execution start: re-read project-facts.md (stable facts only), current bootstrap files, and standard-plan-compliance-block.md.
- Will update new_grok_agent_prompt (primary), AGENTS.md, GROK.md, AGENT_MANDATES.md (consistency), and any duplicates via phased forensic edits + build_app gates.
- Goal: eliminate tool name mismatches, align with current plan-mode harness (session plan.md + enter/exit tools), emphasize two-terminal workflow, keep all core mandates intact.
- No app source changes. Forensic read before/after every edit.

## 2026-07-13 - Start execution: startup-rules-roles-permissions-cleanup-20260713-plan v7

- Resume after approval + review notes (umask run-*, project-facts candidates, master=execute plan, setup_agent no antigravity, no cd&&helpers).
- Phase 0: copy plan to sandbox; promote MASTER_AGENT_MANDATE SoT from master/.


## 2026-07-13 - Completed core phases of startup-rules-roles-permissions-cleanup-20260713-plan v7

- MASTER_AGENT_MANDATE promoted + execute-plan role + TODO close / project-facts prune at merge.
- AGENT_MANDATES: TODO helpers only, eng-log current turn, project-facts candidates, cite STANDARD BLOCK by path, no cd&&helpers, planner owns cycles.
- Launchers: run-grok=dlang bare; run-grok-orchestrator; run-grok-coder; planner/master umask 002 + TodoGate off.
- setup_agent: no auto antigravity; leave user in worktree with next-step hint.
- build_app: umask 002, gradle --no-daemon, source 2775/664 normalize; deploy umask 002.
- ve-env; skills prepare-local-pr + master-merge; disabled pr-babysit/execute-plan/design/check-work.
- AGENTS/README/PERMISSIONS_MODEL/project-facts/plan-style-guide updated.
- Commit orchestration/builds @ 401cf388; update-rules attempted.
- AGENT_CONTEXT.md created at root (may be gitignored — local orientation).


## 2026-07-13 - ve-env: fix stale-group guidance (do not use newgrp)

- dlang is in ai-code/ai-shared/ai-sandbox in NSS; shell sessions often lack them until re-login.
- newgrp only switches primary group and drops other project groups — wrong fix.
- ve-env now distinguishes stale session vs not-a-member; how-to-fix-groups; .git/config smoke.


## 2026-07-13 - ve-env re-exec via setuid ve-refresh-shell (no full desktop logout)

- User expectation: source ./ve-env fixes stale groups without logging out of whole session.
- Linux cannot inject groups into current process; solution is re-exec this terminal via setuid root helper that initgroups() for real uid only.
- One-time: gcc + sudo chown root / chmod 4755, or sudo ./fix-perms.


## 2026-07-13 - CRITICAL: restored master TODO.md after update-rules clobber

- Root cause: update-rules FILES included TODO.md; orchestration agent ritual TODO overwrote master cleaned backlog (57281374).
- Restored content from master pre-sync commit 57281374.
- Removed TODO.md from update-rules.sh and sync_infrastructure.sh FILES.
- TODO must merge only via special-file protocol / todo-append/todo-close, never blind cp.


## 2026-07-13 - NDK permission handoff for ai-coder builds

- Confirmed report: 4x libc++_shared.so under NDK 28.2 are 660; ai-coder cannot read (dlang:dlang).
- Added ./fix-android-sdk-perms for dlang to run (g+rX,o+rX on ndk; a+r on libc++_shared.so).
- Documented in PERMISSIONS_MODEL + research handoff note.
- Orchestration agent cannot chmod under /home/dlang/Android/Sdk.


## 2026-07-13 - Execution start: deploy no-daemon + KSP handoff

- Approved: restore --no-daemon on deploy (missed Phase 5 of startup-rules cleanup)
- Also: pre-gradle kspCaches wipe; gradle.properties daemon=false; PERMISSIONS_MODEL/project-facts
- Symptom: dlang deploy Permission denied on ai-coder-owned kspCaches

## 2026-07-13 - Completed: deploy no-daemon + KSP handoff

- Commit orchestration/builds @ 579f4866
- deploy: --no-daemon on both installDebug paths; prepare_build_tree_for_deploy
- gradle.properties: org.gradle.daemon=false
- PERMISSIONS_MODEL + project-facts updated
- update-rules to push deploy to worktrees

## 2026-07-14 - Execution start: git special-file merge drivers

- Approved: git-special-file-merge-drivers (session plan)
- ve-englog via append-to-engineering-log; ve-special-refuse; merge-branch-into-master always TODO/facts review
- No happy-path chattr ±a

## 2026-07-14 - Completed: git special-file merge drivers

- Tag orchestration/builds @ acb43855
- ve-englog + ve-special-refuse; install-merge-drivers; merge-branch-into-master
- Fixture: third-version eng-log + refuse message OK
- Mandate/skill/project-facts updated; update-rules next

## 2026-07-14 - fix: restore executable bit on git hooks after fix-perms

- Root cause: fix-perms chmod 660 all .git files stripped post-checkout +x
- ensure_git_hooks_executable after every .git blanket chmod
- setup_agent ensures hooks executable before worktree add and after fix-perms

## 2026-07-14 - fix: deploy/build_app stay executable after update-rules

- Root cause: update-rules cp as ai-orchestrator left 664/non-owner scripts; fix-perms only g+s without forcing +x
- ensure_worktree_scripts_executable: chown primary:ai-code + 2775 on deploy/build_app/etc
- update-rules: cp -p, chown primary, a+x per file (no fragile glob chmod)
- gradle.properties: ensure 664 primary:ai-code in fix-perms

## 2026-07-14 - fix: setup_agent project.config warning + leave shell in worktree

- post-checkout: no warn on missing gitignored project.config; skip under VE_SETUP_AGENT=1
- setup_agent: seed project.config before checkout; source ends with cd to agent; exec prints cd
- gitignore ve-refresh-shell (like run-as-primary); track hooks/post-checkout

## 2026-07-14 - setup_agent: exec worktree shell via ve-refresh-shell

- On success: VE_ENV_CWD=agent, umask 002, exec ve-refresh-shell (same as ve-env)
- Fallback: exec $SHELL in worktree if helper not setuid
- Ensures ve-refresh-shell setuid after fix-perms

## 2026-07-14 - fix: deploy no sg re-exec; wipe project .gradle locks

- Root cause: ai-coder-owned .gradle/fileHashes.lock; sg ai-shared left process without ai-code
- deploy: fail-fast if missing groups; prepare_build_tree wipes .gradle locks/version caches
- build_app: same fail-fast for groups

## 2026-07-14 - fix: ve-env must not kill terminal on bad ve-refresh-shell

- Cause: ve-refresh-shell was setuid but owned by ai-coder not root; exec then initgroups fail → shell exit
- ve-env: require owner root for helper; refuse exec of mis-owned setuid
- ve-refresh-shell: on failure re-exec normal user shell instead of return 1
- fix-perms/setup_agent: only 4755 after chown root succeeds; else chmod 755

## 2026-07-14 - ve-env check: report broken non-root setuid helper clearly

- Distinguish OK setuid-root vs BROKEN setuid-wrong-owner vs missing

## 2026-07-14 - install-ve-refresh-shell.sh: deploy helper to every worktree

- Binary stays gitignored; .c tracked
- setup_agent, fix-perms, update-rules call installer for each worktree
- setuid only after chown root succeeds

## 2026-07-14 - mandate: worktree deploy of tracked files must commit

- build_app blocks on uncommitted tracked dirt
- Prefer update-rules; ad-hoc cp must commit on target branch
- setup_agent commits seed/sync dirt before handoff

## 2026-07-14 - ve-env: never exec broken helper (master setuid ai-coder)

- Prefer good root-owned helper; --check before exec
- master ve-env was stale and killed terminal

## 2026-07-14 - fix: ve-env hang on --check subprocess

- Old helper ignored --check and hung source ./ve-env
- Stat-only gate; progress messages before exec

## 2026-07-14 - Execution start: deploy KSP wipe + launcher umask

- Approved: deploy-ksp-group-write-and-launcher-umask-20260714-plan.md
- Root cause: ai-coder 2755 dirs block dlang deploy wipe of generated/ksp; || true swallows EACCES
- Also: run-grok* umask 002 only in parent before sudo -u (may reset)


## 2026-07-14 - Completed: deploy KSP wipe + launcher umask

- deploy: fail-fast residual wipe (generated/kspCaches/intermediates); optional sudo -n -u ai-coder
- build_app: post-repair g+w audit
- run-grok*: umask 002 inside sudo -u target
- PERMISSIONS_MODEL + project-facts; plan deploy-ksp-group-write-and-launcher-umask-20260714-plan.md
- Human runs ./deploy manually (agents never deploy)


## 2026-07-14 - Unify debug keystores for all role users

- Replaced ai-coder + ai-orchestrator private debug.keystore with project .android-shared (SHA1 E0:A9:3E:54… = dlang)
- New ./sync-debug-keystores; called from fix-perms ensure_shared_build_homes
- ve-env + run-grok* export ANDROID_USER_HOME to .android-shared inside target user
- PERMISSIONS_MODEL + project-facts
- Devices already on shared cert (phones, 5554) unaffected; emulator-5556 still needs one-time uninstall by human


## 2026-07-14 - ENVIRONMENT_SETUP doc + worktree infra audit

- Audit: update-rules FILES (58) identical on orch, master/, agent-2/
- Plain master build: no paddle/opencv/rclone source clones; jniLibs + assets + librclone.aar checked in
- Added docs/ENVIRONMENT_SETUP.md; README/CONTRIBUTING/README-multi-agent pointers; sandbox audit report


## 2026-07-14 - Sync merge infra from master (ve-special-ours)

- Backported master@67a0508c: ve-special-ours, merge script index-first, install-merge-drivers autostash=false
- update-rules FILES + docs/reference/ORCHESTRATION_MERGE_INFRA_SYNC.md
- Skill failed-merge recovery points at tracked docs (not sandbox)
- Do NOT promote reset-master-pre-merge.sh


## 2026-07-15 - update-rules safety + orch tip cleanup

- update-rules: --dry-run / --force; skip dirty or worktree-ahead (blob history); copy when orch ahead
- Removed orch app docs (API/ARCHITECTURE/BUFFER_SET); master retains
- Untracked grok-install; gitignore host installers; ground_truth* kept (latest-report uses them)
- Deleted untracked processed_ground_truth.json / .orig backups


## 2026-07-16 - merge-branch-into-master: FF path + false-success fixes

- Backport master return-1 fixes; FF index path; clear partial index; no englog worktree-sync before git merge
- assert_staged_feature_files (eng-log-only stage = FAILED)
- Mandate + master-merge skill post-merge gate; postmortem on orch


## 2026-07-30 - Execution start: deploy APK-first (root only, no update-rules)

- Approved plan: APK-first deploy; --rebuild for gradle path
- Scope: orchestration root deploy only; do not run update-rules
- Version output from APK versionName (aapt), not live HEAD describe when using last APK

## 2026-07-30 - Completed: deploy APK-first (root only)

- Root deploy: default adb install -r last APK; --rebuild for wipe+installDebug
- Version: aapt versionName (git describe at assemble); not live HEAD when APK path
- Early prepare_build_tree only when REBUILD=true (preserves agent caches)
- Did NOT run update-rules; worktrees still have old deploy
- Plan archive: dev-ai-interaction/plans/deploy-apk-first-no-update-rules-20260730-plan.md

## 2026-07-31 - Execution start: orch-brain-policy-updates

- Approved plan: dev-ai-interaction/plans/orch-brain-policy-updates-20260731-1730-plan.md
- Source: policy-redteam AGENT_MANDATES DRAFT v2.1 + prompt ports
- Scope: orchestration brain only; no auto update-rules; no app Kotlin


## 2026-07-31 - Completed: orch-brain-policy-updates

- AGENT_MANDATES slim v2.1 (~12KB) + .grok/prompts/{planning,execution,dedicated}-*.md
- AGENTS/GROK/new_agent_prompt/STANDARD BLOCK; implement disabled; coder completeness+Status
- Launchers cleaned; planner prompt regenerated; style guide; skills archive notes
- Mass-archived 23 CODE LANDED/superseded plans to historical-plans/
- No update-rules (human final sweep when ready)
- Commit on orchestration (brain-only)


## 2026-07-31 - Execution start: orch-launcher-packs-and-master-gaps

- Approved: dev-ai-interaction/plans/orch-launcher-packs-and-master-gaps-20260731-1948-plan.md
- Packs for run-grok*; fix master G1-G3; no bulk plan archive; no update-rules


## 2026-07-31 - Completed: orch-launcher-packs-and-master-gaps

- compose-session-prompt.sh + role-{coder,planner,master,orchestrator,primary}.md
- All run-grok* compose file packs only; master ~2KB (was ~32KB)
- G1 ultra-micro ban correct in role-master; G2 optional checker; G3 eng-log first
- G4 bulk plan archive deferred
- update-rules FILES updated; no update-rules run
- Regenerated .planning-agent-prompt.txt from planner pack


## 2026-07-31 - Plan archive review (implemented evidence)

- Classified ~412 active plans via status/git subject/eng-log/PR/file-touch signals
- Moved ~405 to historical-plans/ (incl. prior CODE LANDED + bulk evidence pass + manual follow-ups)
- Left: 2 orch policy plans, int8/jit PROPOSED set, 256-lie experiment (no clear land)
- Report: dev-ai-interaction/research/plan-archive-review-20260731.md
- Note: scoring favors distinctive tokens; a few weak matches possible — report lists scores/reasons


## 2026-07-31 - Archive obsolete int8 plans

- User: all int8 work obsolete
- Moved remaining int8/jit/256/1024-rec experiment plans (and related patches if present) to historical-plans/
- Active plans/ now only orch policy contracts (+ any non-int8 leftovers)


## 2026-07-31 - Restore TODO-linked plans from archive

- Rule: do not archive plans/research still referenced by any TODO.md
- Restored ~18 pump/4box/binpeak plans + reports-efficiency plan from historical-plans/ to plans/
- Documented rule in plan-style-guide + project-facts
- LOCATION_LOOKUP_WORKER + i18n research already in place


## 2026-07-31 - TODO vs obsolete plans cleanup

- Closed sandbox TODO paddle-v3-greedy as done/obsolete (file never restored)
- Re-archived 17 completed pump/4box plans (only [x] TODO refs)
- Kept reports-efficiency plan (agent-1 still defers multi-select from it)
- Rule: open TODO protects plan; obsolete plan → close TODO then archive


## 2026-08-02 - third_party fetch-deps redesign execute start

- Approved plan: dev-ai-interaction/plans/third-party-fetch-deps-and-infra-gaps-20260802-1145-plan.md
- Status: APPROVED — begin phased implementation (ro/rw fetch-deps, checkifclean, infra gaps)
- Branch context: tools target email-connection (agent-4); orch session driving implementation


## 2026-08-02 - third_party plan execute complete (dlang script)

- Plan: third-party-fetch-deps-and-infra-gaps-20260802-1145-plan.md → CODE LANDED
- agent-4: 2a5a111e fetch-deps ro/rw/status/refresh/upgrade + checkifclean
- orch: ef75a7ab remove_worktree third_party src cleanup
- libs: setup_agent/update-rules/remove_worktree on remotetable+extractmail orchestration
- verify RESULT=READY; ai-coder fetch-deps status OK; pins 94164ff / d375188 ro
- checkifclean mid-run saw VE dirty (expected before commits); re-check after commit if needed
- No push (human)

