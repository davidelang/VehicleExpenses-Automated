# rclone pin

| | |
|--|--|
| **Upstream** | `rclone/rclone` @ `3f9d583cffd94105f3ae2cde2cc6c0d5a2c9d631` (master tip when pinned) |
| **Profile** | **Patch + wrap** — Docker/gomobile wrapper under `scripts/`; no permanent fork commit |
| **build_time** | `tens_of_minutes` (Docker image first time + gomobile bind) |
| **reproducible** | `false` |
| **Product** | `artifact/librclone.aar` (photo-curated backends) |

## Reproduce

```bash
# Docker required
./third_party/fetch-deps ro rclone
./third_party/fetch-deps build rclone
```

Optional: `GIT_HOME=~/git` with **`~/git/rclone` pure upstream** (master) speeds materialize. Do **not** keep VE `ve-build/` on that host — recipes live only under this pin’s `scripts/`.

## Host layout

| Path | Role |
|------|------|
| `~/git/rclone` | Full upstream clone (object store); checkout at pin SHA via fetch-deps |
| `third_party/rclone/src/` | Materialized pin tree (gitignored; RO after `ro`) |
| `third_party/rclone/scripts/` | Docker + photo AAR bind (carry forever) |
| `third_party/rclone/artifact/librclone.aar` | What the app links |

## Wrapper (why not just upstream?)

Upstream `librclone/gomobile` imports **all** backends. VE ships a **photo-curated** set (Drive, OneDrive, S3, WebDAV, SFTP, …) and **compile-outs** local/memory/http/etc. (see `RcloneProviderCatalog.COMPILED_OUT_TYPES` in the app).

That curation is applied **inside the build container** to a **copy** of the tree (`scripts/build-photo-aar.sh` rewrites `librclone/gomobile/gomobile.go`). The pin `src/` is never dirtied.

Also:

- `CGO_LDFLAGS=-Wl,-z,max-page-size=16384` for Android 15+ 16KB pages
- Targets: `android/arm`, `android/arm64`, `android/amd64` (armeabi-v7a + arm64-v8a + x86_64)

## Layout under `src/` after build

- `src/` — clean git tree @ pin (RO after `fetch-deps ro`)
- `src/build/out/librclone_photo.aar` — product before collection  
  (**not** `src/bin/` — that path is upstream rclone helper scripts)
- `artifact/librclone.aar` — stable pin (via `get-artifacts`)

## Scripts

| Path | Role |
|------|------|
| `build` | Host entry: Docker image + run photo AAR |
| `scripts/Dockerfile` | Go 1.25 + NDK r21 + gomobile |
| `scripts/build-photo-aar.sh` | Backend prune + `gomobile bind` |
| `patches/` | Empty for now (build-time rewrite only) |

Historical sandbox copies: `dev-ai-interaction/rclone-build/`. Host `~/git/rclone` `ve-build/` is superseded by this pin layout.

## Upstream vs carry-forever

rclone **already ships** `librclone/gomobile` (official AAR bind path). Android apps (RCX, Round-Sync) embed it.

What VE carries forever under this pin (post-clone build, **not** a long-lived fork commit):

- Photo-curated backend import list / compile-outs
- Docker + NDK + `gomobile bind` recipe + 16KB `CGO_LDFLAGS`

Do **not** put the curation into the rclone git tree for an upstream PR — product-specific. Optional later: docs/CI PR only (see closed #7342; ncw invited a fresh PR). AI-assisted contributions are welcome if human-owned and tested (`CONTRIBUTING.md` / `AGENTS.md`).
