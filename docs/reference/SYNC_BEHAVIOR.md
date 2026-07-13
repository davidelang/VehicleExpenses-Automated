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

## Related docs

- User-facing summary: [USER_GUIDE.md](USER_GUIDE.md) — Synchronization section
- Self-hosted setup: [self-host/INDEX.md](self-host/INDEX.md)
- Non-Google version-history caveat: [self-host/README.md](self-host/README.md)