# Environment Setup — Build & Multi-Agent Layout

This is the **source of truth** for bringing up Vehicle Expenses Automated:

1. **Plain app environment** — clean `git clone` of `master` → successful `./build_app` / Gradle assemble.
2. **Full multi-worktree environment** — orchestration root + `master/` + `agent-N/` + shared sandbox (current primary workflow).

Related docs: `docs/specs/PERMISSIONS_MODEL.md`, `README-multi-agent.md`, `docs/specs/OPERATIONAL_HANDBOOK.md` (agent protocol, not host bootstrap). **User manual (edit Markdown → render HTML for browsers/app):** `docs/reference/USER_MANUAL_BUILD.md` (`./scripts/render-user-manual.sh`).

---

## 1. What is in git vs external clones

### 1.1 No git submodules for native OCR / rclone

There is **no** `.gitmodules` dependency for Paddle, OpenCV, or rclone. A normal clone of **`master`** already contains the prebuilt libraries and models needed to **compile and run** the app.

| Component | In git? | Path | Rebuild from source? |
|-----------|---------|------|----------------------|
| App Kotlin / Compose / Hilt / Room | Yes | `app/src/main/java/…` | N/A (source of truth) |
| JNI / C++ app code | Yes | `app/src/main/cpp/` (`BufferSet.cpp`, `NativeImageUtils.cpp`, `libraw/`, headers) | Built by CMake during Gradle |
| OpenCV shared lib | **Yes (prebuilt)** | `app/src/main/jniLibs/<abi>/libopencv_java4.so` | Only if upgrading OpenCV |
| Paddle Lite JNI | **Yes (prebuilt)** | `app/src/main/jniLibs/<abi>/libpaddle_lite_jni.so` (+ optional `libpaddle_light_api_shared.so` on x86_64) | Only if upgrading Paddle Lite |
| Paddle Java helper | **Yes** | `app/libs/PaddlePredictor.jar` | With Paddle rebuild |
| Paddle OCR models + dict | **Yes** | `app/src/main/assets/paddle/` (`.nb` models, `en_dict.txt`, helper scripts) | Scripts under `assets/paddle/scripts/` when re-exporting models |
| rclone Android AAR | **Yes (prebuilt)** | `app/libs/librclone.aar` | Only if rebuilding librclone from Go |
| ML Kit text recognition | Maven | `com.google.mlkit:text-recognition` | Downloaded by Gradle |
| Other AndroidX / Play / Hilt | Maven | `app/build.gradle.kts` | Downloaded by Gradle |

**Bottom line for a first build:** you do **not** need to clone Paddle-Lite, OpenCV, or rclone repositories. Those trees under `dev-ai-interaction/` (e.g. historical Paddle-Lite build sandboxes) are **research / rebuild** artifacts, not compile prerequisites.

### 1.2 When you *would* clone external tools

| Goal | Typical external tree | Output checked into this repo |
|------|----------------------|-------------------------------|
| Upgrade Paddle Lite `.so` / jar | Paddle-Lite (or project notes under sandbox research) | `jniLibs/**`, `PaddlePredictor.jar` |
| Re-export / quantize OCR `.nb` models | Paddle tooling + scripts in `assets/paddle/scripts/` | `assets/paddle/prod_u8fp16/*.nb` |
| Upgrade OpenCV Android SDK | OpenCV Android pack | `jniLibs/**/libopencv_java4.so` + headers under `cpp/include/opencv2` if needed |
| Rebuild librclone | rclone/librclone Go build | `app/libs/librclone.aar` |

Until you intentionally upgrade those, **ignore external clones**.

---

## 2. Host prerequisites (plain or multi-user)

| Requirement | Notes |
|-------------|--------|
| **Linux** (this project’s multi-user model is Linux) | macOS may build app sources but permissions helpers assume Linux groups |
| **Git** | Version from `git describe` is baked into `versionName` |
| **JDK 17** | Prefer a full JDK. Toolchain: `jvmToolchain(17)` + Foojay resolver in `settings.gradle.kts`. If system default is JRE-only, install OpenJDK 17 or set `org.gradle.java.home` in **user** `~/.gradle/gradle.properties` |
| **Android SDK** | `compileSdk` / `targetSdk` **36**; platform-tools; build-tools |
| **Android NDK** | Project uses NDK for CMake (e.g. **28.x** on the primary machine). Must be installed under the SDK (`ndk/…`) |
| **CMake 3.22.1** | Requested in `app/build.gradle.kts` `externalNativeBuild.cmake.version` (SDK CMake package is fine) |
| **Network** once | Gradle downloads Maven deps |
| **Optional: `adb`** | Only for device install (`./deploy` — **humans only**) |

### 2.1 Point Gradle at the SDK

Create **`local.properties`** at the repo root (usually **gitignored** / machine-local):

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Android Studio generates this automatically. Agents and scripts expect a real SDK path.

### 2.2 Multi-user SDK readability (`ai-coder` builds)

If agents build as a non-primary user:

