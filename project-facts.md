# project-facts.md — Stable orientation map (orchestration)

Cold-start map so agents do not hunt or invent wrong procedures. Anything discovered that would help the *next* agent is a **candidate** to add here (short pointer). Merge process validates and prunes if large.

Read in full early on startup/new cycle.

## Sandbox (dev-ai-interaction)
- Absolute path: `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/`
- `dev-ai-interaction/plans/` — designated active plan (user names exact file)
- `dev-ai-interaction/historical-plans/` — archived plans
- **TODO-linked plans/research:** keep paths referenced by **open** (`- [ ]`) TODO items in `plans/`/`research/`. If the plan is obsolete, **close/eliminate the TODO** then archive. Do not leave open TODOs pointing at dead plans.
- `dev-ai-interaction/implementation-failure-logs/` — scan on planner startup / recovery
- `dev-ai-interaction/PRs/PR-<branch>.md` — local PR docs for Master
- `dev-ai-interaction/.planning-agent-prompt.txt` — optional planner prompt file

## Device / crash logs
- Prefer `adb logcat -d` (or device-specific) into sandbox once; analyze locally. Do **not** start broad `find … *.log` hunts on the host.

## At orchestration root
- `.gradle-shared/` — **orch leftover** Maven/JDK/wrapper cache. Not agent `GRADLE_USER_HOME` (landlocked worktrees must not write orch).
- `.android-shared/` — **orch**: canonical debug keystore to **copy**. Not a live agent `ANDROID_USER_HOME`.
- `orch_root=` in gitignored `project.config` — absolute orchestration root. Worktrees do **not** guess via `../.gradle-shared`. Resolve: `./ve-resolve-orch`. `update-rules.sh` / `setup_agent.sh` stamp it.
- Per worktree writes: `app/build/`, `.gradle/` (project incremental only), `.gradle-home/` (`GRADLE_USER_HOME` — shared caches; `build_app`/`deploy` wipe `daemon/` before and after Gradle), `.android-shared/` (`ANDROID_USER_HOME`, seeded from orch keystore). `build_app` wipes `app/build` intermediates/generated/kspCaches when those dirs are not owned by the current user (same-uid incremental stays). Kotlin in-process via `gradle.properties` + `-P`/`-D` (do not grant Landlock on `~/.local/share/kotlin`).
- AGP Maven `aapt2` zip mode is 0644 (transform cache drops +x). `build_app`/`deploy` pass `-Pandroid.aapt2FromMavenOverride` to the newest executable SDK `build-tools/*/aapt2` from this worktree `local.properties` `sdk.dir` (`ve_aapt2_from_sdk` in `ve-resolve-orch`). Do not commit an absolute SDK path in `gradle.properties`.
- Launch master from `master/` (`./run-grok-master` there). Orch `./run-grok-master` binds `--worktree` to orch.
- `ENGINEERING_LOG.md` — append only via `./append-to-engineering-log`
- `TODO.md` — future backlog via `todo-append` / `todo-close`
- Launchers: `run-grok-orchestrator`, `run-grok-master`, `run-grok-planner`, `run-grok-coder`, bare `run-grok` (dlang). All exec grok with `--no-alt-screen` and `--minimal` via `.grok/lib/grok-launch-common.sh`.
- `ve-env` — `source ./ve-env` sets umask 002; if session groups stale, re-execs shell via setuid `ve-refresh-shell` (no full desktop logout). One-time: build + `sudo chmod 4755 ve-refresh-shell` (or `sudo ./fix-perms`). Never `newgrp` for multi-group. See `./ve-env how-to-fix-groups`
- **Deploy:** APK-first installs the matching ABI flavor APK from `ro.product.cpu.abi` (`app/build/outputs/apk/<arm64|x86_64|armv7>/debug/app-<flavor>-debug.apk`). `--rebuild` is per-device `installArm64Debug` / `installX86_64Debug` / `installArmv7Debug`. Version from APK `versionName` when not rebuilding. Human-only (agents do not run `./deploy` / `adb install`).
- Scripts: `update-rules.sh`, `build_app` and `deploy` (no raw gradlew; both pass `--no-daemon`; deploy wipes `kspCaches`/`intermediates`/`generated` and **fails fast** if residual foreign 2755 dirs block wipe), `get-builds-tag.sh`, `fix-perms` (rare), `setup_agent.sh` — on success **exec**s a shell in the new worktree with ve-env semantics (`VE_ENV_CWD` + setuid `ve-refresh-shell` for full groups + umask 002). `project.config` is gitignored; setup seeds it before checkout. **`ve-refresh-shell` binary is not in git** (setuid root + arch-specific); source `ve-refresh-shell.c` is tracked. Install/deploy with `./install-ve-refresh-shell.sh` [dir|`--all`] — called from `setup_agent`, `fix-perms`, and `update-rules` so each worktree gets a correct root:root 4755 binary. `run-as-primary` similarly gitignored. `remove_worktree.sh`, `generate_pr.sh`, `cleanup_pr.sh`
- **Launcher umask:** `run-grok*` set `umask 002` **inside** `sudo -u <role>` (not only in the parent shell). Parent-only umask is ignored by sudo → agents create 2755 build dirs.
- **Debug keystore:** one key only — orch `.android-shared/debug.keystore` (same SHA as dlang). `./sync-debug-keystores` copies it into each role’s `~/.android/` and worktree `.android-shared/`. Launchers/`ve-env`/`build_app`/`deploy` set `ANDROID_USER_HOME` to the **worktree** `.android-shared`. Foreign keys cause `UPDATE_INCOMPATIBLE` on devices.
- Master merge specials: `./merge-branch-into-master.sh <branch>` (tries `git merge --no-autostash`; index-first +a-safe fallback for eng-log), `./install-merge-drivers.sh` (`merge.autostash=false`), `git-merge-drivers/ve-englog` (eng-log third-version via `append-to-engineering-log`), `git-merge-drivers/ve-special-ours` (keep master TODO/project-facts; refuse path is legacy alias). Every merge re-validates TODO (todo-close) and project-facts (prune) against the **branch delta**. Handoff/recovery: `docs/reference/ORCHESTRATION_MERGE_INFRA_SYNC.md`.
- **Worktree file deploy:** copying tracked files into agent-N/master without a commit dirties the tree and **blocks `./build_app`**. Prefer `./update-rules.sh` (cp + per-worktree commit). Ad-hoc `cp` of tracked paths must be followed by commit on that worktree. Gitignored binaries (`ve-refresh-shell`) do not need commit.
- `.grok/config.toml` + `.grok/hooks/` + `.grok/skills/` (prepare-local-pr, master-merge)
- `MASTER_AGENT_MANDATE.md` — Master review/merge SoT
- `standard-plan-compliance-block.md` — cite by path in plans
- `docs/ENVIRONMENT_SETUP.md` — plain `master` clone build + multi-worktree host setup; in-repo prebuilts vs external rebuilds (on orch + synced to app worktrees)
- Ground-truth fixtures on **orchestration root** (not versioned with app): `ground_truth.json`, `ground_truth_odo.json`. Sandbox `latest-report` scripts often resolve `~/git/VehicleExpenses-automated/ground_truth_odo.json`. Do not delete. Untracked cousins (`processed_ground_truth.json`) are disposable.
- Host installers (`grok-install.sh`, `antigravity-install`) are **local only** (gitignored); `run-*` launchers sync to worktrees. `./update-rules.sh` skips worktree-ahead/dirty paths unless `--force`; supports `--dry-run`.

