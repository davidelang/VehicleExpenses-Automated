---
type: implementation-reference
status: dynamic
ai_directive: "PolicySync pilot prefs were removed. Tab LWW always uses library MergeSync via PolicySyncBridge. Update if that architecture changes."
---

# Tab LWW via remotetable (agent test evidence)

VE no longer gates library LWW behind per-entity prefs. Coordinator always:

| Surface | LWW | Then |
|---------|-----|------|
| Merge acks | `PolicySyncBridge.mergeAcksViaLwwRow` | write-back |
| Expenses | `mergeExpensesViaLwwRow` | `LocationBlobOverlay` then write-back |
| Vehicles | `mergeVehiclesViaLwwRow` | `VehicleDefinitionOverlay` then write-back |
| Fuel tabs | `mergeFuelViaLwwRow` per tab | `LocationBlobOverlay`, then **app field-merge**, then write-back |

Library remains domain-agnostic (keys / timestamps / grids). VE owns headers, location blobs, and fuel field-merge.

## Automated evidence (agents run this)

```bash
# from third_party/remotetable/src
python3 conformance/policysync_scenarios.py          # S1–S7 offline
conformance/ethercalc/up.sh
REMOTETABLE_ETHERCALC_LOCAL=1 python3 conformance/policysync_scenarios.py  # + S8
conformance/ethercalc/down.sh
# also: python3 conformance/harness.py

# VE domain overlays — unit tests
./build_app "test" -- testDebugUnitTest
# filters:
#   -- testDebugUnitTest --tests '*LocationBlobOverlayTest'
#   -- testDebugUnitTest --tests '*VehicleDefinitionOverlayTest'
```

| Scenario | What it proves |
|----------|----------------|
| S1–S5 | Local/remote/tie/tombstone/key-only LWW |
| S6 | Thin remote + thick local → crops/landmarks (Python mirror; Kotlin tests authoritative) |
| S7 | Two fuel tabs independent LWW |
| S8 | Real EtherCalc remote grid merge |
| **LocationBlobOverlayTest** | Fuel/expense `mergeBlobs` after LWW (place/thin, confirmed, empty, list) |
| **VehicleDefinitionOverlayTest** | Odo/other crops, landmarks, photos, loserOfPair, applyToMergedList |

Device/Sheets Sync is optional field confidence; rollback if broken = install master APK.
