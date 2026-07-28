# Reports metrics

Reference for economy math in `ui/reports/ReportsScreen.kt`. Field presence: a numeric field is **present** iff its value **> 0** (Room stores non-null `Int`/`Double`; 0 means absent).

## Full fill (chain anchors)

A fuel row is a **full fill** when:

- `!economyIgnored`, and
- `!isPartialFill` (**explicit override only** — see below), and
- odometer > 0, and
- cost > 0, and
- volume (`gallons`) > 0.

Only full fills anchor MPG legs and $/mi segment endpoints.

### `isPartialFill` (explicit override — not “incomplete”)

| Value | Meaning |
|-------|---------|
| **false (default)** | No override. Full-fill = field presence only. |
| **true** | All three of odo, cost, volume are present **and** user checked “Treat as partial fill” — do **not** use as full-fill anchor. |

**Implicit incompleteness** (missing odo and/or cost and/or volume) ⇒ not a full fill **without** setting the flag. Incomplete inserts keep `isPartialFill = false`.

Batch import, merge, and odo sanitizer **must never** auto-set `isPartialFill = true` for missing data or “heal” odo issues.

Inventory `fills N(Mp)`: **(Mp)** counts rows with **`isPartialFill == true`** only (explicit), not every incomplete row.

Incomplete rows may still roll cost/vol into MPG/$/mi windows when present; they never anchor.

### `economyIgnored` (synced boolean on `FuelEntry`)

- **Must travel with the fuel row** (tabular column **Economy Ignored**; Room + LWW `updatedAt`). Not pending-only.
- Economy (MPG legs, avg/last, $/mi anchors and window cost/vol): ignored rows **do not anchor** and **do not contribute** cost/vol in windows.
- Inventory (fuel $, gallons, **fills N(Mp)** counts): **still include** ignored rows.
- Successful field correction (manual odo/cost/vol) clears ignore; UI also has Unignore.

## Row shapes vs chains

| Row shape | MPG chain | $/mi chain | Notes |
|-----------|-----------|------------|--------|
| **Full fill** | Anchor | Anchor | Fields complete + flag false + not economyIgnored |
| **Explicit partial** (`isPartialFill`) | May roll into window | May roll cost/vol into window | Complete fields but not an anchor |
| **Odo only** (odo > 0, cost ≤ 0, vol ≤ 0) | No-op (no break, no contribution) | No-op | Flag stays false; not a full fill |
| **Cost, no volume** | **Breaks** | Does not break by itself | Re-anchor MPG to nearest fulls on each side |
| **Volume, no cost** | Does not break by itself | **Breaks** | Volume may still roll into an MPG leg if that leg is allowed |
| **Blank** (odo/cost/vol all ≤ 0) | **Breaks** | **Breaks** | Missed / unrecoverable fill marker; no gap column |

Time gaps between fills are normal and are not breaks.

## MPG (last, average, last-5 legs)

1. Collect full fills for the vehicle, sorted by `timestamp` ascending (tie-break `id`).
2. For each adjacent pair `(prev, cur)` with `cur.odometer > prev.odometer`:
   - Window = all fuel rows with `prev.timestamp < t ≤ cur.timestamp` that are **not** `economyIgnored`.
   - If any contributing row in the window is an **MPG chain breaker** (blank, or cost without volume), **skip** the pair.
   - `sumVol` = sum of `gallons` for contributing rows in the window with volume present.
   - If `sumVol ≤ 0`, skip.
   - `mpg = (cur.odometer − prev.odometer) / sumVol`.
   - Display cost on a leg = multi-currency sum of costs in the window for rows with cost present (same helper as elsewhere).
3. **Display avg / last-5 (display filter only — no row mutation):**
   - Keep legs with mpg in a hard absolute band **5–80** (outside → drop for display).
   - Then drop **3× median** outliers among remaining legs (`mpg < ref/3` or `mpg > ref*3`). If fewer than 3 legs after band filter, skip 3× filter.
4. Stage C `FuelEconomyOutliers.detectOutliers` uses the **same** full-fill + window + MPG breaker rules via shared `FuelEconomyChains` (not a separate math). Legs with a blank gap marker (or cost-without-vol) in the window are **not** enqueued as `MPG_OUTLIER`. Display still applies the 5–80 band + 3× median filters above; Stage C uses only the 3× median vs vehicle ref for questions.

Odo-only rows in a window do not break, do not add volume/cost, and do not change odo endpoints (endpoints are full fills only).

After spreadsheet fuel sync, field-merge + question rebuild runs once (see [SYNC_BEHAVIOR.md](SYNC_BEHAVIOR.md) — Fuel LWW vs field-merge vs questions).

## Dollars per mile (`$/mi`)

Per vehicle, **segment sum** over unbroken full→full pairs (not global max−min over all positive odometers):

1. Same full-fill list as MPG.
2. For each adjacent pair `(prev, cur)` with odo increase:
   - Window as above.
   - If any row in the window is a **$/mi chain breaker** (blank, or volume without cost), **skip** the pair.
   - Else: add miles `(cur.odometer − prev.odometer)`; add fuel costs for rows in the window with cost present; add expenses for this vehicle whose `date` is in `(prev.timestamp, cur.timestamp]`.
3. If total miles ≤ 0, or the combined cost map is empty, or more than one currency is present, UI shows **n/a**.
4. Else `$/mi = totalCost / miles` for the single-currency case.

Odo-only (and other non-full) rows never set min/max odo for this metric.

## Inventory totals

Overall and per-vehicle inventory lines (total fuel $, total volume, fill counts) still sum **all** non-deleted rows for the vehicle (including `economyIgnored`). They are inventory, not chain economy. Only last/avg MPG, last-5 legs, and $/mi use the chain rules.

Per-vehicle stats line format:

```text
Fuel $… · 1031.6G · fills 83(15p) · last … · avg … · $/mi …
```

**83** = total fuel rows; **(15p)** = rows with **`isPartialFill == true`** (explicit override only). The word **fills** is required before the counts.

Vehicle id `0` is labeled **Unknown** in reports UI (never “Vehicle 0”).

## Volume display

Fuel volumes in the database are stored in the user’s **preferred** unit (gallons or liters). Reports and fuel lists show that stored number with the preferred unit **label**; they do not re-convert.

## Merge window / odo sanitizer (batch)

- Cluster / pair window: **15 minutes** (`FuelRowMergeEngine.MERGE_WINDOW_MS`).
- Multi dash+pump clusters split into tight time pairs before field merge.
- Unreasonable odo gap: `Δodo > maxVol(vehicle) × medianMpg(vehicle) × 3` demotes suspect to **partial** + question (`ODO_SUSPECT`). No constant mpg fallback.
- Reverse odo in time order: reliability demote (bias later) → partial + question.
- **Flag as partial** on bad-mpg questions: endpoint leaves full-fill anchors without `economyIgnored`.
