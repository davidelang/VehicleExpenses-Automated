# paddle — third_party pin (VehicleExpenses)

## Policy
`docs/reference/FIRST_PARTY_LIBS.md` (Docker **build instructions** in meta; **no** image blobs in git).

## Authoritative build docs (tracked app docs)
- `docs/specs/PADDLE_BUILD.md`
- `docs/specs/BUILD_ENVIRONMENT.md`
- `docs/specs/HOST_PADDLE_USE.md`
- `docs/specs/PADDLE_PR_DESCRIPTIONS.md`

## Sandbox recipes (until fully migrated)
`/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/paddle-build/`
- `Dockerfile`, `Dockerfile.int8`, `apply_int8_patches.sh`, slim arm64/x86_64 scripts
- Significant INT8/u8 patches + `patchelf` soname fix for JNI

## Host setup (planned)
- `~/git/paddle` — patched fork checkout (URL TBD in lock when published)
- VE pin: this directory; artifacts land as jniLibs / predictor jar when build script is wired

## Current ship path
App still uses checked-in `app/src/main/jniLibs/**` + `app/libs/PaddlePredictor.jar` + prod models under assets.
