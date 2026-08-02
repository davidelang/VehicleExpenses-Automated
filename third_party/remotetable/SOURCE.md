# remotetable — third_party pin (VehicleExpenses)

## What this is

Consumer-side pin directory for **remotetable** under VehicleExpenses `third_party/`.

- **Policy:** `docs/reference/FIRST_PARTY_LIBS.md`
- **Agent handoff:** `docs/reference/THIRD_PARTY_LAYOUT_FOR_AGENTS.md`
- **Lock:** `lock.yaml` (SSH + HTTPS URLs, sha, describe, artifacts)
- **Build:** executable `build` (and/or `build-*`); run via `../fetch-deps --build remotetable`
- **Artifact:** stable path under `artifact/` (version only in lock)
- **Source:** prefer **git submodule** at `src/` matching `git_sha`

## Audit (third party)

```bash
./third_party/fetch-deps --readonly remotetable
# inspect lock.yaml git_sha + src/ tree + artifact/ + this file
```

Without reproducible builds, `artifact` sha256 (if present) detects change/tamper, not bit-identical rebuild.

## Bootstrap status

Seed host: ~/git/remotetable. Product seed from sandbox subprojects. No artifact yet (git_sha TBD).