- NDK sysroot libs (e.g. `libc++_shared.so`) must be group/other-readable.
- Run as primary: **`./fix-android-sdk-perms`** after NDK install/upgrade.
- See `docs/specs/PERMISSIONS_MODEL.md` and sandbox research `ndk-build-permission-failure-ai-coder-*.md`.

### 2.3 Debug signing (one key)

| Item | Location |
|------|----------|
| Canonical keystore | `<repo-or-orchestration>/.android-shared/debug.keystore` (seeded from primary `~/.android/debug.keystore`) |
| Unify role homes | **`./sync-debug-keystores`** (also from `fix-perms`) |
| Runtime env | `build_app` / `deploy` / `ve-env` / `run-grok*` set `ANDROID_USER_HOME` → `.android-shared` |

Foreign per-user keys cause `INSTALL_FAILED_UPDATE_INCOMPATIBLE` on devices. Phones already on the shared cert stay fine; a device installed with a foreign cert needs **one** uninstall then re-deploy by a human.

---

## 3. Plain environment — `git clone` of `master`

Goal: compile the Android app **without** multi-agent worktrees.

### 3.1 Steps

```bash
git clone <repo-url> VehicleExpenses-automated
cd VehicleExpenses-automated
git checkout master   # if default is not master

# SDK pointer
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # adjust path

# Optional: group-friendly umask if you will share the tree
umask 002

# Preferred project gateway (commits + tags + assembleDebug):
./build_app "initial smoke"   # or with no dirty files: ./build_app

# Or, for a one-off local compile only (not the agent-mandated path):
# ./gradlew --no-daemon assembleDebug
```

### 3.2 What you get on a plain `master` checkout

Tracked on `master` tip (via normal git history + periodic `update-rules` commits of brain files):

- Full **`app/`** tree, Gradle wrapper, `build_app`, `deploy`
- Mandates / launchers / `fix-perms` / `sync-debug-keystores` (brain files currently present on master as well as orchestration; separation is still evolving — see README)
- **Not** required: orchestration branch, `agent-N/` directories, Grok CLI

### 3.3 What you do *not* need for plain build

- Cloning Paddle / OpenCV / rclone source trees  
- Creating `ai-coder` / groups (single-user primary is enough)  
- `dev-ai-interaction` sandbox (only for agent plans/logs)  
- Grok / Gemini install  

### 3.4 Success criteria

- `./build_app` or `./gradlew assembleDebug` completes **BUILD SUCCESSFUL**
- APK under `app/build/outputs/apk/debug/`
- Native tasks (`configureCMake*`, `buildCMake*`) succeed (NDK readable)

### 3.5 Known doc gap

`CONTRIBUTING.md` still points at `docs/developer-guide.md`, which is **missing**. Prefer **this file** for setup; architecture lives under `docs/specs/`.

---

## 4. Full multi-worktree environment (current primary)

### 4.1 Layout

```
VehicleExpenses-automated/          # orchestration branch (managing root)
├── master/                         # worktree → branch master
├── agent-N/                        # worktrees → feature branches
├── dev-ai-interaction/             # shared sandbox (plans, PRs, logs)
├── .gradle-shared/                 # shared Gradle user home (multi-user)
├── .android-shared/                # shared debug keystore
├── update-rules.sh                 # push brain FILES into worktrees + commit
├── setup_agent.sh                  # create agent-N worktree
├── run-grok-*                      # role launchers
└── build_app, deploy, fix-perms, sync-debug-keystores, ve-env, …
```

App worktrees symlink: `dev-ai-interaction` → `../dev-ai-interaction`.

### 4.2 Bootstrap (once on a machine)

1. **Users/groups** (names from `project.config` / example):  
   `ai-code`, `ai-shared`, `ai-sandbox`; users `ai-coder`, `ai-orchestrator`, `ai-planner`, primary `dlang`.  
   See `docs/specs/PERMISSIONS_MODEL.md` and comments in `project.config.example` / `setup-project` (emit-only user commands).
2. **Copy** `project.config.example` → **`project.config`** (gitignored); fill real usernames/paths.
3. Clone or worktree the **orchestration** branch as the managing root (or run `./enable-full-orchestration.sh` from a plain tree for guidance).
4. **`sudo ./fix-perms`** (rare) for systemic ownership/setgid; day-to-day prefer `source ./ve-env`.
5. **`./install-ve-refresh-shell.sh --all`** (or via fix-perms) for setuid group refresh helper.
6. **`./sync-debug-keystores`** (prefer once with sudo for correct home ownership).
7. **`./fix-android-sdk-perms`** as primary after NDK install.
8. Seed **`.android-shared`** / **`.gradle-shared`** if missing (fix-perms / setup_agent also help).
9. Install **Grok CLI** (or Gemini) binaries referenced by launchers (`GROK_BIN` / project.config).

### 4.3 Daily multi-agent flow

