# rclone — third_party pin (VehicleExpenses)

## What this is

Consumer-side pin directory for **rclone** under VehicleExpenses `third_party/`.

- **Policy:** `docs/reference/FIRST_PARTY_LIBS.md`
- **Agent handoff:** `docs/reference/THIRD_PARTY_LAYOUT_FOR_AGENTS.md`
- **Lock:** `libpin.toml` (SSH + HTTPS URLs, sha, describe, artifacts)
- **Build:** executable `build` (and/or `build-*`); run via `../fetch-deps --build rclone`
- **Artifact:** stable path under `artifact/` (version only in lock)
- **Source:** prefer **git submodule** at `src/` matching `git_sha`

## Audit (third party)

```bash
./third_party/fetch-deps --readonly rclone
# inspect libpin.toml git_sha + src/ tree + artifact/ + this file
```

Without reproducible builds, `artifact` sha256 (if present) detects change/tamper, not bit-identical rebuild.

## Bootstrap status

Point build at sandbox/dev-ai-interaction/rclone-build recipes until migrated. Artifact may later mirror app/libs/librclone.aar.

## Local multi-agent host (2026-08-02)

- Checkout: `~/git/rclone` (branch `email-connection` includes `ve-build/` recipes)
- VE pin: this directory (`libpin.toml` + `artifact/librclone.aar`)
- Rebuild: `./third_party/rclone/build` (Docker) or `cd ~/git/rclone/ve-build && docker build ...`
- Interim artifact: photo AAR copied from historical sandbox `rclone-build/librclone_photo.aar`
