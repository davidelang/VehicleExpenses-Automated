# Adb data transfer

Hidden provider. Authority `${applicationId}.datatransfer`. The production id is `com.davidlang.vehicleexpensesautomated`. The test app (`qa`, `applicationIdSuffix` `.test`) uses `com.davidlang.vehicleexpensesautomated.test.datatransfer`. Only adb shell (uid 2000) or root can call it.

Replace `<id>` below with that authority's package id.

```
adb exec-out content read --uri content://<id>.datatransfer/export > backup.tar
adb exec-in content write --uri content://<id>.datatransfer/import < backup.tar
adb exec-out content read --uri content://<id>.datatransfer/import-status
```

Production examples:

```
adb exec-out content read --uri content://com.davidlang.vehicleexpensesautomated.datatransfer/export > backup.tar
adb exec-in content write --uri content://com.davidlang.vehicleexpensesautomated.datatransfer/import < backup.tar
adb exec-out content read --uri content://com.davidlang.vehicleexpensesautomated.datatransfer/import-status
```

Always use `adb exec-out` and `adb exec-in`. Never redirect with `adb shell` (`adb shell ... > file` or `< file`).

`exec-in` hides errors: on platform-tools 34.0.5 it exits 0 and prints nothing even when the device command fails. Import errors are only in `/import-status` (`lastImport`). After every import, read import-status until `lastImport.applied` is true or `lastImport.result` is `error`. One immediate read can still show `applied: false`, because the swap runs in the next process.

Export errors do show on the `exec-out` stream.

Before the first transfer, probe `exec-in` on the selected device. On a mismatch, stop. Do not substitute `adb shell`.

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

Zoho access and refresh tokens inside sync-destination settings are stripped on export. After import, sign in again. The live export path checkpoints and copies inside the running process and does not restart it. Import writes a commit marker and restarts; the next launch swaps staged data into place.