## Application ABI / Paddle assets
- Production models: `app/src/arm64/assets/paddle/prod_u8fp16/*_armv8.nb`, `app/src/x86_64/assets/paddle/prod_u8fp32_u8/*_x86_64.nb`, `app/src/armv7/assets/paddle/prod_u8fp32_u8/*_armv7.nb`; shared dict `app/src/main/assets/paddle/en_dict.txt`; scheduled exp dets `app/src/<abi>/assets/paddle/exp_det_ab/`
- Runtime: `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}/`, `app/libs/PaddlePredictor.jar`
- JNI sizing: arm64-v8a tailored ~1.6MB; x86_64 slim jni + `libpaddle_light_api_shared.so`; armeabi-v7a fat multi-path lib (interim)
- Product ABIs: arm64-v8a + armeabi-v7a + x86_64 flavor APKs (`useLegacyPackaging = false`). arm64/x86 jni = First-10-good pin ship.

## Worktree layout
- App worktrees (`agent-N/`, `master/`): `app/` + root scripts + symlink `dev-ai-interaction -> ../dev-ai-interaction`
- Orchestration root: source of brain for `update-rules.sh`; may lack `app/` depending on layout mode
- Sessions: human `cd`s into worktree once, then launcher; agents keep that cwd (no per-command `cd && helper`)