```bash
# Managing root (orchestration)
source ./ve-env          # umask 002 + groups; ANDROID_USER_HOME
./setup_agent.sh my-feature
cd my-feature            # or agent-N
../run-grok-coder        # or run-grok-planner / run-grok-master from appropriate trees

# Builds: always inside the worktree
./build_app "msg" changed.kt …

# Deploy: human only, from the same worktree
./deploy
```

After brain/infra edits on orchestration:

```bash
./update-rules.sh --dry-run   # preview COPY vs SKIP (dirty / worktree-ahead protected)
./update-rules.sh             # publish; skips worktree-ahead or dirty paths
./update-rules.sh --force     # always take orchestration content when different
```

Equal content is always a no-op. Host installers (`grok-install.sh`, `antigravity-install`) are **not** in git and are **not** synced; `run-grok*` / `run-antigravity*` **are** synced.

### 4.4 Worktree infra parity checklist

`update-rules.sh` **FILES** list is the contract. On a healthy machine, for each path in `FILES`:

- Present under orchestration root, `master/`, and each `agent-N/`
- **Byte-identical** to orchestration after a successful sync
- Scripts that must be executable: `build_app`, `deploy`, `fix-perms`, `sync-debug-keystores`, `ve-env`, `run-grok*`, etc. (`update-rules` / fix-perms force `+x` on known names)

**Not** in FILES (machine-local or generated):

| Path | Notes |
|------|--------|
| `local.properties` | SDK path; per machine / worktree |
| `project.config` | gitignored secrets/names |
| `app/build/`, `.gradle/`, `.cxx/` | build outputs |
| `ve-refresh-shell` binary | setuid; built by `install-ve-refresh-shell.sh` |
| `run-as-primary` binary | optional setuid helper |
| `.android-shared/`, `.gradle-shared/` | shared state outside normal “source” sync |

### 4.5 Orchestration root vs app worktree

| | Orchestration root | `master/` / `agent-N/` |
|--|--------------------|------------------------|
| Branch | `orchestration` | `master` / feature |
| `app/` + `gradlew` | Often **absent** | Present |
| `./build_app` | Tags only if no gradlew | Full compile + tag |
| `update-rules.sh` | **Source** of brain push | Receiver |
| `setup_agent.sh` | Run here | Creates sibling worktree |

---

## 5. Build gateway rules (both modes)

| Do | Do not |
|----|--------|
| `./build_app` for agent-mandated builds (commit + tag + assemble) | Raw `./gradlew` as the normal agent path |
| Human `./deploy` for install | Agents running `deploy` / `adb install` / `installDebug` |
| `source ./ve-env` if groups/umask wrong | `newgrp` for multi-group restore |
| `./sync-debug-keystores` if signing drifts | Unique `~/.android/debug.keystore` per ai-* user for installs |

---

## 6. Quick verification recipes

### 6.1 Plain clone build smoke

```bash
test -f local.properties && test -x gradlew
./gradlew --no-daemon :app:assembleDebug
# or ./build_app
```

### 6.2 Infra sync smoke (multi-worktree)

```bash
# From orchestration root — all FILES present and matching (example)
cmp -s build_app master/build_app && cmp -s build_app agent-2/build_app && echo build_app OK
cmp -s sync-debug-keystores master/sync-debug-keystores && echo sync-debug-keystores OK
test -x master/build_app && test -x agent-2/deploy
```

### 6.3 Keystore unity

```bash
./sync-debug-keystores
# All printed SHA1 lines should match the canonical shared key
```

### 6.4 NDK readable as coder

```bash
# as primary:
./fix-android-sdk-perms
# as ai-coder:
test -r "$ANDROID_SDK_ROOT/ndk/"*/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so
```

---

## 7. Troubleshooting map

| Symptom | First action |
|---------|----------------|
| `libc++_shared.so: Permission denied` | `./fix-android-sdk-perms` as primary |
| `Unable to delete …/generated/ksp/…` on deploy | Owner wipe of `app/build/generated` or fail-fast message from deploy; ensure group-writable build dirs |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Unify keys (`./sync-debug-keystores`); uninstall once on poisoned device; human re-deploy |
| Missing groups / umask | `source ./ve-env` |
| Uncommitted tracked files block `build_app` | Commit via `./build_app msg files…` or restore; after ad-hoc `cp` of tracked scripts, commit or `update-rules` |
| Orchestration `./build_app` “No gradlew” | Expected; compile inside `master/` or `agent-N/` |

---

## 8. Document ownership

| Topic | Document |
|-------|----------|
| Host + plain + multi setup | **This file** (`docs/ENVIRONMENT_SETUP.md`) |
| Unix permissions model | `docs/specs/PERMISSIONS_MODEL.md` |
| Agent bi-modal protocol | `AGENT_MANDATES.md`, `OPERATIONAL_HANDBOOK.md` |
| Multi-agent workflows | `README-multi-agent.md` |
| Architecture / OCR coords | `docs/specs/ARCHITECTURE.md`, `ISOTROPIC_COORDINATE_SPEC.md` (if present) |

Update this file when: new native prebuilts are required, SDK/NDK floors change, or the FILES sync contract changes.
