# Changelog

## Backlog completed (moved from TODO, 2026-07-30)

### Docs / product UX (audit close)
- **Generate UI manual / in-app guide** — illustrated `docs/user-manual.md` + HTML pipeline; in-app `UserManualActivity` WebView; Help/About entry points (`PR-instruction`, 2026-07-16)
- **Quick Fill: Settings default currency/volume to "use system"** — Settings already expose System default for currency and volume; Quick Fill resolves `currency_symbol` / volume prefs (stale backlog; verified on master 2026-07-30)

### minor-fixes merge (PR-minor-fixes)
- **Trip recording** — open-only Trip Tracking (`TripTrackingScreen`, Room v17 `tripType` / `tripTypesJson`, Close → Personal start, purpose types, odo camera); tax-mile phase-2 still open
- Photo sync: bulk download vehicle refs only; on-demand fuel/expense “Fetch image from archive” + path scrub
- Fuel History + edit; Syncing page split; Quick Fill notes; Settings/About polish
- Reports Lab (experimental charts hub; does **not** close “Advanced reports and charts”)
- Unit display façades (`VolumeUnits` / `UnitFormat`); UI_COMPATIBILITY + shared chrome

## Backlog completed (moved from TODO, 2026-07-13)

### Sync / settings / data (2026-07-12 — 2026-07-13)
- Multi-destination spreadsheet + photo sync; `TabularShareApi`; rclone photo backends (Drive / OneDrive / S3 / Other)
- Row-level currency persistence; sheet tab rename on vehicle rename; CSV/tabular parity
- Expense multi-vehicle + multi-photo **schema** (Room v12, `ExpensePhotoUrls`, tabular + cloud manifest)
- Google Sheets/Drive browse-pick-create UI; in-app rclone remote create/manage; self-host docs
- Lat/long + location fields in tabular/CSV/sheet schema (`TabularSchema`)
- Host Paddle precision verification + smoke tests (sandbox Phases 1–4)
- ENGINEERING_LOG append-only wrapper; orchestration layer separation

### OCR / alignment / ingestion
- ICRS coordinate migration (normalization discrepancy closed)
- LibRaw zero-copy DNG ingestion; `minAreaRect` in native/Kotlin post-process
- Multi-scale discovery pipeline; sub-pixel landmark refinement; Camera2 buffer borrowing; dual-plane BufferSet optimizations
- Native YUV / BufferSet architecture (production paths; anti-pattern **audit** still open in TODO)
- Deskew forensic logging in experiment JSON (`deskew_data_*`); hybrid contrast / valley-peak stretch on production OCR path
- Multi-Strategy Voting, Paddle V3 Phase 2 greedy numeric, engineering mandates (ALPHA_8 phase-out direction) — superseded by BufferSet + ICRS work
- Reference dash photo setup + odometer confirmation flow; UI polish (dark mode / tablet / responsive)

### Infrastructure / refactor
- Refactor `OdometerOcrUtils.kt` / `TfLiteOcrUtils.kt` decomposition (per project state)
- Extra cloud backup targets via rclone Other + first-class Drive/OneDrive/S3
- OCR confidence threshold settings toggle (removed in settings-hygiene pass — behavior replaced)
- Sync protocol reference: `docs/reference/SYNC_BEHAVIOR.md` (+ TabularShare) supersedes planned `SYNC_PROTOCOL.md`

### Obsolete (removed from active backlog)
- BitmapFactory→ImageDecoder replace; ImageNet normalization constants; NativePaddleEngine→NativeVisionSystem rename

## Declined / cancelled

- **unclipBox** / **warpPerspective** crop expansion — superseded by `expandByValleyStop` / valley-based expansion
- Dynamic Veto Frequency Filter; Needle-Based Correction; Skip-Deskew discovery pipeline
- Orphaned cloud image cleanup — cancelled (manifest + pending rules sufficient)

## v1.0 (2025)
- Full two-way Google Sheets sync
- Configurable background sync
- Play Store ready