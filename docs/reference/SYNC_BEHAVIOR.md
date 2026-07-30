---
type: implementation-reference
status: dynamic
ai_directive: "This is a downstream reference. It MUST be updated continuously to reflect the current state of the codebase. If you change a function or architecture described here, update this document in the same commit."
---

# Sync behavior — developer / operator reference

Bidirectional sync merges local Room data with remote tabular destinations (Google Sheets, Excel, EtherCalc, row databases, etc.) and optional photo backup (Google Drive, OneDrive, S3, rclone-backed targets). Merge key is **`syncId`** per row; last-write-wins (**LWW**) uses **`updatedAt`** (milliseconds) on the full row, including **`deleted`** / **`deletedAt`** tombstones.

## Mid-sync interruption (#8)

If the app crashes or is killed **during a full tab rewrite** of a remote sheet tab, the remote tab can be temporarily partial or inconsistent.

**Primary recovery:** the next successful sync re-merges local + remote (LWW) and rewrites the tab again — expected self-heal when local Room is intact.

**Manual recovery:** edit the remote sheet directly, or use provider version history where available.

| Provider | Version history |
|----------|-----------------|
| **Google Sheets** | File → Version history — restore a prior revision as last resort |
| **Excel (OneDrive/Graph)** | Version history in the web/desktop client when available |
| **Self-hosted / row DB / EtherCalc** | Often no sheet-style history — rely on next sync + local Room as source of truth |

Optional app log line when falling back to full rewrite: see `SpreadsheetSyncCoordinator` (`fullRewrite`).

## Duplicate fills across devices (#9)

Devices and device IDs are unique. There is **no** supported model where two devices intentionally share one logical fill with two independent local identities that auto-collapse.

If the user **manually enters the same fill on two devices**, or runs **Quick Fill twice** for the same real-world fill, they should **expect two rows** until they clean up manually (delete in app or sheet). The **Deleted** column / tombstone supports removal after cleanup.

## Photo bookkeeping vs LWW

Vehicle reference images, fuel dash/pump photos, and expense receipts sync **binary files** via photo destinations. Updates that only adjust **`cloudManifest`** or local **`photoUrl`** paths must **not** bump **`updatedAt`** on fuel/expense rows, or a photo upload could spuriously win LWW over a newer real edit on another device. Vehicles already use `updateVehiclePreservingTimestamp` for manifest-only writes.

## Background sync and failures

- Periodic sync uses WorkManager with per-destination **`frequencyMinutes`** (UI shows **hours**, 0.25–24 h).
- A destination **last failure** is persisted until the next success for that destination; Settings shows red error text and the main app bar shows a problem icon when any unfixed failure exists.
- Retry is primarily the **next scheduled interval**, not aggressive re-run of all destinations on partial failure.

## Upgrade backfill

On first launch after a schema upgrade that introduces blank **`syncId`** values, a one-shot local Room backfill assigns deterministic IDs. The UI may show **“Updating database after upgrade…”** until complete; cloud workers are not started until backfill finishes.

## Fuel LWW vs field-merge vs questions

| Layer | What | When |
|-------|------|------|
| **Row merge (LWW)** | Per `syncId`, side with greater `updatedAt` wins (tie → local). Upsert into Room **preserves** winner `updatedAt` (no restamp). **Room PK is never taken from the sheet ID column** (insert with `id=0`). Then field-merge, then write merged set back to the sheet. | Every successful spreadsheet/tabular fuel tab sync |
| **Field merge (partials)** | After fuel LWW, **before** sheet write-back: same **Run merge** engine (`fieldMergeForSync`): 15m window, tight pairs, soft-delete published absorbs. Second pass if unmatched odo/pump pairs remain. **MERGE_EXEMPT** acks suppress absorb of acked member `syncId` sets. | Once per successful fuel sync session |
| **Question rebuild** | Pending JSON is **local-only** (not LWW’d). After field-merge: phase-scoped detectors; durable **Merge acks** drop re-asks for acked CONFLICT / AMBIGUOUS / MPG fingerprints. | Post-sync after fuel is stable |
| **Merge acks tab** | Sheet tab **Merge acks** LWW by `ackId` (Sync ID column). Survives multi-device; kinds include `CONFLICT_ODO`, `AMBIGUOUS_MULTI_PUMP`, `MPG_OUTLIER`, `MERGE_EXEMPT`. CSV zip includes **`Merge acks.csv`** (import no-op if missing). | Same sync as fuel |

### Fuel columns: Location vs Notes

| Column | Meaning |
|--------|---------|
| **Location** | Station place: JSON `{"name":"Shell","address":"…"}` when known; legacy plain text still displays. Lat/long stay in **Latitude** / **Longitude**. |
| **Notes** | Freeform + batch provenance (`batch_import_dash:…`, `batch_gap_marker`, …). New batch inserts write tags here; engine role checks read **notes** and still accept legacy tags in **location**. |

**Default header order** (new / empty fuel tabs and CSV export): human fields first (Timestamp … Notes), machine IDs last (Vehicle Sync ID … Deleted At). Existing sheets keep their column order; missing columns (e.g. Notes) are **appended** only — never auto-reordered. Backends that rewrite the full grid after append **pad** data rows with empty cells for new columns. Reads are **name-based**.

### CSV zip backup parity

Local CSV zip export mirrors the spreadsheet tabular surface (not a remote destination):

| Zip entry | Sheet counterpart | Tombstones |
|-----------|-------------------|------------|
| `Vehicles.csv` | Vehicles tab | Included |
| `Expenses.csv` | Expenses tab | Included |
| `Fuel - {name}.csv` | Per live vehicle fuel tab (incl. Unassigned) | Soft-deleted fuel included |
| `Merge acks.csv` | Merge acks tab | Included |

Import: missing entries (old zips without Merge acks / Notes column) are no-ops; fuel/expense columns resolve by header name. Fuel export header always includes **Notes** and **Sync ID**.

**Complete fulls in a 15 m cluster:** ≥2 complete fills with distinct odos → silent keep-both; same odo → `CONFLICT_ODO` pending (no silent absorb). Keep both / Looks correct write durable acks as before.

User edits (gap insert, odo fix, partial checkbox) go through repository APIs that **stamp `updatedAt = now`**, so the next sync **pushes** those cleanups unless remote edited the same `syncId` later.

## Related docs

- User-facing summary: [USER_GUIDE.md](USER_GUIDE.md) — Synchronization section
- Economy chain rules: [REPORTS_METRICS.md](REPORTS_METRICS.md)
- Self-hosted setup: [self-host/INDEX.md](self-host/INDEX.md)
- Non-Google version-history caveat: [self-host/README.md](self-host/README.md)