# third_party layout — short handoff

**Read first:** `third_party/README.md`  
**Full pin/build/audit contract:** `docs/reference/THIRD_PARTY_PIN_BUILDS.md`  
**Library hosts (remotetable/extractmail orchestration):** `docs/reference/FIRST_PARTY_LIBS.md`

This file is a thin pointer so older links keep working. Process language is for **developers** (human or automated), not a separate “agent-only” layout.

## Commands

```bash
./third_party/fetch-deps ro [name…]
./third_party/fetch-deps build [name…]
./third_party/get-artifacts [name…]
```

## Hard rules (summary)

1. **`src/`** is not app product source — it is the pinned library (or upstream) tree. Prefer a real **git** checkout (submodule, worktree, or clone).
2. Do not `git add` a full non-submodule dump of library sources into the app repo.
3. **Patches** only via fetch-deps at materialize.
4. **Artifacts the app uses** live under **`artifact/`** (collected by get-artifacts), not only under `src/`.
5. Build scripts may create writable dirs under `src` (e.g. `build/`, `bin/`); default sources are RO after `fetch-deps ro`.

## OpenCV note

Later branch work is in progress on `email-connection`; pin process is the same as any other lib once `lock.yaml` + `./build` + artifacts are correct.
