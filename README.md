# Vehicle Expenses Automated

Camera-first tracking for fuel fill-ups and other vehicle expenses. Quick Fill reads the dashboard odometer and the pump display, matches the vehicle from the dash, and records the station from the phone’s location. Expenses, trips, and reports stay on the device. Spreadsheet sync and photo backup use accounts you control.

## Read next

| Doc | What it is |
|-----|------------|
| [Quick guide](docs/QUICK_GUIDE.md) | Everyday use, without screenshots |
| [Full manual](docs/user-manual/index.html) | Illustrated steps. Edit [manual.md](docs/user-manual/manual.md) |
| [Overview](docs/OVERVIEW.md) | What the app is for and how the pieces fit |
| [Docs index](docs/README.md) | Map of `docs/` |
| [Environment setup](docs/ENVIRONMENT_SETUP.md) | Build a clone, SDK/NDK, and this multi-worktree host |

Third-party libraries are pinned under `third_party/`. The contract is [docs/specs/THIRD_PARTY_PIN_BUILDS.md](docs/specs/THIRD_PARTY_PIN_BUILDS.md). A short command list is in [third_party/README.md](third_party/README.md).

## Adb data transfer

Hidden provider, adb shell or root only. Full notes: [docs/reference/DATA_TRANSFER.md](docs/reference/DATA_TRANSFER.md). The test app authority is `com.davidlang.vehicleexpensesautomated.test.datatransfer`.

```
adb exec-out content read --uri content://com.davidlang.vehicleexpensesautomated.datatransfer/export > backup.tar
adb exec-in content write --uri content://com.davidlang.vehicleexpensesautomated.datatransfer/import < backup.tar
adb exec-out content read --uri content://com.davidlang.vehicleexpensesautomated.datatransfer/import-status
```

`exec-in` hides errors, so read import-status after every import until `applied` is true or `result` is `error`. Never redirect through `adb shell`. Probe `exec-in` before the first transfer:

```
probe="ve-exec-probe-$(date +%s)"
printf '%s\n' "$probe" | adb -s <serial> exec-in 'cat > /data/local/tmp/ve-exec-probe'
got=$(adb -s <serial> exec-out cat /data/local/tmp/ve-exec-probe)
if [ "$got" != "$probe" ]; then
  echo "ERROR: adb exec-in round trip failed (sent '$probe', read back '$got')" >&2
  exit 1
fi
adb -s <serial> exec-out rm /data/local/tmp/ve-exec-probe
```

Export strips Zoho tokens, so sign in again after import. Export stays in the process. Import restarts, and the next launch applies the swap.
