---
type: implementation-reference
status: dynamic
ai_directive: "This is a downstream reference. It MUST be updated continuously to reflect the current state of the codebase. If you change a function or architecture described here, update this document in the same commit."
---

# Vehicle Expenses Automated — User Guide (condensed)

**Full illustrated manual (HTML for browsers):** [docs/user-manual.html](../user-manual.html)  
**Edit source:** [docs/user-manual.md](../user-manual.md) — regenerate HTML + assets with `./scripts/render-user-manual.sh` (see [USER_MANUAL_BUILD.md](USER_MANUAL_BUILD.md))  
**On device:** Menu → **Help** (quick start) · Menu → **About** / Help → full manual (in-app HTML + screenshots)

This file is a short reference for everyday use and sync behavior. Prefer the full manual for first-time setup with screenshots.

## What the app does

- **Quick Fill-up** — camera OCR for odometer and pump cost/volume, or manual entry; **vehicle auto-detects from dash landmarks** (no need to pick vehicle first).
- **Trip Tracking** — open/switch tax-style trip segments (Business / Personal / …) as fuel rows with **Trip Type** (drawer after Quick Fill).
- **Manage Vehicles** — reference dash photo, odo/ignore crops, landmark discovery for vehicle identity.
- **Expenses** — non-fuel costs with optional receipt photos.
- **Fuel History** — per-vehicle fill list; edit fill; **Fetch image from archive** when the photo is only in cloud backup.
- **Reports & Charts** — production summaries, last full fills, expense categories, fill history (**trip starts are not counted as fills**).
- **Reports Lab** — experimental report sets (including **Trip miles** by trip type); always in the drawer.
- **Settings** — units, currency, local photo prefs, debug/experiment gates (not the main sync summary).
- **Syncing** — spreadsheet + photo destination summary, **Sync now**, and entry to Spreadsheet Sync / Photo Backup.

**Not documented for end users:** Import Old Pictures, Alignment Experiment, Pump Experiment.

## Icons (cheat sheet)

| Control | Meaning |
|---------|---------|
| ☰ | Navigation drawer |
| ! (title bar) | Recent sync/backup failure → open **Syncing** |
| Shutter (white circle) | Capture for OCR / receipt |
| Disk | Save fill or expense |
| ↕ (Quick Fill) | Switch odometer ↔ pump mode |
| ↔ (Quick Fill) | Swap cost ↔ volume |
| 🔍 (sync forms) | Browse Drive for sheet or folder |
| ← | Back from Settings / Syncing sub-route |

## Getting started

1. **Manage Vehicles** → Add New Vehicle → dash photo → **Odo Crop** → **Run Discovery** (fix landmarks) → name → Create.
2. **Quick Fill-up** → shutter on odometer (vehicle matches from landmarks) → **↕** → shutter on pump → **Save**. Works offline.
3. Optional multi-device / backup: open **Syncing** (or Settings sub-routes) → **Spreadsheet Sync** / **Photo Backup**.

## Units

- Preferred volume unit (gallons or liters) is set under **Settings**; stored fill volume matches that unit.
- Currency on each fill/expense when set; mixed currencies show separate totals (no FX conversion in-app).
- Odometer is the instrument reading (no automatic mi/km conversion).

## Photos & archive

- **Sync / background photo backup** auto-downloads **vehicle reference** images only.
- Fuel fill and expense receipt photos download **on demand** via **Fetch image from archive** when the cloud identity exists.
- Local path is stored after a successful fetch; dead local paths are cleared without deleting cloud identity.

## Backups & multi-device

- **Your accounts only** — Google / Microsoft / S3 / self-host; not a shared app cloud.
- **Offline first** — add fills and receipts with no network; sync is background.
- **Data (tabular):** Google Sheets, Excel, EtherCalc; Other → Baserow, NocoDB, Airtable, PocketBase, Supabase, Firebase, Zoho Sheet (+ deferred OnlyOffice/Collabora).
- **Photos:** Google Drive, OneDrive, S3, Other (rclone: WebDAV, SFTP, …).
- Full detail + setup screenshots: [user-manual.html § Backups](../user-manual.html#backups-and-multi-device-sync). Self-host: [self-host/INDEX.md](self-host/INDEX.md).

## Google Sheets / Drive (quick)

Spreadsheet: **Syncing** → Spreadsheet Sync → Add → **Google Sheets** → sign-in → URL or 🔍 → Sync now.  
Photos: **Syncing** → Photo Backup → Add → **Google Drive** → sign-in → folder → Sync now.  
(Same destinations are also reachable from Settings sub-routes.)

## Sync behavior (summary)

- **LWW** by **Sync ID** + **Updated** timestamp; soft deletes.
- Upgrade splash: local sync-id backfill (“Updating database after upgrade…”).
- Interrupted sync: next successful sync repairs remote tabs.
- Same fill entered twice = two rows (delete extras).
- Failures: red summary on **Syncing** + **!** in app bar (opens Syncing).
- Detail: [SYNC_BEHAVIOR.md](SYNC_BEHAVIOR.md).

## Reports

Production **Reports & Charts**: per-vehicle summary, last-5 full-fill legs, expenses, all fills (non-trip). Row currency respected; mixed currencies show per-currency subtotals (no FX). See [REPORTS_METRICS.md](REPORTS_METRICS.md).  
Experimental **Reports Lab** includes **Trip miles** (miles by trip type from open-only segments).

## Navigation map

See [NAVIGATION_MAP.md](NAVIGATION_MAP.md).
