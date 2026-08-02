# extractmail — source pin (VehicleExpenses)

| Field | Value |
|-------|--------|
| Canonical | `git@github.com:davidelang/extractmail.git` |
| Host | `/home/dlang/git/extractmail` |
| Lock | `lock.yaml` |
| Artifact | `artifact/extractmail.aar` (when built) |

## M1 status (2026-08-02)

Library host now has:

- `spec/OVERVIEW.md`, `spec/EXTERNAL.md` (stdin / exit 0/1/2)
- `extractors/shell-ereceipt.yaml`, `samsclub-fuel.yaml` + `reference-js/`
- Fixtures + `python/extractmail_stdin.py` + `python/run_goldens.py` (**goldens PASS**)
- Apps Script under `apps-script/` (same fixtures)

**Pin still `TBD`:** library `.git` not writable by `ai-coder`. Human commits on library host, then VE bumps pin + artifact.

## Conformance (host)

```bash
python3 python/run_goldens.py
# expects: 144.77/32.036, 52.34/13.12, 136.30/29.069
```

## VE app

In-app Gmail/IMAP + parsers under `app/.../data/email/` remain until extractmail AAR cutover; new extract features go to extractmail first.
