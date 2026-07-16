---
type: implementation-reference
status: dynamic
ai_directive: "This is a downstream reference. It MUST be updated continuously to reflect the current state of the codebase. If you change a function or architecture described here, update this document in the same commit."
---

# Vehicle Expenses Automated — User Guide (condensed)

**Full illustrated manual:** [docs/user-manual.md](../user-manual.md)  
**On device:** Menu → **Help** (quick start) · Menu → **About** (version + manual link)

This file is a short reference for everyday use and sync behavior. Prefer the full manual for first-time setup with screenshots.

## What the app does

- **Quick Fill-up** — camera OCR for odometer and pump cost/volume, or manual entry; **vehicle auto-detects from dash landmarks** (no need to pick vehicle first).
- **Manage Vehicles** — reference dash photo, odo/ignore crops, landmark discovery for vehicle identity.
- **Expenses** — non-fuel costs with optional receipt photos.
- **Reports** — summaries, last full fills, expense categories, fill history.
- **Settings** — multi-destination spreadsheet sync + photo backup under **your** accounts, units, currency, local photo prefs.

**Not documented for end users:** Import Old Pictures, Alignment Experiment, Pump Experiment.

## Icons (cheat sheet)

| Control | Meaning |
|---------|---------|
| ☰ | Navigation drawer |
| ! (title bar) | Recent sync/backup failure → open Settings |
| Shutter (white circle) | Capture for OCR / receipt |
| Disk | Save fill or expense |
| ↕ (Quick Fill) | Switch odometer ↔ pump mode |
| ↔ (Quick Fill) | Swap cost ↔ volume |
| 🔍 (sync forms) | Browse Drive for sheet or folder |
| ← | Back from Settings sub-route |

## Getting started

1. **Manage Vehicles** → Add New Vehicle → dash photo → **Odo Crop** → **Run Discovery** (fix landmarks) → name → Create.
2. **Quick Fill-up** → shutter on odometer (vehicle matches from landmarks) → **↕** → shutter on pump → **Save**. Works offline.
3. Optional multi-device / backup: **Settings → Spreadsheet sync** / **Photo backup**.

## Backups & multi-device

- **Your accounts only** — Google / Microsoft / S3 / self-host; not a shared app cloud.
- **Offline first** — add fills and receipts with no network; sync is background.
- **Data (tabular):** Google Sheets, Excel, EtherCalc; Other → Baserow, NocoDB, Airtable, PocketBase, Supabase, Firebase, Zoho Sheet (+ deferred OnlyOffice/Collabora).
- **Photos:** Google Drive, OneDrive, S3, Other (rclone: WebDAV, SFTP, …).
- Full detail + setup screenshots: [user-manual.md § Backups](../user-manual.md#backups-and-multi-device-sync). Self-host: [self-host/INDEX.md](self-host/INDEX.md).

## Google Sheets / Drive (quick)

Spreadsheet: Settings → Spreadsheet sync → Add → **Google Sheets** → sign-in → URL or 🔍 → Sync now.  
Photos: Settings → Photo backup → Add → **Google Drive** → sign-in → folder → Sync now.

## Sync behavior (summary)

- **LWW** by **Sync ID** + **Updated** timestamp; soft deletes.
- Upgrade splash: local sync-id backfill (“Updating database after upgrade…”).
- Interrupted sync: next successful sync repairs remote tabs.
- Same fill entered twice = two rows (delete extras).
- Failures: red summary on Settings + **!** in app bar.
- Detail: [SYNC_BEHAVIOR.md](SYNC_BEHAVIOR.md).

## Reports

Per-vehicle summary, last-5 full-fill legs, expenses, all fills. Row currency respected; mixed currencies show per-currency subtotals (no FX). See [REPORTS_METRICS.md](REPORTS_METRICS.md).

## Navigation map

See [NAVIGATION_MAP.md](NAVIGATION_MAP.md).
