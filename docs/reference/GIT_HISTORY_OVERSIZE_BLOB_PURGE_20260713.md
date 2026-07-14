# Git history: oversize Paddle JNI blob purge (2026-07-13)

GitHub rejected push (GH001) because historical commits contained plain-git blobs over 100 MB:

| Path | Approx. size (pre-purge) |
|------|-------------------------:|
| `app/src/main/jniLibs/x86_64/libpaddle_light_api_shared.so` | ~283 MB |
| `app/src/main/jniLibs/arm64-v8a/libpaddle_lite_jni.so` | ~189 MB |

Those blobs were introduced on the `master` line at `b2260fb9` (*paddle: ship multi-path lite jni + jar*). Tip retains slim/tailored replacements validated for prod `uint8_fp16_u8`.

## What we did

- `git filter-repo --strip-blobs-bigger-than 100M` on the shared object store (2026-07-13).
- Pre-rewrite backup branch: `backup/pre-gh-purge-20260713` (points at pre-purge tip).
- Sandbox inventory: `dev-ai-interaction/purge-20260713-obsolete-sha-inventory.md`, commit-map: `purge-20260713-commit-map.txt`.

## Broken window (intentional)

Any historical checkout whose tree depended **only** on the fat multi-kernel JNI libs (roughly `b2260fb9` through pre-slim/tailor commits) is **not** fully reproducible for native Paddle from git alone after this purge. Intermediate commits may lack those `.so` paths entirely. Restore fat binaries only from offline backups if ever needed.

## Obsolete doc pins (do not delete tags)

Commits referenced from `docs/obsolete/` are anchored by lightweight tags so `git gc` cannot drop the trees:

| Tag | Commit (unchanged by purge) |
|-----|----------------------------|
| `obsolete-ALIGNMENT_ALGORITHMS` | `c51809dc4a539dcf285832a0d5d7577b0d731c04` |
| `obsolete-DISCOVERY_ENGINES` | `c51809dc4a539dcf285832a0d5d7577b0d731c04` |
| `obsolete-IDENTITY_ALGORITHMS` | `c51809dc4a539dcf285832a0d5d7577b0d731c04` |
| `obsolete-TEST_HARNESSES` | `c51809dc4a539dcf285832a0d5d7577b0d731c04` |
| `obsolete-REFINEMENT_STRATEGIES` | `cdfd1ac040d7bf81feaf6b46724e1b7bbd934269` |
| `refinement` | `cdfd1ac040d7bf81feaf6b46724e1b7bbd934269` (legacy alias) |

Obsolete-doc SHAs were verified unchanged via filter-repo commit-map (identity rows).