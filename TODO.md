# TODO

Backlog only. Completed items → `CHANGELOG.md` § Backlog completed. Journal → `ENGINEERING_LOG.md`.

- [x] History purge: strip git blobs >100MB (fat Paddle JNI); see `docs/reference/GIT_HISTORY_OVERSIZE_BLOB_PURGE_20260713.md` (2026-07-13).

## Backlog (native / paddle)
- [ ] True `LITE_BUILD_TAILOR` for **x86_64** emulator (space only; prod-path speed matches fat kernels)
- [ ] Keep **armeabi-v7a** (aftermarket car head units): true `LITE_BUILD_TAILOR` for Paddle Lite + pin-build OpenCV fat `libopencv_java4.so` for armv7 (16KB pages); do not drop the ABI
- [ ] Strip debug information and excessive logging from Paddle Lite **x86_64** Android build (binary size)
- [ ] **16KB page size alignment:** rebuild OpenCV, rclone/gomobile, and other prebuilt `.so` libs with 16KB ELF segment alignment (see `docs/reference/16k-pages-compatibility-notes.md`)

## Backlog (OCR / alignment / identity)
- [ ] **Dashboard Polarity:** refine polarity detection beyond simple corner sampling (Algorithm A/B fallback)
- [ ] **Conflict resolution:** field-level UI for multi-device sync column conflicts + wire `ConflictResolutionScreen` into identification flow for ambiguous matches (`ConflictResolutionScreen` **exists**; identification wiring incomplete)
- [ ] **Landmark management:** add ability to remove landmarks (not only ignore-crop); improve landmark CRUD in Manage Vehicles
- [ ] **BufferSet anti-pattern audit:** eliminate cached `Mat`/`Slice` pointer aliases repo-wide (see `docs/specs/BUFFER_SET_SPEC.md`)

## Backlog (sync / settings / data model)
- [ ] Multi-currency normalization: convert amounts to a standard currency using exchange rates (row storage done)
- [ ] Expense multi-vehicle picker UI + multi-page receipt capture UX (schema/sync ready)
- [ ] Import **fill data** and **expense receipts** from email and/or file pickers (not only camera / gallery)
- [ ] **Email hook:** receive/import fill and expense data via email intent or similar
- [ ] **OnlyOffice / Collabora** tabular sync (catalog + DeferredTabularBackendStub present; **real backends still TODO** — prior “spike NO-GO” was API approach; product still wanted)
- [ ] **MSAL app registration:** replace placeholder `msal_auth_config.json` with real Azure app registration for managed OneDrive
- [ ] **Deep linking** (not implemented per `docs/reference/NAVIGATION_MAP.md`)

## Backlog (location)
- [x] **Location Lookup Worker:** background POI resolution (Overpass/OSM; `dev-ai-interaction/LOCATION_LOOKUP_WORKER.md`)
- [x] **Troubleshoot missing lat/long:** EXIF from photos; if absent, explicit location permission + capture at save

## Backlog (features / product)
- [ ] **Expense receipt parsing:** OCR/parse store name, cost, line items from receipt photos
- [ ] **ODB-II integration:** live odometer reading
- [x] **Advanced reports and charts** (beyond current Reports screen)
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

Trip tax-mile reporting (remaining polish): open→open packaging/export if still needed — open-only + Lab trip miles/personal filters shipped via ui-followups

i18n later: language packs for LTR locales; RTL and beyond deferred — see dev-ai-interaction/research/i18n-rtl-and-beyond-languages-20260730.md (odometers/pumps still primarily Western digits; full UI RTL/complex scripts much later)

i18n language packs (later): include Help + user-manual pipeline translation; debug/failure/feedback email templates stay English; RTL/beyond still deferred

Location multi-candidate picker: nearby POIs ranked by distance/accuracy; pick one

Post-save location confirm UI on edit screens (silent worker fill uses confirmed:false)

Reports multi-select vehicle checkboxes + Sum/Average (not Each-only); deferred from efficiency Each-vehicle

Trip miles packaging polish (export labels/annual packs); core open-only + implicit personal shipped

Vehicle preferred fuel grade/product field (e.g. regular/premium/diesel) for future auto-assignment of loyalty email fills; assignment UX remains separate work

extractmail: browser-based human-confirm extract helper (open file/email; avoid auto-picking non-visible fields)

After third_party pin/fetch-deps/get-artifacts happy path (OpenCV first): make remotetable + extractmail well-behaved — build from fetch-deps ro src (out-of-tree build dirs under src), get-artifacts to artifact/; reproducible builds nice-to-have only (low effort). Scope: third_party process project + VE pin alignment.

third_party get-artifacts pick=smart (future): try tiers to pick best artifact when from uses globs — best-to-worst: (1) release *x.y.z (numeric per-tier, y/z optional; 1.10>1.9); (2) RC *x.y.z-rcN (higher N better); (3) nightly *x.y.z-N-gHASH (git describe); plus common naming practices. Fallback mtime. pick=mtime/sort/sort-n remain explicit; smart = try-everything.
