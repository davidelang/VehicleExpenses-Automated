---
type: implementation-reference
status: dynamic
ai_directive: "This is a downstream reference. It MUST be updated continuously to reflect the current state of the codebase. If you change a function or architecture described here, update this document in the same commit. Code is authoritative if code and this doc disagree."
---

# Compose UI compatibility (mandatory for UI work)

Agent-facing **do / don’t** for unit labels, font scale, deps, reports inventory, and photo bandwidth.  
Binding rule summary: **`AGENT_MANDATES.md`** → section *Compose UI compatibility*.  
Orientation pointers: **`project-facts.md`**. Economy math: **`REPORTS_METRICS.md`**.

**If code and this doc conflict → code wins; update this file in the same commit.**

## 1. Volume

| Do | Don’t |
|----|--------|
| Use `ui/util/VolumeUnits.kt`: `resolvedPreferredVolumeUnit`, `shortLabel` / `longLabel`, `formatVolume` | Invent conversion at display time (storage is already preferred G/L) |
| Label volume fields with unit (e.g. `Volume (L)`) | Hardcode ``${x}G``, bare `"G"`, or unlabeled `"Volume"` for user-facing fuel volume |
| Keep sheet/CSV protocol column name **`Gallons`** (English protocol) | Rename tabular schema headers for locale in a UI-only change |

## 2. Distance / economy labels

| Do | Don’t |
|----|--------|
| Use `ui/util/UnitFormat.kt`: `economyEfficiencyLabel()`, `costPerDistanceLabel()`, `distanceUnitShortLabel()`, `odometerReadingLabel(odo)`, `distanceDeltaLabel(delta)` | Hardcode `"mpg"`, `"MPG"`, `"$/mi"`, `"mi"`, `"miles"` as **unit words** in new user-visible strings |
| Treat odometer as **instrument integer** (no mi/km conversion yet) | Add km/L/100km math without an approved plan |
| Open / undefined distance → **`n/a`** (no fake unit word) | Strings like `"miles n/a"` that embed a unit when value is undefined |

## 3. Currency

| Do | Don’t |
|----|--------|
| Use `ui/util/CurrencyCodes.kt` (`formatAmount`, `formatAggregateSum`, defaults) | Bare `$` / ``$${cost}`` for fuel/expense money in UI or share TEXT |

## 4. Font scale / layout

| Do | Don’t |
|----|--------|
| Material3 typography (`sp`); honor system `fontScale` | Custom density that zeros `fontScale` |
| `softWrap = true`, `maxLines ≥ 2` on tight chrome labels | Fixed tiny `height(N.dp)` that clips text at large font |
| Prefer `heightIn(min=…)` on buttons/rows | Horizontal overflow / right-edge loss of controls (failure) |
| Vertical scroll for long screens | Redesign Quick Fill from scratch for font-only bugs |

## 5. Icons & dependencies

| Do | Don’t |
|----|--------|
| Keep BOM-aligned `material-icons-core` **and** `material-icons-extended` in `app/build.gradle.kts` | Drop icons deps (startup `ClassNotFoundException: Icons`) |
| Use icons from that set | Reintroduce tessdata / `copyTessdataOnce` at Application startup |

## 6. Reports Lab / Vico

| Do | Don’t |
|----|--------|
| Vico **`compose:3.2.3` only** (`ReportsLabCharts.kt` patterns) | Add `vico:compose-m3` (Material3 ABI break vs Compose BOM 2024.10) |
| Treat Lab hub (`reports_lab`) as **product Reports** (drawer **Reports**) | Reintroduce a second “production Reports & Charts” surface without a plan |
| Keep Vico X values ≤ **4 decimal places** (`tsToChartX` quantize) | Pass raw epoch-ms/day floats into Vico line models (crashes GCD) |
| Share TEXT/CSV under `filesDir/reports_lab/` via `ReportsLabShare` | Assume Lab filters replace global Settings |

## 7. Trip starts vs fills

| Do | Don’t |
|----|--------|
| `TripTimeline.isTripStart` / `FuelEconomyChains.isTripStart` — single predicate | Second ad-hoc `tripType.isNotBlank()` inventory rule |
| Fill **counts** and fill **lists**: `withoutTripStarts()` / non-trip only | Count trip starts as fill-ups in Lab fill-facing sets |
| Tax-style miles: Lab **Trip miles** (`reports_lab/trips`) + `TripSegments` | Put tax mile redesign into production MPG chains without a plan |
| Odo range summaries may include trip odo events | Treat open-only trip rows as volume/cost inventory |

See `REPORTS_METRICS.md` (trip row shape + inventory).

## 8. Photo bandwidth

| Do | Don’t |
|----|--------|
| Bulk auto-download **vehicle_ref** + **vehicle_ref_cleaned** only | Bulk-download fuel fill or expense receipt photos on Sync now / worker |
| On-demand UI: **“Fetch image from archive”** + persist local path | Leave permanent zombie paths when local file is gone |
| Scrub unreadable local pointers; preserve `cloudManifest` | Auto-fetch solely because a path went stale |

