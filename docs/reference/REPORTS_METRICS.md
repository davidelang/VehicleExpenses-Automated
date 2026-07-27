# Reports metrics

Reference for economy math in `ui/reports/ReportsScreen.kt`. Field presence: a numeric field is **present** iff its value **> 0** (Room stores non-null `Int`/`Double`; 0 means absent).

## Full fill (chain anchors)

A fuel row is a **full fill** when:

- `!economyIgnored`, and
- `!isPartialFill`, and
- odometer > 0, and
- cost > 0, and
- volume (`gallons`) > 0.

Only full fills anchor MPG legs and $/mi segment endpoints. Partials may sit inside a window (and roll volume/cost when present) but never start or end a chain segment.

### `economyIgnored` (synced boolean on `FuelEntry`)

- **Must travel with the fuel row** (tabular column **Economy Ignored**; Room + LWW `updatedAt`). Not pending-only.
- Economy (MPG legs, avg/last, $/mi anchors and window cost/vol): ignored rows **do not anchor** and **do not contribute** cost/vol in windows.
- Inventory (fuel $, gallons, **fills N(Mp)** counts): **still include** ignored rows.
- Successful field correction (manual odo/cost/vol) clears ignore; UI also has Unignore.

## Row shapes vs chains

| Row shape | MPG chain | $/mi chain | Notes |
|-----------|-----------|------------|--------|
| **Full fill** | Anchor | Anchor | Ends legs / segment endpoints |
| **Partial** | May roll into window | May roll cost/vol into window | Not an anchor |
| **Odo only** (odo > 0, cost ≤ 0, vol ≤ 0) | No-op (no break, no contribution) | No-op | Kept for later merge of cost/volume |
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
3. **Display avg / last-5:** drop **MPG outliers** where `mpg < ref/3` or `mpg > ref*3`, with `ref` = **median** of all valid leg mpgs for that vehicle. If fewer than 3 legs, no outlier filter. Last MPG / avg use the filtered set.
4. Outlier endpoints also enqueue Stage C `MPG_OUTLIER` questions after merge (separate from display).

Odo-only rows in a window do not break, do not add volume/cost, and do not change odo endpoints (endpoints are full fills only).

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

**83** = total fuel rows; **(15p)** = partials. The word **fills** is required before the counts.

Vehicle id `0` is labeled **Unknown** in reports UI (never “Vehicle 0”).

## Volume display

Fuel volumes in the database are stored in the user’s **preferred** unit (gallons or liters). Reports and fuel lists show that stored number with the preferred unit **label**; they do not re-convert.
