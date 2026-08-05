---
type: implementation-reference
status: dynamic
ai_directive: "Update when PolicySync pilot prefs or log lines change. Default-on flips require human go (see plan ve-policysync-pilots-default-on-evaluation)."
---

# PolicySync LWW pilots — soak checklist

Gated prefs in SharedPreferences **`vehicle_settings`**. Wire: `PolicySyncBridge` + `SpreadsheetSyncCoordinator`. Library path uses remotetable `MergeSync` **lww_row**; production Sheets dest unchanged.

## Current defaults (as of evaluation plan)

| Pref key | Surface | Default | Residual vs legacy |
|----------|---------|---------|-------------------|
| `use_policy_sync_merge_acks` | Merge acks tab | **false** | Full-row LWW |
| `use_policy_sync_expenses` | Expenses tab | **false** | Full-row LWW |
| `use_policy_sync_vehicles` | Vehicles tab | **false** | Full-row LWW + **VehicleDefinitionOverlay** (same as legacy) |
| `use_policy_sync_fuel` | Each `Fuel - {name}` tab | **false** | Pass 1 library LWW; Pass 2 **field-merge still app-side** |

**Force legacy:** set the pref boolean to **false** (works even after a future default-on flip).

**Proposed first tranche (not applied until human go):** merge_acks / expenses / vehicles → default **true**; fuel stays **false**.

## Log lines to expect (logcat, app tag)

| Pref true | Logcat (approx) |
|-----------|-----------------|
| merge_acks | `Merge acks via PolicySync/MergeSync (lww_row pilot)` |
| expenses | `Expenses via PolicySync/MergeSync (lww_row pilot)` |
| vehicles | `Vehicles via PolicySync/MergeSync (lww_row + definition overlay)` |
| fuel | `Fuel LWW via PolicySync/MergeSync (lww_row pilot; field-merge still app-side)` |

Also still see `Fuel field-merge before sheet write` after fuel LWW (both flag paths).

## Automated evidence (primary for agents)

Local EtherCalc + pure MergeSync scenarios — **no Google account / emulator required**.

```bash
# from third_party/remotetable/src (or pin src)
python3 conformance/policysync_scenarios.py          # S1–S7 offline always
conformance/ethercalc/up.sh
REMOTETABLE_ETHERCALC_LOCAL=1 python3 conformance/policysync_scenarios.py  # + S8 HTTP
conformance/ethercalc/down.sh
# also included in: python3 conformance/harness.py
```

| Scenario | What it proves |
|----------|----------------|
| S1–S5 | Local/remote/tie/tombstone/key-only LWW for acks, expenses, vehicles, fuel |
| S6 | Thin remote win + thick local → crops/landmarks filled (VehicleDefinitionOverlay rules) |
| S7 | Two fuel tabs independent LWW |
| S8 | Real EtherCalc remote grid read + merge (per entity + multi-tab fuel rooms) |

**PASS offline + S8 with up.sh** is sufficient agent evidence for LWW correctness. Device/Sheets soak remains optional product confidence.

## Soak sequence (optional human + real sheet)

Use a **non-prod or backup sheet** if possible. Install build that has all four pilots. Prefs editor or adb write on `vehicle_settings`.

1. **Baseline** — all four prefs **false** (or unset). **Sync now**. Confirm success; no PolicySync log lines above.
2. **Merge acks only** — `use_policy_sync_merge_acks=true`, others false. Sync. Expect merge-acks log; Merge acks sheet matches Room.
3. **Expenses only** — expenses true, others false. Sync. Expenses OK.
4. **Vehicles only** — vehicles true, others false. Sync. After a case where remote wins LWW but local had crops/landmarks/photos, confirm definition fields still present (overlay).
5. **Fuel only** — fuel true, others false. Sync multi-vehicle fuel tabs. Confirm field-merge log still runs; no header/Sync ID corruption; Stage C still sensible.
6. **All four true** — enable all. One full Sync. Spot-check tabs + photos.

Record pass/fail per step in chat or eng-log. **Do not flip code defaults** until human says which prefs get default **true**.

## Recovery

Set any pilot pref to **false** and Sync again to force coordinator legacy LWW for that surface.

## Related

- `PolicySyncBridge.kt` — pref constants + merge helpers  
- `VehicleDefinitionOverlay.kt` — vehicles definition fill after LWW  
- `docs/reference/SYNC_BEHAVIOR.md` — general LWW / interruption  
- Plan: `dev-ai-interaction/plans/ve-policysync-pilots-default-on-evaluation-20260805-0815-plan.md`
