# VE→plugin fill contract

This file is the fill contract.

One phone runs VehicleExpenses and the plugin. VE sends each opted-in fill, after the existing merge, to the local plugin. A missing app, a failed bind, or a failed send is silent. Do not start Torque or a foreground service. There is no historical backfill.

## Endpoint
- Exported service `com.davidlang.torque.fuelsmoothing.FillEventBridgeService`, action `com.davidlang.torque.fuelsmoothing.action.BIND_FILL_EVENT_BRIDGE`.
- AIDL, copied unchanged: `app/src/main/aidl/com/davidlang/torque/fuelsmoothing/fillbridge/IFillEventBridge.aidl`.
```aidl
package com.davidlang.torque.fuelsmoothing.fillbridge;
interface IFillEventBridge {
    String getCapabilities();
    String submitFillBatch(String batchJson);
}
```
- Bind with `BIND_AUTO_CREATE`. At most 16 events and 32768 UTF-8 bytes per request.

## Vehicle and storage
- The vehicle record stores `odometerUnit` as `mi` or `km`. Stored odometer integers are in that unit. Stored volume is US gallons. Neither is rewritten for the wire. The wire field is `displayUnits`: `metric` when the stored unit normalizes to kilometers, `imperial` otherwise. A new payload has no `odometerUnit` key.
- Wire `vin` is 17 ISO characters. When the first character is `1`-`5`, the North-American check digit must pass. Any other stored text, including blank, is null on the wire. Stored VIN text is unchanged.
- Quantity on the wire is always liters: stored gallons times `3.785411784`, half-up to at most 9 fractional digits, trailing zeros stripped.
- Odometer on the wire is always kilometers. A stored odometer of 0 is omitted, not sent as zero. Zero A6 is not a reading.
- A Torque source sends its stored tenth-kilometer text as kilometers (`tenths / 10`) and repeats that decimal in `rawTorqueOdometer` with `resolutionKm` `"0.1"`. Any other positive reading is the stored integer converted with the mile factor `1.609344`, or the kilometer integer unchanged.
- Cost keeps its currency. `economyIgnored` is its own boolean and does not change `fillExtent`.

## fillExtent
- `full` when odometer, cost, and volume are present and the partial checkbox is off.
- `partial` when that checkbox is on.
- `unknown` when one of those three amounts is missing.
- A void uses `unknown`, `economyIgnored` false, and null quantity, cost, odometer, and raw odometer.

## Provenance
- Provenance names the odometer source and the cost source and the volume source.
- Odometer `source` on the reading is `torque`, `photo`, `manual`, or `unknown`. Field provenance maps stored `photo` to `ocr` and otherwise uses `torque`, `manual`, `email`, `ocr`, `sync`, `mixed`, or `legacy_unknown`.
- Email cost and volume stay the authority when the merge copied them. A pump save is `ocr` when that field still holds the OCR text, otherwise `manual`.
- Unassigned rows are stored and are not queued. An opted-out vehicle produces no outbox row.

## Revisions and outbox
- One local producer id. One high-water per fuel `syncId`. The high-water moves when a revision is allocated, in the same transaction as the source write. An acknowledgement does not move it.
- A no-op sync allocates nothing. Writes while the vehicle is off allocate nothing. Turning it on does not scan old rows. The next real edit while on uses high-water + 1 and sends the row as it is then.
- Switching the vehicle off deletes that vehicle’s pending rows and does not lower the high-water. A call already handed to the binder is not recalled. Its late acknowledgement does not restore a pending row and does not lower the high-water.
- `ACCEPTED` and `DUPLICATE` clear that pending item. `CONFLICT` and `INVALID_EVENT` stay pending and are not retried until the source changes. `STALE` drops that older pending item. One item’s result does not clear the rest of the batch.
- When a sent donor `syncId` is deleted by the merge, the same transaction enqueues a void. A donor that was never sent gets no void. Merge pairing is unchanged: 15 minutes, location when it matches, time alone when it does not.
- Startup retries whatever is still pending.

## Payload
Every listed member is present, including nulls. Objects are canonical: UTF-8, no whitespace, ASCII-sorted keys, NFC strings. SHA-256 is lowercase hex of the canonical payload only.

