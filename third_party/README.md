# third_party — pin, materialize, build, collect artifacts

**Audience:** any developer (human or automated) who clones this tree and needs to **audit, reproduce, or tweak** custom builds of external libraries.

**Detail:** `docs/reference/THIRD_PARTY_PIN_BUILDS.md`  
**Toy example:** `third_party/example/`  
**Future tooling name (if extracted):** *libpin* — name appears free on GitHub / PyPI / npm as of 2026-08-03; leave in-tree until OpenCV + rclone + paddle + remotetable/extractmail profiles all work.

---

## What to do (happy path)

```bash
# From the VehicleExpenses worktree root (or any consumer that vendors this layout)
./third_party/fetch-deps ro opencv          # materialize src @ lock SHA, apply patches, sources RO
./third_party/fetch-deps build opencv       # run build script(s), then collect → artifact/
# or, when iterating a build only:
./third_party/get-artifacts opencv          # copy from src build outputs → destinations in libpin.toml
```

Same pattern for `remotetable`, `extractmail`, `rclone`, `paddle` (when pins are real).

| Step | Tool | Responsibility |
|------|------|----------------|
| 1. Materialize | `fetch-deps ro` / `rw` | **Git pins:** `src/` at `git_sha` (+ `patches/`). **File pins:** `[[source]]` rows (HTTPS/`file:`/`seed` + required `sha256`; **no SSH**). Default RO tree |
| 2. Build | `./build` (or scripts listed in lock) | Create/chmod **writable** `src/build`, `src/bin` (or upstream-equivalent dirs); compile; leave products under `src/…`. Explicit `build = []` skips exec (pure source pins) |
| 3. Collect | `get-artifacts` (called by `fetch-deps build`) | Copy products to **`path`** destinations in `libpin.toml` (pin `artifact/` and/or consumer paths like `app/src/main/jniLibs/…`) |

### Non-git pins (`[[source]]`)

For content that is not a git tree (e.g. tailor `.nb` inputs):

```toml
name = "paddle-models"
source_kind = "files"
build = []

[[source]]
seed = "seed/armv8/det_armv8.nb"   # offline copy under the pin
# url = "https://example.com/det_armv8.nb"  # optional remote (never git@)
path = "armv8/det_armv8.nb"        # under src/ after materialize
sha256 = "…"
```

```bash
./third_party/fetch-deps ro paddle-models   # → third_party/paddle-models/src/…
```

**Hash policy:** `sha256` is the pin. Cache hit only if hash matches. Stale cache → **one** re-fetch, then **hard fail** if still wrong. If a remote URL moves, update `sha256` (and seed) in `libpin.toml` — fetch-deps will not spin waiting for the old bytes to return.

### `[[artifact]]` destinations

Each section is independent (same `from` may appear twice).

| `path` | Resolved as |
|--------|-------------|
| `artifact/…` or other pin-relative | `third_party/<lib>/<path>` |
| `app/…` | `<repo-root>/app/…` (VE jniLibs, jars, …) |
| absolute | as written |

Optional `sha256` — row fails if the chosen `from` file does not match.

### Optional write sandbox (bubblewrap and/or Landlock)

`libpin-sandbox` wraps **fetch-deps / build** when available (Linux). **Missing tools → unsandboxed, still correct.**  
Landlock matters most when **executing retrieved sources**, not when copying our own build outputs.

| Layer | What | Install / detect |
|-------|------|------------------|
| **bubblewrap** | Outer mount jail: RO root + RW hole | `sudo apt install bubblewrap` (`bwrap`) |
| **Landlock** | LSM: mutation only under allowed dirs (read/exec unrestricted) | Kernel with `landlock` in LSM (`./third_party/libpin-landlock --status`) |

Order: **bwrap outer → Landlock inner** (Landlock stacks; nested bwrap does not).

| Step | Writable only (mutation) |
|------|---------------------------|
| Materialize + `status.local` | `third_party/<lib>/` |
| Patches / build | `third_party/<lib>/src/` (+ `/tmp` under Landlock) |
| `get-artifacts` | **No sandbox by default** (may write `app/…`). Opt-in: `LIBPIN_GET_ARTIFACTS_SANDBOX=1` (artifact dir only). |

Disable fetch/build sandbox: `LIBPIN_NO_BWRAP=1`, `LIBPIN_NO_LANDLOCK=1`, or `--no-bwrap` / `--no-landlock`.  
Debug: `LIBPIN_BWRAP_DEBUG=1`, `LIBPIN_LANDLOCK_DEBUG=1`.

**Committed pin surface for the app:** `libpin.toml` + build scripts + patches (+ often `artifact/*` and/or app `jniLibs` after collect).  
**Not committed on a fresh clone:** usually `src/` contents (materialize with fetch-deps). `src` must still be a **real git checkout** when present (submodule, worktree, or clone) so `git status` works.

---

## Layout (every library)

```text
third_party/<lib>/
  libpin.toml       # pin identity + [[artifact]] + build_time + reproducible?
  SOURCE.md       # human narrative (profiles, gotchas)
  patches/        # optional; applied only by fetch-deps
  build           # executable entry (may call scripts/)
  scripts/        # optional helpers
  src/            # materialized sources (git tree @ pin)
  artifact/       # STABLE outputs VE (or other consumers) use
  status.local    # gitignored — ro/rw mode (not the pin)
```

Optional local accelerator: full clone under `GIT_HOME` (e.g. `~/git/opencv`). fetch-deps may attach a **worktree** at `src/` so history stays outside the app tree. **Correctness does not require GIT_HOME.**

---

## Build-time t-shirt sizes (`build_time` in lock / SOURCE)

| Value | Meaning |
|-------|---------|
| `minutes` | Roughly under ~10 minutes |
| `tens_of_minutes` | ~10–60 minutes |
| `few_hours` | ~1–4 hours |
| `tens_of_hours` | long Docker / full SDK rebuilds |

---

## Profiles (same layout, different build stories)

| Profile | Example | Materialize | Build |
|---------|---------|-------------|--------|
| Options-only | **opencv** | pin SHA | Script + flags (ABIs, 16k pages, module list) |
| Patch + wrap | **rclone** | pin + patches | Script (± Docker) → Kotlin/gomobile lib |
| Docker + models | **paddle** | pin (fork commit) | Containers, old toolchains, model post-steps |
| Active co-dev | **remotetable / extractmail** | `ro` for audit/rebuild; `rw` while co-developing | Kotlin AAR via Gradle; RO unlocks only `android/.gradle` + module `build/`; **reproducible=true** (same toolchain) |

---

## Commands (fetch-deps)

```text
./third_party/fetch-deps ro [NAME…]     # default: pin + patches + RO
./third_party/fetch-deps rw [NAME…]     # writable branch for co-development
./third_party/fetch-deps build [NAME…]  # build + get-artifacts
./third_party/fetch-deps status [-v]
./third_party/get-artifacts [NAME…]     # collect only (iterate builds)
```

`--depth N` — optional shallow clone (not default). See `fetch-deps --help`.

---

## Do not

- Apply patches in the **build** script (fetch-deps already did).
- Leave app-consumed binaries **only** under `src/` (they vanish without materialize).
- Treat `~/git/...` as the pin if `src/` was never materialized.
- `git add` a full non-submodule dump of library sources into the app repo.