Helpers: `PhotoBackupCoordinator`, `ArchivePhotoHelpers`, Fuel History / Edit / expense / batch surfaces.

## 9. Deferred (do not expand casually)

- LTR language packs / `strings.xml` translations — TODO + later plan  
- RTL / complex scripts — `dev-ai-interaction/research/i18n-rtl-and-beyond-languages-20260730.md`  
- mi/km prefs, L/100km economy mode  
- Full illustrated `user-manual.md` rewrite (condensed guide: `USER_GUIDE.md`)

## 10. Quick grep checklist (before claiming UI done)

```text
# Unit / money hardcodes (expect none in new UI paths; allow schema "Gallons", OCR training, comments)
"mpg" | "MPG" | "$/mi" | miles n/a | \$\{[^}]+\}G

# Forbidden deps / dead startup
compose-m3
copyTessdata|tessdata/

# Icons still present
material-icons-core
material-icons-extended

# Trip inventory
withoutTripStarts|isTripStart

# Tight chrome (review hits; heightIn preferred)
height\([0-9]
```

Also smoke: large system font — TopAppBar, drawer labels, Share row buttons, Lab filters wrap; no horizontal control loss.

## 11. Cards (tappable only)

| Do | Don’t |
|----|--------|
| Use `ui/components/UiChrome.kt` **`TappableCard`** for rows/tiles that **navigate or activate** | Cards around static KPIs, form fields, switches, camera chrome |
| Bare layout for non-tappable content | Bare `.clickable` list rows that navigate without Card chrome |

## 12. Density (content-measured multi-column)

| Do | Don’t |
|----|--------|
| Use **`AdaptiveItemGrid`** — natural measure with **wrap** constraints (`maxWidth = Infinity`, min 0), then `itemW = max(natural)` clamped to parent W; cols = floor((W+gap)/(itemW+gap)); layout pass fills equal **cell** widths | New peer-list **hardcoded dp breakpoint tables** (`minWidth = 200.dp` for col count) |
| **No fillMaxWidth on grid item roots** inside `AdaptiveItemGrid { }` — use [TappableCard] (wrap-friendly) or wrap content; grid nat pass also wraps so child fill does not snap to full screen | Force `Modifier.fillMaxWidth()` on outermost cell content (makes `itemW ≈ W` → always 1 column) |
| Re-measure with fontScale / density / content (wider text → fewer cols) | Hardcoded **dp floors** in column-count math (e.g. 148.dp min item width) |
| Prefer inside parent vertical scroll | Nested vertical scroll inside the grid |

## 13. Theme accents (non-camera)

| Do | Don’t |
|----|--------|
| `MaterialTheme.colorScheme.*` for badges, efficiency bars, non-camera borders | Hardcoded amber/green hex on app chrome |
| Camera / crop / photo viewer fixed contrast (black/white/greyscale) | Dynamic surface colors on live camera overlays |

## 14. Icons (drawer + Material only)

| Do | Don’t |
|----|--------|
| Material Icons from **core + extended** BOM for Save, Close, Menu, PhotoLibrary, … | Local `ImageVector.Builder` paths for icons Material already provides |
| **No** `icon =` on navigation drawer items (labels only) | Drawer leading icons |
| Prefer **`AppIcon`** or 24.dp + theme tint | One-off sizes/tints without reason |

## 15. Shared controls

| Helper | Pattern |
|--------|---------|
| `EmptyStateText` / Lab `ReportsLabEmpty` | `bodyMedium` + `onSurfaceVariant` |
| `AppDateTimeField` | Full-width **OutlinedButton** trigger; dialogs stay local |
| `AppTextCancel` | Dialog/footer **TextButton** Cancel |
| `AppOutlinedBack` | Full-width leave / cancel form **OutlinedButton** |
| `FeatureScreenHeader` | `headlineMedium` title + optional `bodySmall` subtitle |

## Extra grep (cards / theme / icons)

```text
Color\(0xFF   # non-camera chrome hex (review; camera/theme static OK)
ImageVector.Builder
NavigationDrawerItem\([\s\S]*icon\s*=
```

## Key source paths

| Concern | Path |
|---------|------|
| Volume | `app/.../ui/util/VolumeUnits.kt` |
| Distance/economy labels | `app/.../ui/util/UnitFormat.kt` |
| Currency | `app/.../ui/util/CurrencyCodes.kt` |
| Economy chains / inventory | `app/.../data/batch/FuelEconomyChains.kt` |
| Trip predicate / segments | `app/.../data/trip/TripTimeline.kt`, `TripSegments.kt` |
| Lab charts | `app/.../ui/reports/lab/ReportsLabCharts.kt` |
| Shared chrome | `app/.../ui/components/UiChrome.kt` (`TappableCard`, `AdaptiveItemGrid`, empty/date/cancel/header/icon) |
| Deps | `app/build.gradle.kts` |
| Metrics rules | `docs/reference/REPORTS_METRICS.md` |
| Nav | `docs/reference/NAVIGATION_MAP.md` |
