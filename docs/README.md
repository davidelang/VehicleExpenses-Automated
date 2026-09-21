# Docs

Product story and the index. GitHub’s front page is the repo [README](../README.md).

| | |
|--|--|
| [OVERVIEW.md](OVERVIEW.md) | What the app is for, and links into the rest |
| [QUICK_GUIDE.md](QUICK_GUIDE.md) | Short everyday guide |
| [user-manual/manual.md](user-manual/manual.md) | Full manual source |
| [user-manual/index.html](user-manual/index.html) | Rendered manual (browsers and the app) |
| [user-manual/images/](user-manual/images/) | Screenshots |
| [ENVIRONMENT_SETUP.md](ENVIRONMENT_SETUP.md) | Clone, SDK/NDK, multi-worktree host |
| [CHANGELOG.md](CHANGELOG.md) | Product notes |

## reference

Updated when the code changes.

| File | One line |
|------|----------|
| [QUICK_FILL_CAMERA.md](reference/QUICK_FILL_CAMERA.md) | Dash identity, odometer, pump stages, location |
| [OCR_PREPROCESSING.md](reference/OCR_PREPROCESSING.md) | Crop prep before odometer and pump OCR |
| [PUMP_CLASSIFICATION.md](reference/PUMP_CLASSIFICATION.md) | How cost and volume are chosen |
| [PUMP_EXPERIMENT_FLOWS.md](reference/PUMP_EXPERIMENT_FLOWS.md) | Current heatmap-expand attempts |
| [NAVIGATION_MAP.md](reference/NAVIGATION_MAP.md) | Drawer and screens |
| [API.md](reference/API.md) | Room entities |
| [REPORTS_METRICS.md](reference/REPORTS_METRICS.md) | Fuel-economy math |
| [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md) | Room merge with spreadsheet and photo targets |
| [SYNC_TAB_LWW_AND_TESTS.md](reference/SYNC_TAB_LWW_AND_TESTS.md) | Tab last-writer-wins runbook |
| [UI_COMPATIBILITY.md](reference/UI_COMPATIBILITY.md) | Compose label, font-scale, and photo rules |
| [USER_MANUAL_BUILD.md](reference/USER_MANUAL_BUILD.md) | How the manual becomes HTML and app assets |
| [APP_LAUNCHER_ICON.md](reference/APP_LAUNCHER_ICON.md) | Launcher icon source |
| [16k-pages-compatibility-notes.md](reference/16k-pages-compatibility-notes.md) | 16 KB page-size notes |
| [PADDLE_ABI_EMULATOR_TEST.md](reference/PADDLE_ABI_EMULATOR_TEST.md) | Runbook for multi-ABI Paddle tests |
| [PADDLE_SO_SMOKE.md](reference/PADDLE_SO_SMOKE.md) | Runbook for `paddle_so_smoke` |
| [ORCHESTRATION_MERGE_INFRA_SYNC.md](reference/ORCHESTRATION_MERGE_INFRA_SYNC.md) | How merge drivers stay in sync with orchestration |
| [self-host/INDEX.md](reference/self-host/INDEX.md) | Self-hosted sync cheatsheets (one page per provider, plus vendor links) |

## specs

Locked. If the code and the spec disagree, the code is wrong. Edits need a strategy turn and explicit approval.

| File | One line |
|------|----------|
| [BUFFER_SET_SPEC.md](specs/BUFFER_SET_SPEC.md) | 8-bit YUV pair, flip, grow-only resize, crops on the logical slice |
| [ISOTROPIC_COORDINATE_SPEC.md](specs/ISOTROPIC_COORDINATE_SPEC.md) | Center-relative coordinates |
| [TAKE_SNAPSHOT_SPECIFICATION.md](specs/TAKE_SNAPSHOT_SPECIFICATION.md) | Stateless native thumbnail |
| [PERMISSIONS_MODEL.md](specs/PERMISSIONS_MODEL.md) | Unix modes, git group, Landlock |
| [BUILD_ENVIRONMENT.md](specs/BUILD_ENVIRONMENT.md) | JDK, NDK, JNI layout |
| [PADDLE_BUILD.md](specs/PADDLE_BUILD.md) | How this repo builds Paddle-Lite |
| [PADDLE_PIN_BUILDS.md](specs/PADDLE_PIN_BUILDS.md) | How a Paddle pin is recorded and rebuilt |
| [THIRD_PARTY_PIN_BUILDS.md](specs/THIRD_PARTY_PIN_BUILDS.md) | How vendored pins are materialized and audited |
| [HOST_PADDLE_USE.md](specs/HOST_PADDLE_USE.md) | Host-side Paddle `opt` and benchmark contract |

## obsolete

What we tried, how it worked, and why we stopped. Includes old classifier rules, old odometer notes, saved Paddle PR text, the JNI-mismatch investigation, the parallel harness note, and two history notes (merge postmortem, oversize-blob purge).
