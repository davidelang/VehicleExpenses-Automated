# Paddle-Lite upstream PR restack (VE durable notes)

**Canonical location:** `third_party/paddle/docs/upstream/` in the VehicleExpenses tree  
(Do **not** rely on files only under `~/git/paddle` — that tree is rebased/cleaned often.)

Agent rule: **no git push** to GitHub; **fetch/pull via HTTPS only**.

## Branches (local + origin fork as of restack)

Rebased on `upstream/develop` @ `8c07d68f8`:

| Branch | Tip (restack) | Role | Existing open PR |
|--------|---------------|------|------------------|
| `pr-upstream-cleanup-restack` | `81b6abea0` | PR1 → base `develop` | [#10712](https://github.com/PaddlePaddle/Paddle-Lite/pull/10712) |
| `pr-x86-android-mobile-gap-restack` | `44b78dc75` | PR2 → base cleanup | [#10713](https://github.com/PaddlePaddle/Paddle-Lite/pull/10713) |
| `pr-calib-safe-uint8-dequant-restack` | `4563e88bd` | PR3 → base x86-gap | [#10714](https://github.com/PaddlePaddle/Paddle-Lite/pull/10714) |

Local tips may be ahead of `origin/*-restack` until **you** push (agents do not push).

**Ancestry:** cleanup ⊂ x86-gap ⊂ uint8.

Each tip commit body ends with **`test=develop`** (Paddle docs: required to trigger CI).

### vs old PR tips
- Dropped local PR markdown from git history of the stack (messages live here under `docs/upstream/PR_*.md`).
- Folded OpenBLAS size strip + **mklml `LITE_WITH_MKL` gate** into x86 PR.
- `ANDROID_NDK`-relative sysroot (no hardcoded `/opt/android-ndk-r20b`).
- clang-format on touched C/C++ (project `.clang-format`).
- uint8 rebased onto x86 tip (was parallel on bare develop).

## Suggested PR bodies (copy-paste)

| File | Use for |
|------|---------|
| [PR_UPSTREAM_CLEANUP.md](./PR_UPSTREAM_CLEANUP.md) | #10712 / cleanup |
| [PR_X86_ANDROID.md](./PR_X86_ANDROID.md) | #10713 / x86 mobile gap |
| [PR_CALIB_SAFE_UINT8.md](./PR_CALIB_SAFE_UINT8.md) | #10714 / uint8 dequant |

Template fields match `.github/PULL_REQUEST_TEMPLATE.md`: **PR devices**, **PR types**, **PR changes**, **Description**.

## Why GitHub CI looked stalled

1. Checks showed only **CLA** green (`license/cla`).
2. Commits lacked **`test=develop`** → project docs say CI will not run.
3. No in-repo GitHub Actions Android build; Travis job is **pre-commit only** (`.travis.yml` → `.travis/pre-commit-job.sh`).
4. Code owners: `@zhupengyang` `@hong19860320` — review requested, no activity.

## Local checks before you update PRs

```bash
cd ~/git/paddle
git checkout pr-calib-safe-uint8-dequant-restack

# Travis-equivalent style (needs pre-commit + clang-format + cpplint on PATH)
# Note: Travis used clang-format-3.8; modern clang-format may reformat more aggressively.
export PATH="/path/to/venv/bin:$PATH"
pre-commit install
pre-commit run -a

# Android smokes (docker image with NDK r20b works)
./lite/tools/build_android.sh --arch=x86_64 --toolchain=clang --with_java=ON \
  --with_cv=OFF --with_extra=ON --with_log=OFF --with_benchmark=OFF \
  --android_stl=c++_static --with_exception=ON
./lite/tools/build_android.sh --arch=armv8 --toolchain=clang --with_java=ON \
  --with_cv=OFF --with_extra=ON --with_log=OFF --with_benchmark=OFF \
  --android_stl=c++_static --with_exception=ON --with_arm82_fp16=ON
# arm: strings/grep uint8_to_fp16 int8_to_fp32 on libpaddle_* .so
```

**Already run on restack tip (docker `ve-paddle-int8`):**
- x86_64 JNI: OK  
- armv8 JNI + uint8 stamps: OK  

**Style (Travis-like):**
- Hooks that **passed** with modern pre-commit (patched `sha`→`rev`): CRLF, large files, merge conflict, symlinks, private key, EOF, **cpplint**, **copyright**.
- **clang-format** requires version string containing `3.8` (see `tools/codestyle/clang_format.hook`). Host clang-format 22 fails the version check.
- Re-ran **clang-format-3.8** (Ubuntu 16.04 package in docker) on all C/C++ files in the stack diff; folded into restack commits.
- **cpplint** on stack: 24 `whitespace/indent_namespace` hits in `im2col.cc` (signed-char explicit instantiations). Upstream `develop` copy of the same file already has similar namespace-indent nits (16 errors). Matches local style of that file; not new policy violations.

## Force-update existing PRs vs new set

**Recommend force-update the three existing PRs** (keep numbers, history of discussion) **if**:

- You set bases correctly (develop ← cleanup ← x86 ← uint8).
- You force-with-lease the three head branches to the restack tips.
- You replace PR bodies with the files in this directory.

**Open a new stacked set** only if force-push is painful for reviewers or bases cannot be retargeted cleanly.

```bash
# YOU push + update PR titles/bodies (agents do not push to GitHub)
# From VE worktree or anywhere:
./third_party/paddle/docs/upstream/push-and-update-prs.sh
# Preview only:
DRY_RUN=1 ./third_party/paddle/docs/upstream/push-and-update-prs.sh
```

Script: force-with-lease push restack → PR heads, `gh pr edit` title/body from `PR_*.md`, best-effort stacked bases.

## VE pin patch reduction (after pin retarget to uint8 tip)

After `libpin.toml` `git_sha` → tip of uint8 restack:

| In git tip now | Still need pin overlays |
|----------------|-------------------------|
| cleanup + x86-gap + mkl gate | `patches-int8` analytic quant, output calib, opt, MobileConfig/JNI, light_api keep_quantized, etc. |
| uint8 type_trans / calib core | Re-diff before deleting; patches-int8 may still differ (product extras) |

`patches-x86-openblas` can go once pin is on restack tip (mkl gate is in x86 commit).

## Related VE docs

- `docs/reference/PADDLE_PIN_BUILDS.md` — pin/build variations  
- `third_party/paddle/SOURCE.md` — pin contract  
