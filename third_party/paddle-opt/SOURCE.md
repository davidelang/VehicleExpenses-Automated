# paddle-opt — host linux-x86_64 Paddle-Lite `opt`

Builds only the host model optimizer (`opt`) used by `third_party/paddle-models` to
write the `.nb` models. Same Paddle-Lite pin as `third_party/paddle`
(`git_sha` c6a9b9ada00e3b37aa80c21407e7d8e240595ebb); the local checkout comes from the
host repo named by `host_repo = "paddle"` (no network clone when it is missing).

Build order: **paddle-opt → paddle-models → paddle** (the library tailor consumes the
kernel/ops lists that paddle-models writes with this opt).

## Overlays

`./build` copies, fresh on every build, into `src/work/overlay/`:

- `../paddle/patches/`, `../paddle/patches-int8/`
- `../paddle/scripts/apply_patches.sh`, `../paddle/scripts/apply_int8_patches.sh`

`third_party/paddle` stays the source of truth for those files; nothing is pinned or
duplicated here. Then `patches-opt/` (opt-only) is copied on top:

- `patches-opt/code/lite/api/paddle_place.cc`: adds `"kUInt8"`, `"kFP64"` after
  `"kInt16"` in the precision-name table, so opt's kernel-list writer names those
  precisions (the Android library does not need it).
- `patches-opt/code/lite/model_parser/model_parser.cc`: `SaveModelNaive` writes the
  16-byte `opt_version` header field (bytes 2-17 of the `.nb`) from a zero-initialized
  buffer. Upstream wrote 16 bytes from `version().c_str()`; in this build the version is
  empty (the container's `git log` cannot reach the host objects behind
  `.git/objects/info/alternates`, so `PADDLE_LITE_COMMIT` is empty), so bytes after the
  terminator were uninitialized and `.nb` sha256 varied run to run. The version string
  itself stays empty, matching the Android library (loader only compares and, for
  meta_version 2, warns). The build fails if the marker `VE_ZERO_PADDED_OPT_VERSION` is
  missing after the overlay copy.

All overlay inputs are mounted read-only in the container; only `src/bin/linux-x86_64`
is writable.

## Build

```
LIBPIN_NO_BWRAP=1 ./third_party/fetch-deps ro paddle-opt
LIBPIN_NO_BWRAP=1 ./third_party/fetch-deps build --record paddle-opt
```

Docker image `ve-paddle-ndk28c` (override with `PADDLE_DOCKER_IMAGE`); a missing image
fails, there is no fallback. `-ffast-math` is stripped unless `PADDLE_ALLOW_FAST_MATH=1`.
Gates: `strings opt` contains `analytic_input_mean`, `analytic_input_std`,
`analytic_input_quant_pass`, `output_calib_pass`, `kUInt8`; `opt --help` lists
`--analytic_input_mean`; then `build-opt: OK`.

Artifact: `src/bin/linux-x86_64/opt` → `artifact/linux-x86_64/opt` (sha256 recorded by
`--record`; consumed by `paddle-models` `[[source]] path = "tools/opt"`).
