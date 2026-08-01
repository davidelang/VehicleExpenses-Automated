---
type: implementation-reference
status: dynamic
ai_directive: "This is a downstream reference. It MUST be updated continuously to reflect the current state of the codebase. If you change a function or architecture described here, update this document in the same commit."
---

# Vehicle Expenses Automated — Navigation Map

## Drawer order (always visible)

1. **Quick Fill-up** (`quickfill`) — start destination; camera/OCR fuel entry  
2. **Start trip** (`triptracking`) — open-only trip segments as fuel rows with Trip Type  
3. **Manage Vehicles** (`managevehicles`) — vehicles, reference dash photo, crops, landmarks  
4. **New expense** (`expense`) — non-fuel expenses (create; edit via `expense/{id}`)  
5. **Reports** (`reports_lab`) — product reports hub (Lab); not experiment-gated  
6. **Settings** (`settings`) — units, photos, debug QF, experiment gate  
7. **Syncing** (`syncing`) — spreadsheet + photo sync summary / Sync now  
8. **Help** (`help`)  
9. **About** (`about`)

## Drawer — experiment gate only (`show_experiment_screens`)

Order under the toggle (Settings):

1. **Alignment Experiment** (`experiment`)  
2. **Pump Experiment** (`experiment_pump`)  
3. **Import Old Pictures** (`import` / `import?review=1`) — batch historical photos  

When the gate is **off**, Import is **not** in the drawer. Top-bar **`?N`** still opens import review when pending questions exist.

## Not in the main drawer

| Surface | How to open |
|---------|-------------|
| Expense list | Reports hub card → `expenselist` |
| Fill history / Fuel history | Reports hub card or deep `fuelhistory` |
| Edit fill | `fuel/{fuelId}` from history / reports fills |
| Spreadsheet / Photo destinations | Syncing → cards, or Settings sub-routes |
| Legacy **Reports & Charts** | **Removed** (no route `reports`; no `ReportsScreen`) |

## Top app bar chrome

| Control | Role |
|---------|------|
| **☰** or **←** | Open drawer, or back from sub-routes (settings children, fuel edit, reports_lab children). Report children + `expenselist` show **☰ and ←**. |
| **ⓘ** Page help | Shown when the current screen calls `RegisterPageHelp` (leading, next to menu/back). Registration uses a **generation token** so dispose of a previous screen does not clear a newer screen’s help. Phone-narrow titles use page name only so Info stays visible with badges. |
| **`?N`** | Pending import review count → `import?review=1` |
| **`!`** | Stored sync failure → **Syncing** |

Priority if space is tight: menu/back → **Info** → `?N` → `!`.

## Page flows (selected)

- **Quick Fill-up** / **New expense** — stay in place after save (drawer for Reports)  
- **Start trip** — writes trip-start fuel rows; no separate close column  
- **Reports hub** → `reports_lab/time` (**Time based reports**), `expenses`, `fills`, `vehicle_summary`, `trips`; hub card → `expenselist`. Legacy routes `efficiency` / `cost_trends` / `monthly` redirect to `time`.  
- **Syncing** → `settings/spreadsheet_sync`, `settings/photo_backup`  
- **Fuel History** → fills only → **Edit Fill**; trip starts listed under **Trip miles**  

## Notes

- **Start destination:** `quickfill`  
- **Product Reports** = Lab hub only (`reports_lab/*`).  
- **Fill inventory** (Fuel History + Fill history report) excludes trip starts (`tripType` non-blank). Trip list is on Trip miles.  
- Conflict resolution UI exists but is not drawer-linked.
