# PERMISSIONS_MODEL.md — Authoritative Permissions Specification

**This is the source of truth for the multi-user / multi-agent permissions model.**
All code, scripts, and docs must follow this. Changes require plan + approval.

## TODO.md Rules (additions are rare)
- Not world-writable (664 dlang:ai-shared).
- Additions ONLY via `todo-append` (high-level future deferrals only, when you explicitly decide NOT to address in this turn).
- Close items ONLY via `todo-close`.
- Never bulk-edit, git reset, or commit "20 lines per commit".
- Detailed work goes in the active plan + ENGINEERING_LOG.md.
- Agents (including previous sessions) have trashed it via direct edits/resets; the helpers + prompts enforce discipline.

## Principles (from user mandates)
- Pure Unix permissions only (owner/group/other, setgid on dirs, setgid on select scripts). **No ACLs (setfacl)**.
- No files owned by root in normal operation. Owner at most `dlang`.
- Root used *only* for fixing broken perms or initial bootstrap. No ongoing root changes.
- `dlang` must be able to build, deploy, etc. without the act breaking permissions for agents.
- Device app data must survive normal deploys (use PRESERVE_DATA; no re-init every build).
- Kernel ignores suid on scripts. Use setgid binaries/helpers where needed for enforcement.
- If sudo required, ai-* users sudo **to dlang** (never root) via NOPASSWD for specific approved commands.
- All compile paths (gradle, cmake, ndk, paddle builds, etc.) in agent-*/app and dev-ai-interaction must work for `ai-coder` and `dlang`.

## Groups and Users
- `ai-code`: coders + orchestrator + dlang (write to source + build artifacts).
- `ai-shared`: all ai-* + dlang (shared writable things: log, sandbox, launchers, .git).
- `ai-planner` is *intentionally not in ai-code* (read-only on source via "other" perms).
- dlang is member of both ai-code and ai-shared.
- ai-coder, ai-orchestrator in ai-code + ai-shared.
- ai-planner in ai-shared (for sandbox access).

## Directory and File Permissions (pure Unix + setgid)
- **Source code** (app/, docs/, etc., excluding build/ and dev-ai-interaction/): `dlang:ai-code 2775` (setgid).
  - Files created by group members: **664**.
  - Non-members (ai-planner as "other"): r-x (read source, no write).
  - **Umask for source/docs work: `002`** (666→664, 777→775 → with setgid dirs **2775**). **Not** `007` (that yields 660/770 and breaks planner other-read).
  - **Failure mode:** umask `027` → **640** (owner-only write; group read-only; planner cannot read). Fix via `source ./ve-env`, agent launchers, and `build_app`/`deploy` post-normalize — not daily `fix-perms`.
