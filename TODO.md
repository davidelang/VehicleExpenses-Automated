# TODO

Backlog only. Completed items → `CHANGELOG.md` § Backlog completed. Journal → `ENGINEERING_LOG.md`.

- [x] History purge: strip git blobs >100MB (fat Paddle JNI); see `docs/reference/GIT_HISTORY_OVERSIZE_BLOB_PURGE_20260713.md` (2026-07-13).

## Backlog (native / paddle)
- [ ] True `LITE_BUILD_TAILOR` for **x86_64** emulator (space only; pin stays slim — android-x86 LITE_BUILD_TAILOR drops KernelRegistrar / calib kernels on NDK r28c)
- [x] **armeabi-v7a** product path: tailor + int8 fp32-calib (no arm82 fp16), ~0.75MB jni/light, abiFilters, models `prod_u8fp32_u8` (libpin-paddle-cleanup 2026-08-04)
- [x] Non-git `[[source]]` + **paddle-models** pin; NDK **r28c** three-ABI products; multi-ABI SO smoke under QEMU; pin `./build`/`./test` gates
- [x] Multi-ABI OCR functional harness under QEMU (arm64 + x86 + **armv7** full det→deskew→crop→rec PASS)
- [x] **BUG: armv7 det heatmap all-zero** — root cause: `--quant_model=true` on armv7 opt (broken det.nb). Fixed 2026-08-04: analytic u8 only (no weight quant); rec float CTC out; slim armv7 SO default. QEMU+Pixel PASS `ABCD12345`.
- [x] Strip debug information and excessive logging from Paddle Lite **x86_64** Android build (binary size)
- [ ] **16KB residual (armv7 multi-ABI purity only)** (see `docs/reference/16k-pages-compatibility-notes.md`): OpenCV **armv7** pin still 4KB; paddle armv7 max-page-size. **Done (Play/64-bit path):** `useLegacyPackaging=false`; app CMake `-Wl,-z,max-page-size=16384` + `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`; rclone 16KB; OpenCV arm64/x86_64 pin; CameraX→1.6.1; drop UPX; NDK `libc++_shared`; First 10 before/after on emu-5554 PASS (`dev-ai-interaction/scratch/16k-5554-first10-20260805/`).

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
- [ ] **Deep linking** (product routes still open per `docs/reference/NAVIGATION_MAP.md`; experiment automation only: `vehicleexpenses://experiment/{align,pump}?auto=first10`)

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


Expense receipt field extraction helper (assistive OCR propose vendor/amount/line items; HITL confirm; offline): research cache + open decisions — dev-ai-interaction/research/expense-receipt-field-extraction-deep-research-20260801.md (also see RECEIPT_PARSING_RESEARCH.md; existing backlog bullet "Expense receipt parsing")

~~Keep armeabi-v7a: true LITE_BUILD_TAILOR + pin jniLibs~~ — **done** (slim default ~3.1MB after zero-heatmap fix; analytic models; abiFilters). Remaining: OpenCV armv7 16KB fat jni if still needed; optional re-tailor after lists match. No hand-copied `libc++_shared` (NDK packaging).

~~Pin slim armeabi-v7a paddle JNI + artifact/app jniLibs~~ — **done** (strip-unneeded; get-artifacts rows).
Email loyalty: live Gmail/IMAP production hardening + expense-from-email path still open; fuel Shell/Sam's Room ingest shipped on master

extractmail: browser-based human-confirm extract helper (open file/email; avoid auto-picking non-visible fields)

Vehicle preferred fuel grade/product field for future auto-assignment of loyalty email fills

third_party get-artifacts pick=smart (release/RC/nightly tiers) when from uses globs; fallback mtime
