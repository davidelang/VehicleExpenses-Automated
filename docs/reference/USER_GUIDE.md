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
- **Start trip** — open/switch tax-style trip segments (Business / Personal / …) as fuel rows with **Trip Type** (drawer after Quick Fill).
- **Manage Vehicles** — reference dash photo, odo/ignore crops, landmark discovery for vehicle identity.
- **Expenses** — non-fuel costs with optional receipt photos (New expense in drawer; list via **Reports** hub).
- **Reports** — product reports hub (Lab): **Time based reports** (one chart), expenses, fill history (fills only), vehicle summary, **Trip miles**. Trip starts are **not** counted as fills. Expense list and fill inventory open from hub cards.
- **Settings** — units, currency, local photo prefs, debug/experiment gates (not the main sync summary).
- **Syncing** — spreadsheet + photo destination summary, **Sync now**, Details on failures, entry to Spreadsheet Sync / Photo Backup.

**Advanced / experiment drawer (Settings → Show experiment screens):** Alignment Experiment, Pump Experiment, **Import Old Pictures**. Import is not on the main drawer when the gate is off; yellow **`?N`** still opens import review when questions are pending.

## Icons (cheat sheet)

| Control | Meaning |
|---------|---------|
| ☰ | Navigation drawer |
| **ⓘ** (title bar, next to menu) | Page help for the current screen (when registered; stays for the whole visit via generation token) |
| **`?N`** | Pending import review questions → Import review |
| **`!`** (title bar) | Recent sync/backup failure → open **Syncing** |
| Shutter (white circle) | Capture for OCR / receipt |
| Disk | Save fill or expense / start trip |
| Stop (Start trip) | Personal now at this location |
| ↕ (Quick Fill) | Switch odometer ↔ pump mode |
| ↔ (Quick Fill) | Swap cost ↔ volume |
| 🔍 (sync forms) | Browse Drive for sheet or folder |
| ← | Back from Settings / Syncing / Reports child / fuel edit |

## Getting started

1. **Manage Vehicles** → Add New Vehicle → dash photo → **Odo Crop** → **Run Discovery** (fix landmarks) → name → Create.
2. **Quick Fill-up** → shutter on odometer (vehicle matches from landmarks) → **↕** → shutter on pump → **Save**. Works offline. Use **ⓘ** for control reminders.
3. Optional multi-device / backup: open **Syncing** → **Spreadsheet Sync** / **Photo Backup**. New device into an **existing** cluster: sign in and open the **same** shared sheet + photo folder (Help → **Connect existing setup** tutorial); stand-alone first setup is **Add a vehicle**.

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

Spreadsheet: **Syncing** → Spreadsheet Sync → Add → **Google Sheets** → sign-in → URL or 🔍 → Sync now (all) or open dest → **Sync now (this destination)**.  
Photos: **Syncing** → Photo Backup → same pattern.  
Failures: red summary + **Details** (full API text, Copy); **!** in app bar opens Syncing.

## Sync behavior (summary)

- **LWW** by **Sync ID** + **Updated** timestamp; soft deletes.
- Multi-dest: sequential; Sheets paces reads/writes under ~60/min per quota with wait/retry; compare pass uses bulk `batchGet` where possible.
- Upgrade splash: local sync-id backfill (“Updating database after upgrade…”).
- Same fill entered twice = two rows (delete extras).
- Detail: [SYNC_BEHAVIOR.md](SYNC_BEHAVIOR.md).

## Reports

Menu → **Reports** opens the product hub (all-time summary + catalog). Child reports support vehicle mode (**All / Each / Single**) and period filters. **Time based reports** plots optional metrics on **one** plot with **independent Y scales** per unit family (economy left; $ / trip miles / trip % by type on the right) and Smooth bins; labels use unit façades (`$/G`, `G/mi`, …). **Trip miles** lists trip starts (tap to edit); Fill history / Fuel History are fills only. Photos missing locally can **Fetch from archive** using any configured photo destination. Illustrated steps: [user-manual.html](../user-manual.html). Math: [REPORTS_METRICS.md](REPORTS_METRICS.md).

## Navigation map

See [NAVIGATION_MAP.md](NAVIGATION_MAP.md).