- **Build output** (app/build/, build/, .gradle/): `dlang:ai-code 2770` (setgid). Files often **660**. Group members can overwrite. Gradle in `build_app` **and** `deploy` uses `--no-daemon` and may use umask 007 only inside the gradle subprocess; then chmod dirs 2770.
- **Sandbox** (dev-ai-interaction/ and subdirs for plans etc.): `dlang:ai-shared 2775`.
- **.git/**: `dlang:ai-shared 2770` (group can add objects).
- **ENGINEERING_LOG.md**: `dlang:ai-shared 660` + `chattr +a` (kernel append-only). Set once (root only if needed to re-apply).
- **append-to-engineering-log**: `dlang:ai-shared 2755` (setgid) — enforces format and only appends. Even ai-planner can use it because of setgid.
- **TODO.md / project-facts.md**: `dlang:ai-shared 664` (not world-writable; group rw for planner + coders, other r).
- **Launchers and scripts** (run-*, build_app, deploy, etc.): `dlang:ai-shared 755` (or 2755 setgid where they need to provide egid for children).
- **run-as-primary** (generic setuid helper, not named after any local account): owned by primary_user (e.g. dlang), mode 4755. Any process executing it gets euid of the file owner for keystore consistency. Source run-as-primary.c is tracked; binary is built locally and ignored.
- **No world-writable (666) anywhere** except possibly temp.

New files inherit group via setgid. **General/project shells and agent launchers: umask 002.** Build artifact trees tightened to 660/2770 after gradle.

## Critical Helpers (enforce rules where Unix alone is insufficient)
- **append-to-engineering-log**: setgid wrapper + chattr +a on log. Validates `## YYYY-MM-DD` header. Only appends. Prevents git reset / overwrite on log.
- **todo-append**: (new) setgid helper. Only appends high-level future items under `# Future work` (rare use: only explicit deferrals, not per-commit). Enforces format.
- **todo-close**: (new) setgid helper. Marks specific items [x] or closes them. No bulk overwrites.
- These are small shell + (if needed) setgid C binary for strict enforcement. Owned `dlang:ai-shared`, setgid.
- Agents **must** use helpers for log and TODO changes. Direct edits or `git reset` on them forbidden.

## Build vs Deploy Ownership
- Build dirs setgid `ai-code` + 2770 ensures any group member can create/overwrite generated files (BuildConfig etc.).
- `ai-coder` builds (via `build_app`) → files `ai-coder:ai-code 660` or inherited group. **`build_app` does not re-exec as dlang** for agents (only root→primary).
- `dlang` deploys → re-exec as primary; member of ai-code; dir write bit allows delete/overwrite of other-group-member files (standard Unix behavior in non-sticky dir).
- **No Gradle daemon:** both `build_app` and `deploy` pass `--no-daemon`; `org.gradle.daemon=false`. Daemon reuse across uids caused KSP/cache Permission denied after agent builds.
- `deploy` wipes `app/build/kspCaches` (and intermediates/generated/config-cache) before compile so foreign-owned incremental state is not required.
- Scripts always: `umask 007; sg ai-code` (and `sudo -u dlang` only for keystore/signing if needed).
- No direct `gradlew` or `gradlew.bat`. Agent builds through `build_app`; device install through `deploy` (human/primary).
- `build_app` forwards gradle flags after `--` (e.g. `build_app "msg" file -- --info --stacktrace`).
- Deploy never does unconditional uninstall. Uses PRESERVE_DATA for data survival.

## Sudo Model
- ai-* users: `sudo -u dlang` only for approved commands that require dlang identity (keystore, certain deploys).
- dlang: `sudo -u ai-*` only for launching agent CLIs as the correct role (via run-* scripts).
- Never ai-* to root. Never unnecessary sudo.
- NOPASSWD in sudoers for exact paths only.

## Device Data
- Always prefer in-place updates.
- Only uninstall on explicit `--clean` or after successful PRESERVE backup on mismatch.
- Backup uses run-as + private storage (no /sdcard write issues).
- No re-init on normal deploys.

## Enforcement and Drift Prevention
- All scripts enforce umask/sg/sudo logic at top.
- `set-worktree-perms` (dlang-runnable): sets the above, with explicit post-chown fixups for exceptions (log, TODO, facts, wrapper, build dirs). No ACLs.
- Git hooks / plan-mode hooks / AI prompts forbid direct gradlew, git reset on log/TODO, bulk overwrites.
- Specs in this doc are authoritative. Update only via approved plan.
- Fresh `setup_agent.sh` leaves correct initial perms (no root chowns in normal flow).

## Android SDK / NDK (outside the git tree)

SDK is typically under `/home/dlang/Android/Sdk` (primary user home). NDK sysroot libs (`libc++_shared.so`) must be **readable by `ai-coder`** for CMake configure during `./build_app`.

| Bad | Good |
|-----|------|
| `dlang:dlang 660` on `libc++_shared.so` | `a+r` (and preferably `ai-code` group + `g+rX` on NDK tree) |

**Fix (as dlang, after NDK install/upgrade):** `./fix-android-sdk-perms`  
See also `dev-ai-interaction/research/ndk-build-permission-failure-ai-coder-20260713.md`.

`fix-perms` / `update-rules` do **not** modify the SDK tree (outside worktrees).

## When to Run Fixers / env helpers
| Symptom | Run |
|---------|-----|
| New shell, unsure umask/groups | `source ./ve-env` or `./ve-env check` |
| Agent/build created wrong modes | next `./build_app` / `./deploy` (normalize) |
| Root-owned / systemic breakage | `sudo ./fix-perms` (**rare**) |
| `ai-coder` NDK Permission denied on `libc++_shared.so` | `./fix-android-sdk-perms` as **dlang** |
| Daily work | Do **not** run fix-perms habitually |

- dlang builds/deploys must leave the tree in a state where ai-coder can continue (enforced by scripts + 2770 build dirs + 664 sources).

This model satisfies all constraints with minimal complexity.
