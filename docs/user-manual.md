# Vehicle Expenses Automated — User Manual

> **Edit source (Markdown).** Browsers and the in-app reader open the **rendered HTML**:
> - Web: [`docs/user-manual.html`](user-manual.html) (regenerate with `./scripts/render-user-manual.sh`)
> - App: Help / About → full manual (bundled HTML + screenshots)
>
> Do not point end users at raw `.md` URLs — browsers show plain text only.

Camera-first tracking for fuel fill-ups and vehicle expenses, with optional multi-device sync and backup under **your** cloud accounts.

This is the **full manual** (screenshots + every step). On the phone, **Menu → Help** is a shorter getting-started guide.

**Not covered here:** Import Old Pictures, Alignment Experiment, and Pump Experiment (developer / advanced tools).

---

## Table of contents

1. [What you need](#what-you-need)
2. [Icons at a glance](#icons-at-a-glance)
3. [Open the menu](#open-the-menu)
4. [First-time setup: Manage Vehicles](#first-time-setup-manage-vehicles)
5. [Backups and multi-device sync](#backups-and-multi-device-sync)
6. [Quick Fill-up (fuel)](#quick-fill-up-fuel)
7. [Start trip](#start-trip)
8. [Expenses](#expenses)
9. [Reports](#reports)
10. [Settings (local preferences)](#settings-local-preferences)
11. [Syncing](#syncing)
12. [Help & About](#help--about)
13. [Related docs](#related-docs)

---

## What you need

- Android phone or tablet.
- For best OCR: a clear view of your **dashboard odometer** and **pump totals** (or type the numbers by hand).
- Optional: accounts **you control** for spreadsheet data and/or photo backup (see [Backups and multi-device sync](#backups-and-multi-device-sync)).

---

## Icons at a glance

These appear on the main screens. Knowing them saves a lot of hunting.

| Where | Icon / control | What it does |
|-------|----------------|--------------|
| Top bar | **☰ Menu** (hamburger) | Opens the navigation drawer |
| Top bar | **ⓘ** (page help) | Short help for the **current** page (next to menu when available) |
| Top bar | **`?N`** (yellow) | Pending import review questions — opens Import review |
| Top bar | **!** (red) | A spreadsheet or photo destination failed recently — open **Syncing** to fix |
| Top bar | **☰ + ←** | Report children and Expenses list show **menu and back** together; Reports hub is menu only |
| Settings / fuel edit | **←** | Back (settings spreadsheet/photo and fuel edit stay back-focused) |
| Quick Fill | **White circle** (shutter) | Capture odometer or pump display for OCR |
| Quick Fill | **Disk / Save** | Save the fill-up (needs a vehicle and at least one of odo / volume / cost) |
| Quick Fill | **↕ arrows** (mode switch) | Toggle **odometer mode** vs **pump (cost/volume) mode**. Green border highlights the active field group |
| Quick Fill | **↔ arrows** (between cost & volume) | Swap cost and volume if OCR put them in the wrong fields |
| Quick Fill | **Zoom 1x / …** | Camera zoom ratios when the lens supports them |
| Quick Fill (after capture) | **Refresh** on main button | Discard preview and return to live camera |
| Quick Fill (while processing) | **X** on main button | Cancel in-progress capture/OCR |
| Expense | **Save** | Save the expense |
| Expense | **Shutter circle** | Take a receipt photo |
| Expense | **Gallery** | Pick a receipt image from the library |
| Expense | **Retake** | Clear current receipt photo and shoot again |
| Expense / Manage Vehicles | **+ / −** FABs | Zoom the photo preview |
| Landmarks dialog | **Edit OCR** | Correct or add landmark text the engines missed |
| Spreadsheet / Photo forms | **🔍 Search** | Browse Google Drive for a sheet or folder (after sign-in) |

Currency symbols on cost fields and **G/L** on volume fields are tappable: open a small menu to change currency or gallons vs liters for that entry.

---

## Open the menu

1. Tap **☰** at the top left.
2. Choose a page.

![Navigation drawer](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Main drawer:** Quick Fill-up · Start trip · Manage Vehicles · New expense · **Reports** · Settings · Syncing · Help · About.

**Experiment drawer** (Settings → Show experiment screens): Alignment Experiment · Pump Experiment · **Import Old Pictures**.

**Via Reports hub (not main drawer):** Expenses list · Fill history.

---

## First-time setup: Manage Vehicles

OCR and **automatic vehicle matching** work best after you register each vehicle with a **reference dashboard photo**, crop the odometer, and run **Discovery** so the app stores landmark text for that dash. (How landmarks are chosen and matched will be documented in more detail in a later update.)

### Open Manage Vehicles

Menu → **Manage Vehicles**. Choose a vehicle (or **Add New Vehicle**).

![Manage Vehicles with reference dash, crops, and discovery](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### Add or edit a vehicle

1. Open the **Vehicle** dropdown → pick a vehicle or **Add New Vehicle**.
2. Capture or pick a clear **reference dash photo** (full instrument cluster, well lit, phone roughly square-on). Use **Take Photo** or **Gallery**.
3. Draw crops:
   - **Odo Crop** — rectangle tightly around the odometer digits (button shows **Done Odo** while that mode is active).
   - **Ignore Crop** — optional region to ignore (clock, radio, etc.).
   - **Edit Crops** — adjust existing rectangles.
4. Tap **Run Discovery** — multi-engine OCR finds landmark words outside the crops.
5. Review with **Show Landmarks**. Use **Edit OCR** to fix misreads or **add** text that was missed.
6. Fill **Vehicle Name** (required), plus make/model/year/plate as you like.
7. Tap **Create Vehicle** or **Save Changes** (requires name + reference photo for a new vehicle).

![Add new vehicle form fields](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### Landmarks: fix what Discovery missed

After **Show Landmarks**, scroll the list and correct values. Engines sometimes miss small digits (for example a clock **60** on the bottom right of the cluster). Use **Edit OCR** to add or fix them so vehicle identity stays reliable.

![Landmarks dialog — review and edit OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### Typing without a perfect photo

You can still use the app by selecting a vehicle and **typing** odometer, volume, and cost on Quick Fill — OCR is optional for every field. Gallery import works for the reference dash photo when you prefer not to shoot in-app.

**Tip:** After spreadsheet sync, vehicle definitions (crops, landmarks) live in the local database — you do not need to re-open Manage Vehicles for Quick Fill to use them.

---

## Backups and multi-device sync

The app is built so **several phones or tablets can share the same fleet data**, and so you can keep a **copy of your data and photos off the device**. That is done with destinations **you** configure under **your** accounts or **your** self-hosted servers — not a company-run “Vehicle Expenses cloud” that other people can see.

### What runs where

| Kind | What it stores | Typical use |
|------|----------------|-------------|
| **Spreadsheet / tabular sync** | Vehicles, fuel fills, expenses (rows and tabs) | Multi-device merge + structured backup |
| **Photo backup** | Binary images (dash/pump/receipt/reference photos) | Photo backup + restore missing files |

You can configure **multiple destinations** of each kind (soft cap per type). Manual **Sync now** and **background** workers run the enabled ones.

### Offline first

- **No network is required** to add a fill-up, expense, or receipt. Everything is saved **locally first**.
- When the network is available, sync and photo backup run as **background tasks** (on a schedule you set, and when you tap **Sync now**). Failures show as red text under the Settings rows and a **!** in the app title bar.

### Your accounts only

Sign-in and tokens stay on the device for the providers you choose (Google, Microsoft, S3 keys, self-hosted URLs, and so on). Destinations are under **full control of the user** — your Google account, your OneDrive, your MinIO bucket, your EtherCalc host, etc. Nothing is shared with other Vehicle Expenses users through a shared backend.

### Supported targets — data (spreadsheet / tabular)

Configured under **Menu → Syncing → Spreadsheet sync** (also reachable from Settings summary rows). First-class picker options:

| Target | Notes |
|--------|--------|
| **Google Sheets** | Common default; tabs for Vehicles, Expenses, and per-vehicle fuel |
| **Excel** | Microsoft workbook via Graph / OneDrive-style binding |
| **EtherCalc** | Self-hosted collaborative spreadsheet rooms |
| **Other →** implemented backends | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Deferred / not headless yet (listed under Other but not fully implemented): OnlyOffice, Collabora. See also [self-host index](reference/self-host/INDEX.md).

CSV **export/import** (ZIP of the same tab layout) is available from Settings as a portable backup, independent of live sync.

### Supported targets — photos (image backup)

Configured under **Menu → Syncing → Photo backup** (also from Settings summary rows):

| Target | Notes |
|--------|--------|
| **Google Drive** | Folder you choose (browse or paste URL) |
| **OneDrive** | Microsoft account + path prefix |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO, and other S3-compatible endpoints |
| **Other** | rclone-backed storage (e.g. WebDAV, SFTP, and other curated remotes available in the in-app picker) |

Setup cheatsheets for self-hosted photo and tabular targets: [self-host index](reference/self-host/INDEX.md).

### Multi-device behavior (short)

- Rows merge by **Sync ID** with **last-write-wins** on **Updated** timestamps.
- Deletes are soft; a newer edit on another device can restore a row.
- Entering the **same fill twice** on two devices creates **two rows** — delete the extra when you notice.
- More detail: [Sync behavior notes](#sync-behavior-notes) and [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### Example: add Google Sheets (data)

1. **Menu → Syncing → Spreadsheet sync** (or Settings → Spreadsheet sync).

   ![Spreadsheet sync list](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Tap **Add spreadsheet destination**.

   ![Provider picker](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Choose **Google Sheets**.

   ![Google Sheets form](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Sign in with Google** → display name → **Sheet URL** or **🔍** browse/create → schedule options → enable → save.
5. **Sync now** once to create/update tabs: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### Example: add Google Drive (photos)

1. **Menu → Syncing → Photo backup** (or Settings → Photo backup).

   ![Photo backup list](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Tap **Add photo destination**.

   ![Photo provider picker](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Choose **Google Drive**.

   ![Google Drive form](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Sign in with Google (Drive)** → optional folder URL/browse → enable → save → **Sync now**.

Manual **Sync now** for photos is a full pass; background backup typically processes **pending-only** uploads on a schedule.

### Sync behavior notes

- After app upgrade you may briefly see **“Updating database after upgrade…”** (local sync-id backfill).
- If a sync is interrupted, the next **successful** sync re-merges and repairs remote tabs.
- Failures: red summary on Syncing cards + **!** in the app bar.

---

## Quick Fill-up (fuel)

This is the **home screen** when you open the app.

### Vehicle selection (usually automatic)

You do **not** need to pick the vehicle first. When vehicles have **landmarks** set up in Manage Vehicles, Quick Fill **auto-detects which vehicle** from the dash image after you capture the odometer. You can still open the **Vehicle** dropdown to override if needed.

### Aim at the odometer

Stay in odometer mode and frame the cluster. Instruction: *Aim at odometer. Tap shutter to capture.*

![Quick Fill — live camera on odometer (example dash image in preview)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-quickfill-odo-live.jpg)

### After the odometer shutter

OCR fills **Odo** and tries to match the vehicle from landmarks (review both if needed). The main button becomes **Retry** to re-shoot. Instruction summarizes the reading.

![Quick Fill — odometer OCR result](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-quickfill-odo-result.jpg)

### Pump mode (cost and volume)

1. Tap **↕** to switch to pump mode: *Aim at pump display (cost/volume). Tap shutter.*
2. Capture the pump totals. Cost and volume fields fill; use **↔** if they are swapped.
3. Tap currency or **G/L** if needed, then **Save** (disk). Empty fields make a **partial fill** (still allowed).

![Quick Fill — pump preview / totals (example pump image in preview)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-quickfill-pump-result.jpg)

You stay on Quick Fill for the next stop (fields clear after save). Work fully **offline**; sync runs later in the background when configured.

### Manual entry (no camera / bad OCR)

1. Tap **Odo**, **cost**, or **volume** and type values (portrait uses the system keyboard; landscape uses an on-screen keypad).
2. Choose or confirm the **Vehicle** if auto-detect did not run.
3. Save as above.

### Modes and borders

- **Green border** around vehicle+odo → capturing/editing odometer.
- **Green border** around cost+volume → pump mode.
- **Save** stays disabled until a vehicle is selected and at least one of odo/cost/volume has data, and OCR is not still running.

On-screen tip (below the instruction line): *Shutter = capture · Disk = save · ↕ = odo/pump mode · ↔ = swap cost/volume.*

---

## Expenses

### New expense

Menu → **New expense**.

![New expense form and camera controls (example dash image in camera band when empty)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Save** (disk), **shutter** (receipt photo), or **gallery** (pick image).
2. Fill **Date**, **Vehicle**, **Vendor**, **Description**, **Amount** (currency symbol tappable), **Category**, optional **Odometer**.
3. Multi-page receipts: capture additional pages if the UI offers paging (page 0 is the primary receipt).
4. **Save** to store (local first; photo backup and spreadsheet sync happen in the background when configured).

### Expense list

Menu → **Reports** → **Expenses list** — browse past non-fuel expenses; open an item to edit.

![Expense list](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### Edit expense

Open a row from the list. Correct vendor, amount, category, vehicle, and description. If the receipt is only in photo backup (no readable local file), use **Fetch image from archive** when shown (works across configured photo destinations).

![Edit expense](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## Start trip

Menu → **Start trip** (after Quick Fill in the drawer). Capture or enter odometer, choose trip type, save with the **disk** icon. **Stop** is a shortcut for Personal now at the held GPS location. Use **ⓘ** for control reminders.

![Start trip](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Trip starts are stored as fuel rows with a **Trip Type** (not normal fills). They appear under **Reports → Trip miles**, not under Fuel History.

---

## Reports

Menu → **Reports** opens the product hub (all-time summary + catalog cards). This is the only product reports surface — there is no separate “Reports & Charts” drawer item.

![Reports hub](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Open a card for vehicle mode (**All / Each / Single**), period filters, charts, and share (**TEXT / CSV / PDF**). Top bar on report children: **☰ + ←** (and **ⓘ** when registered).

### Time based reports

The main chart card. Optional metrics (mpg, volume/distance such as G/mi, unit price such as $/G, cost/distance, monthly $, trip miles, trip % by type) with **Smooth** bins and **independent Y scales** (economy left; money and trip families on the right).

![Time based reports](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Time based reports — scrolled metrics](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Details of economy math: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### Fill history vs Fuel History

- **Reports → Fill history** — chronological fills for the report filters (**fills only**; no trip starts).

![Fill history report](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Fuel History** (if present in your build’s navigation) — per-vehicle fill inventory, also fills only; tap a row to edit.

### Trip miles

**Reports → Trip miles** — miles by type, charts, and a chronological **trip start / segment list**. Tap a real start to open **Edit fill** for that row.

![Trip miles](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### Edit fill

From Fill history, Fuel History, or Trip miles, open a fill. Layout: vehicle and odometer, **currency before cost**, volume, notes. Trip type appears only when the row is a trip start. Location has a summary plus **Location details**. Missing local photo with cloud identity: **Fetch image from archive**.

![Edit fill — Fetch from archive when local photo missing](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Other catalog cards include expenses by category, vehicle summary, and expenses list.

Money uses each row’s currency when set. Mixed-currency totals show **per-currency subtotals** (no silent FX conversion).

---

## Syncing

Menu → **Syncing** is the hub for spreadsheet and photo destinations (not only buried under Settings).

![Syncing hub](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Cards for **Spreadsheet sync** and **Photo backup** with short status, **Sync** for that kind, and **›** into the destination list.
- Open a destination for **Test connection** and **Sync now (this destination)** / all configured.
- Failure **Details** and the red **!** in the title bar land here.
- Step-by-step Google Sheets and Drive setup: [Backups and multi-device sync](#backups-and-multi-device-sync).

---

## Settings (local preferences)

Menu → **Settings**.

![Settings](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![More settings (scrolled)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

For destinations, prefer **Menu → Syncing**. Settings may still show summary rows that open the same lists.

### Local preferences (common)

- **Save Fuel Receipt Photos** / **Save Expense Photos Locally** — keep images on the device (may request Photos permission).
- **Play Shutter Sound**
- **Currency** / **Volume unit** — app defaults (system or explicit). Changing volume unit with existing fuel data may offer a convert dialog.
- **Dark mode**
- **Setup tips** — re-open first-run vehicle / sync tutorials.
- **Debug Quick Fill** / **Show experiment screens (dev)** — advanced; leave off for daily use. Experiment screens are not documented here.

CSV **export/import** (ZIP of Vehicles / Expenses / Fuel tabs) is available from Settings when offered by the current build.

---

## Help & About

![Help](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![About](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Help** — on-device quick start, setup tutorials, link to this manual, self-host setup index.
- **About** — version, licenses, GitHub, this manual (bundled offline + online HTML when published).

---

## Related docs

- [USER_GUIDE.md](reference/USER_GUIDE.md) — condensed reference
- [self-host/INDEX.md](reference/self-host/INDEX.md) — self-hosted photo/tabular setup
- [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md) — merge, recovery, duplicates
- [REPORTS_METRICS.md](reference/REPORTS_METRICS.md) — economy metrics detail
