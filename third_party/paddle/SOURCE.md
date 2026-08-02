# paddle — third_party pin (VehicleExpenses)

## What this is

Consumer-side pin directory for **paddle** under VehicleExpenses `third_party/`.

- **Policy:** `docs/reference/FIRST_PARTY_LIBS.md`
- **Agent handoff:** `docs/reference/THIRD_PARTY_LAYOUT_FOR_AGENTS.md`
- **Lock:** `lock.yaml` (SSH + HTTPS URLs, sha, describe, artifacts)
- **Build:** executable `build` (and/or `build-*`); run via `../fetch-deps --build paddle`
- **Artifact:** stable path under `artifact/` (version only in lock)
- **Source:** prefer **git submodule** at `src/` matching `git_sha`

## Audit (third party)

```bash
./third_party/fetch-deps --readonly paddle
# inspect lock.yaml git_sha + src/ tree + artifact/ + this file
```

Without reproducible builds, `artifact` sha256 (if present) detects change/tamper, not bit-identical rebuild.

## Bootstrap status

Sandbox paddle-build remains until post-merge cleanup. lock URLs TBD for internal fork.
