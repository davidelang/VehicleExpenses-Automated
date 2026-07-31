# TODO

Backlog only. Completed items → `CHANGELOG.md` § Backlog completed. Journal → `ENGINEERING_LOG.md`.

- [x] History purge: strip git blobs >100MB (fat Paddle JNI); see `docs/reference/GIT_HISTORY_OVERSIZE_BLOB_PURGE_20260713.md` (2026-07-13).

## Backlog (native / paddle)
- [ ] True `LITE_BUILD_TAILOR` for **x86_64** emulator (space only; prod-path speed matches fat kernels)
- [ ] True `LITE_BUILD_TAILOR` for **armeabi-v7a** or drop the ABI (space only if kept)
- [ ] Strip debug information and excessive logging from Paddle Lite **x86_64** Android build (binary size)
- [ ] **16KB page size alignment:** rebuild OpenCV, rclone/gomobile, and other prebuilt `.so` libs with 16KB ELF segment alignment (see `docs/reference/16k-pages-compatibility-notes.md`)

## Backlog (OCR / alignment / identity)
- [ ] **Dashboard Polarity:** refine polarity detection beyond simple corner sampling (Algorithm A/B fallback)
- [ ] **Conflict resolution:** field-level UI for multi-device sync column conflicts + wire `ConflictResolutionScreen` into identification flow for ambiguous matches
- [ ] **Landmark management:** add ability to remove landmarks (not only ignore-crop); improve landmark CRUD in Manage Vehicles
- [ ] **BufferSet anti-pattern audit:** eliminate cached `Mat`/`Slice` pointer aliases repo-wide (see `docs/specs/BUFFER_SET_SPEC.md`)

## Backlog (sync / settings / data model)
- [ ] Multi-currency normalization: convert amounts to a standard currency using exchange rates (row storage done)
- [ ] Expense multi-vehicle picker UI + multi-page receipt capture UX (schema/sync ready)
- [ ] Import **fill data** and **expense receipts** from email and/or file pickers (not only camera / gallery)
- [ ] **Email hook:** receive/import fill and expense data via email intent or similar
- [ ] **OnlyOffice / Collabora** tabular sync (deferred stub; spike NO-GO for headless cell API)
- [ ] **MSAL app registration:** replace placeholder `msal_auth_config.json` with real Azure app registration for managed OneDrive
- [ ] **Deep linking** (not implemented per `docs/reference/NAVIGATION_MAP.md`)

## Backlog (location)
- [x] **Location Lookup Worker:** background POI resolution (Overpass/OSM; `dev-ai-interaction/LOCATION_LOOKUP_WORKER.md`)
- [x] **Troubleshoot missing lat/long:** EXIF from photos; if absent, explicit location permission + capture at save

## Backlog (features / product)
- [ ] **Expense receipt parsing:** OCR/parse store name, cost, line items from receipt photos
- [ ] **ODB-II integration:** live odometer reading
- [ ] **Advanced reports and charts** (beyond current Reports screen)
- [ ] **Prepare for Play Store** (signing, listing, policy, release pipeline)
- [x] **Generate UI manual / in-app guide** (expand beyond `docs/reference/USER_GUIDE.md`)
- [x] Quick Fill: Settings default currency/volume to "use system"
- [ ] Quick Fill: GPS-based currency default + locale filter for symbol chooser

## Backlog (engineering / tech debt)
- [x] **Dead code cleanup**
- [ ] **Code optimization / hardening**
- [ ] **Remove pump and alignment experiment UIs** once no longer needed for diagnostics
- [ ] Create `docs/reference/DATABASE_SCHEMA.md`
- [ ] Create `docs/reference/OCR_ENGINE_STRATEGY.md`
- [ ] Create `docs/reference/ALIGNMENT_PIPELINE.md`
- [ ] **NDK as git subproject:** migrate standalone NDK tree to tracked `ndk/` subproject with pinned revision
# Future work

Missed fill logging: fuel added but not recorded so MPG/$/mi cannot span that gap; needs UX + report/side-effect handling

Trip tax-mile reporting (phase-2): open→open odo deltas by tripType; zero-length segment filter; economy-chain explicit ignore of trip starts (Trip Tracking open-only is shipped)

i18n later: language packs for LTR locales; RTL and beyond deferred — see dev-ai-interaction/research/i18n-rtl-and-beyond-languages-20260730.md (odometers/pumps still primarily Western digits; full UI RTL/complex scripts much later)

i18n language packs (later): include Help + user-manual pipeline translation; debug/failure/feedback email templates stay English; RTL/beyond still deferred
Location multi-candidate picker: show nearby POIs ranked by distance/accuracy; pick one (accuracyM informs ranking)

Post-save location confirm UI: confirm deferred/worker place fill on edit screens (silent fill uses confirmed:false)