## Permissions targets
- Source: dirs **2775**, files **664**, umask **002**
- Build: dirs **2770**, files often **660**
- See `docs/specs/PERMISSIONS_MODEL.md`

Update only with orientation facts valid for future work. Effort/plan details → plan file or ENGINEERING_LOG.

## First-party libraries (orchestration note)

- Tracked policy after agent-4 bootstrap commit: `docs/reference/FIRST_PARTY_LIBS.md` on **email-connection** / master once merged
- Library hosts: `~/git/remotetable`, `~/git/extractmail`
- One-shot bootstrap: `bootstrap-first-party-libs.sh` + `bootstrap-first-party-libs.d/` (untracked historical)
- Staging leftovers: `dev-ai-interaction/subprojects/` until cleaned post-merge

## project.config (local only — all multi-agent hosts)
- **Never commit** `project.config` (usernames, binary paths, sandbox dir). Track `project.config.example` only.
- Applies to VE **and** first-party library hosts (`~/git/remotetable`, `extractmail`). Same rule for any third_party consumer machine.
- Seed: copy example → `project.config` on each machine/worktree. Filters/smudge need the local file.
- Library `update-rules.sh` does **not** update VE `third_party/<lib>/src` worktrees (co-dev checkouts, not dedicated lib agents).

- Launchers: thin run-grok* + .grok/prompts/packs on VE and lib hosts; local PR skills prepare-local-pr / master-merge; sandbox_dir (VE dev-ai-interaction, libs sandbox).

- Third-party pin promote (optional): `promote-third-party-pins.sh`.

- Multi-user git (all hosts): `.git` group **ai-shared** (not ai-code), dirs **2770** setgid, `core.sharedRepository=group`. Doc: `docs/reference/MULTIUSER_GIT_VE_PARITY.md`. Repair: `./fix-multiuser-git-hosts.sh` (VE + libs + orchestration-example) or `./fix-ve-git-shared.sh` (VE only). Run as **dlang** with sudo. Partial chgrp of `.git` top only is insufficient — children re-infect via setgid. **Hooks must not** run `fix-perms --all` or chown/chmod common `.git` (see PERMISSIONS_MODEL).
- Session Landlock: `agent-landlock` + wire in `.grok/lib/grok-launch-common.sh`; always grants `$HOME/.grok` (session/trust). Smoke: `./landlock-smoke-matrix`. Publish: `./deploy-landlock-fix.sh [--commit] [--also-ve]`.
- Grok 1.0 free-form (primary/orch): optional `GROK_SANDBOX=workspace` and/or `GROK_WORKTREE=1|name` (or args after `--`). Does **not** enable native plan mode. Local pool dir `grok-worktrees/` (gitignored). Master: Grok worktrees for merge **dry-run** only; final merge on real `master/`.
- Stance: avoid native plan mode for multi-agent; capability_mode not general policy; personas held; memory ⊂ project-facts; `/goal` `/deep-research` workflows opt-in. Completeness Stop hook opt-in: `VE_STOP_COMPLETENESS=1` (`.grok/hooks/stop-completeness-gate.*`).
- Grok **4.6** is the default coding model; **process is unchanged** (`AGENT_MANDATES.md` §3.5a). `grok -c` / `--resume` keeps transcript + **stored** `current_model_id` (does not upgrade 4.5→4.6; use `/model`). Planner/coder launchers pass `GROK_WORKFLOWS=0`; planner also `GROK_SUBAGENTS=0`.
- **Permission blocks:** authoritative rule is **`AGENT_MANDATES.md` §1.1** (report; do not work around). Orientation only here.
