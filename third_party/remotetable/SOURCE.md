# remotetable — source pin (VehicleExpenses)

| Field | Value |
|-------|--------|
| Canonical | `git@github.com:davidelang/remotetable.git` |
| Host | `/home/dlang/git/remotetable` |
| Lock | `lock.yaml` |
| Artifact | `artifact/remotetable.aar` (when built) |

## M1 status (2026-08-02)

Library host now has:

- `spec/OPS.md` + `OVERVIEW.md`
- Python `RemoteTable` + `MockBackend`; stubs for `google-sheets`, `excel-graph`, `ethercalc`
- `conformance/harness.py` (mock PASS)
- Kotlin API sketch under `kotlin/`
- Go mock package under `go/remotetable/`

**Pin still `TBD`:** library `.git` is not writable by `ai-coder` (owner `dlang:dlang`). Human must commit on library host, then set `git_sha` / `git_describe` and commit AAR here.

## Build

```bash
# on library host after commit:
python3 conformance/harness.py
# AAR packaging TBD; third_party/remotetable/build currently stub
./third_party/remotetable/build
```