```text
{
  operation: upsert|void,
  vehicle: {
    syncId, vin: 17 ISO characters|null, name, make, model, year,
    displayUnits: imperial|metric
  },
  occurredAtEpochMs,
  quantity: {value, unit: L}|null,
  cost: {value, currency}|null,
  fillExtent: full|partial|unknown,
  economyIgnored: boolean,
  odometer: {value, unit: km, source: torque|photo|manual|unknown, observedAtEpochMs}|null,
  rawTorqueOdometer: {kilometers, resolutionKm: "0.1", observedAtEpochMs}|null,
  provenance: {
    originDeviceId, sourceUpdatedAtEpochMs,
    sourceKind, occurrenceTimeQuality, producerBuildAtResolution,
    fields: { pointer: {source, sourceRef, observedAtEpochMs, confirmed} }
  },
  voidReason: deleted|trip_marker|null
}
```

Request envelope: `schemaVersion` 1, `producer`, `batchId`, `events[]` of `eventSyncId`, `revision`, `payloadHash`, `payload`.

Response envelope: `schemaVersion`, `batchId`, `envelopeStatus`, `results[]` of `producer`, `eventSyncId`, `revision`, `payloadHash`, `status`, `currentRevision`, `currentHash`. Per-item status is `ACCEPTED`, `DUPLICATE`, `STALE`, `CONFLICT`, `INVALID_EVENT`, or `RETRY_LATER`.

## Samples
Hash bytes are the single UTF-8 line inside each fence, without a fence newline. Samples A and B are payloads the fill writer emits. Sample C locks the canonical bytes, including NFC in `sourceRef`. The writer sends `sourceRef` null, so sample C is not a writer assertion. Sample C uses `displayUnits` `imperial` and null `vin` for the stored text `opaque-vin`.

### A — incomplete upsert
SHA-256: `e78bf8d8508dc01a44f1efd66fd73578c575561505a05475045e1ed8c6947930`; byte length: 485.
```json
{"cost":null,"economyIgnored":false,"fillExtent":"unknown","occurredAtEpochMs":0,"odometer":null,"operation":"upsert","provenance":{"fields":{},"occurrenceTimeQuality":"unknown","originDeviceId":null,"producerBuildAtResolution":null,"sourceKind":"legacy_unknown","sourceUpdatedAtEpochMs":null},"quantity":null,"rawTorqueOdometer":null,"vehicle":{"displayUnits":"imperial","make":null,"model":null,"name":"Example vehicle","syncId":"vehicle-1","vin":null,"year":null},"voidReason":null}
```

### B — tombstone
SHA-256: `e84b4fe0b51f0e5cf10ebf75cad2cab93531d233b1dd1ad5d2e5e5e185d3a3b4`; byte length: 488.
```json
{"cost":null,"economyIgnored":false,"fillExtent":"unknown","occurredAtEpochMs":0,"odometer":null,"operation":"void","provenance":{"fields":{},"occurrenceTimeQuality":"unknown","originDeviceId":null,"producerBuildAtResolution":null,"sourceKind":"legacy_unknown","sourceUpdatedAtEpochMs":null},"quantity":null,"rawTorqueOdometer":null,"vehicle":{"displayUnits":"imperial","make":null,"model":null,"name":"Example vehicle","syncId":"vehicle-1","vin":null,"year":null},"voidReason":"deleted"}
```

### C — email amounts, Torque kilometers, non-ISO stored VIN
SHA-256: `c66fe7883b30567f0370f2d3ca2727b24b45c5317635fd27cf88357073b83a7d`; byte length: 987.
```json
{"cost":{"currency":"USD","value":"42.5"},"economyIgnored":false,"fillExtent":"full","occurredAtEpochMs":1794096000000,"odometer":{"observedAtEpochMs":1794096000000,"source":"torque","unit":"km","value":"68825.2"},"operation":"upsert","provenance":{"fields":{"/cost":{"confirmed":null,"observedAtEpochMs":null,"source":"email","sourceRef":null},"/odometer":{"confirmed":null,"observedAtEpochMs":1794096000000,"source":"torque","sourceRef":null},"/quantity":{"confirmed":null,"observedAtEpochMs":null,"source":"email","sourceRef":"café-1"}},"occurrenceTimeQuality":"source_reported","originDeviceId":null,"producerBuildAtResolution":null,"sourceKind":"mixed","sourceUpdatedAtEpochMs":null},"quantity":{"unit":"L","value":"38.800470786"},"rawTorqueOdometer":{"kilometers":"68825.2","observedAtEpochMs":1794096000000,"resolutionKm":"0.1"},"vehicle":{"displayUnits":"imperial","make":null,"model":null,"name":"Example vehicle","syncId":"vehicle-1","vin":null,"year":null},"voidReason":null}
```
