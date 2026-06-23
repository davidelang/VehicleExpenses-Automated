# TODO

> **Reconstruction note (2026-06-23, updated with user feedback):** Produced from exhaustive git history (800+ commits) + code verification. All items unioned; completion = marked completed in any version.
> Updated per direct user feedback on open items (2026-06-23):
> - unclipBox moved to Rejected.
> - BitmapFactory→ImageDecoder replace: obsolete (direct file ingestion in QuickFillup/experiment paths; no camera in experiment for this).
> - ImageNet normalization: obsolete.
> - NativePaddleEngine → NativeVisionSystem rename: obsolete.
> - Phase 7 BufferSet simplified to anti-pattern audit item.
> - Multi-Strategy Voting: completed (obsolete).
> - Normalization Discrepancy BUG: fixed via ICRS migration.
> - Paddle V3 Phase 2 Greedy Numeric: completed (obsolete).
> - Dashboard Polarity, Location Lookup Worker: still pending.
> - Remaining engineering mandates: completed/largely obsolete (replaced by BufferSet work).
> Verified via grep/read_file on OdometerOcrUtils.kt, NativeImageUtils.cpp, BufferSet.cpp, Experiment*/QuickFillup, ICRS specs, etc. (see open-items-vs-current-code-20260623.md and follow-ups).

## Protocol, Mandates & AI Alignment (from recent meta + syncs)
- [x] Safe leading env assignment prefix support ("KEY=val cmd") for all already-allowed bash commands + promote agent-1 "don't ask again" commands to global checked-in config (plan approved 2026-06-13) [and all its sub-steps]
- [x] Refactor Agent Workspace Syncing (setup_agent.sh, update-rules.sh, links, etc.)
- [x] Fix Sandbox Policy Permissions (various .gemini and .grok policies)
- [x] Refine update-rules.sh Robustness, jq/whitespace permissions, ls whitelist, etc.
- [x] Enforce Git Reset and Validation Rigor + tiered policies
- [x] Fix Master Agent 'works' Tag Violation + Safety Override
- [x] Separate the orchestration layer (run-*, update-rules.sh, .grok/, permission model, worktree management, multi-agent brain) from the application source into distinct trees/concerns (orchestration-layer-separation-and-cleanup-plan)
- [x] Mandate Guardrail: Explicitly anchor corrected mandates in TODO.md to prevent AI memory conflicts (linear history, 3-3-3, build_app exclusive, user approval for resets)
- [x] Meta plans: Robust Plan/Execute Cycle, Interactive Strategic Planning + Continuity, project-facts.md stable facts only (2026-06-12)

## Active Work / Open Items (union from richest historical snapshots)

### Pump / BinPeak / Report Experiment (recent phases mostly completed in history; any remaining listed here)
- (Most detailed per-plan phases from 20260619-20260622 plans are completed per snapshots. No major open pump phases identified in latest good versions beyond general diagnostics.)

### OCR Engine & Post-Processing (Advanced Refinements)
- [ ] **Advanced DB-PostProcess (Refinement 2.1) for NativePaddleEngine:**
    - [ ] Switch `boundingRect` to `minAreaRect` for tilted text support.
    - [ ] Implement `warpPerspective` for rotated text crop extraction.
- [ ] **Final Validation (Phase 4):**
    - [ ] Benchmark accuracy vs. ML Kit using the 12-image test set / 140-image ground truth.
    - [ ] Compare "Veto" accuracy between ML Kit and Paddle-Lite discovery.
- [ ] Strip debug information and excessive logging from the Paddle Lite `x86_64` Android build (reduce binary size).

### Alignment, Deskew, Identity & Matching
- [ ] **Dashboard Polarity:** Refine dashboard polarity detection to go beyond simple corner sampling (needed for Algorithm A/B fallback logic).
- [ ] **Adaptive Thresholding:** Investigate and resolve Otsu's threshold "blackout" issues where it occasionally erases all text in specific dash reports.
- [ ] **Deskew Forensic Logging:** Add text-block-level logging to the deskew stage and unify coordinate systems for forensic analysis.
- [ ] **Multi-Scale Discovery:** Implement a multi-resolution discovery pipeline (e.g., full-res + 2048x2048) to resolve "landmark blindness".
- [ ] **Conflict Resolution Integration:** Connect the existing `ConflictResolutionScreen` to the identification flow for ambiguous results.

### BufferSet, YUV / Native Ingestion & Architecture
- [ ] **Anti-pattern Audit (simplified from Phase 7 BufferSet Final Migration & Decommissioning):** Repository-wide audit to eliminate variable-assignment anti-patterns (e.g. storing manager.s or slice.yuv in local vars); enforce direct chaining from BufferSet to prevent stale-pointer/affinity errors.
- [ ] **High-Resolution DNG & Zero-Copy Ingestion (continuing):**
    - [ ] Redirect ingestion flow to native YUV primary.
    - [ ] Implement native JPEG ingestion using OpenCV `imread`.
    - [ ] Integrate native RAW decoder (e.g. LibRaw) for zero-copy DNG.
    - [ ] Buffer Borrowing and Dual-Plane BufferSet optimizations.
- [ ] **Chain-of-Command / Variable Anti-pattern Audit:** Repository-wide audit to eliminate storing slices/handles in local vars; enforce direct chaining.
- [ ] **BufferSet Audit:** Eliminate variable-assignment anti-patterns (e.g. manager.s, slice.yuv); prevent stale-pointer/affinity errors.

### Location, Sync & Data
- [ ] **Location Lookup Worker:** Re-implement the background geocoding worker for automated gas station identification.
- [ ] **Sync Parity:** Update `CsvManager` and `GoogleSheetsClient` to handle latitude, longitude, and formatted address fields.

### Documentation & Technical Reference
- [ ] Create `docs/reference/DATABASE_SCHEMA.md`
- [ ] Create `docs/reference/SYNC_PROTOCOL.md`
- [ ] Create `docs/reference/OCR_ENGINE_STRATEGY.md`
- [ ] Create `docs/reference/ALIGNMENT_PIPELINE.md`
- [ ] **Technical Reference Backlog** and NDK directory migration to git subproject (`ndk/` locked to r20b).

### Future Features & Integrations (still open across history)
- [ ] **Expense Reports & Receipts:** Take picture of receipt, store for reference, attempt parse (name, cost, line items). Consider EXIF lat/long for repair shops.
- [ ] **ODB-II Integration:** For live odometer reading.
- [ ] **Cloud Image Backup Options:** Google Photos, Amazon Photos, Dropbox, SSH, HTTP/CGI, library survey.
- [ ] **Advanced Reports and Charts.**
- [ ] **UI Polish:** dark mode / tablet / all sizes/resolutions.
- [ ] **Improve reference dash photo setup UI:** Add odometer confirmation dialog.
- [ ] **Settings Toggle:** Add settings toggle for OCR confidence threshold.
- [ ] **Refactor `OdometerOcrUtils.kt`:** Decompose into smaller utilities (BitmapMathUtils, OpenCvFilters, etc.).
- [ ] **Refactor `TfLiteOcrUtils.kt`:** Extract DBNet post-processing to standalone.

## Infrastructure & Protocol (selected open or in-progress from history)
- [ ] **Migrate NDK directory to Git Subproject (`ndk/`)**
- [ ] **BUG:** Investigate/Fix inability to write to `dev-ai-interaction/plans` during plan mode (if still relevant).
- Other protocol/mandate items from 2026-06 meta plans are completed (see top and Completed section).

## Completed / Historical
(Union of all items marked [x] in any version. High-level or grouped for readability. Many detailed pump experiment plans from June 2026 are included at summary level.)

- All listed 2026-06 meta/policy items (leading env prefix, workspace syncing, sandbox policies, jq rules, git reset rigor, works tag, orchestration layer separation, mandate guardrails, interactive planning meta plans, etc.)
- Pump experiment plans (20260619-20260622): persist-pump-decision-data..., fix-peak-detection..., fix-redbox-combined-histogram..., implement-p4-binarization..., implement-varying-calculated-blue..., fix-pump-probs-decimal..., fix-pump-distinct-cost-volume..., fix-classifier-numeric..., fix-remaining-report-issues, fix-4box-report-issues, complete-real-4box..., finish-4box..., recovery-4box... (and all their phases), streaming-json-with-per-photo-fragment-staging, externalize-p4, golden pump photos deploy, binpeak timing, object-based blue/annotated, binpeak range changes, wide cleaning scoping, maxwidth, redbox hist sampling, largeHeap manifest, tier buffer restore, int8 retries, etc.
- BufferSet / Native: Phase 25.x (Short-Run Rotation Refactor, Annotate & Compress extract, Incremental Migration steps 1-5.x, Pointer Invalidation Fix, Remove OCR Gating), Phase 1-6 of BufferSet migration, Stateless Native Snapshot, YUV Handle infrastructure progress.
- OCR / Alignment / Core: Automatic OCR on every photo, Camera-first flow, Gallery Import with auto OCR, Reference dash auto-matching (perceptual hash), VehicleViewModel + Room, Hierarchical Tiered Logic (Tier1Veto first), Strict "No Match", Re-enable NativePaddleEngine, Alignment Registry (dynamic strategies), Report Flexibility (dynamic), Traceability (frame-by-frame HTML/JSON), 7-Segment Robustness (180° + remap), Local Python Benchmarking, Deep Trace Phase 1+2, Text-Based Leveling (0.2°), Reference Image Rendering (blue/red), OCR Filtering (area-based), TFLite Strategy Evaluation, Tesseract/Paddle diagnostic work, Paddle Valley Mono Iterative, applyContrastStretch, deskew_data logging, landmark angle, Final Validation benchmarks (in some snapshots).
- Multi-Strategy Voting (completed; superseded by ICRS/alignment/veto logic).
- Normalization Discrepancy BUG (fixed via full migration to ICRS coordinates; legacy per-axis 0.0-1.0 normalized coords obsolete).
- Paddle V3 "Phase 2 — Greedy Numeric Decoding" (completed).
- Remaining engineering mandates (ALPHA_8 phase-out, direct native/YUV sourcing for OCR, memory recycle) largely completed/obsolete (replaced by BufferSet + related work).
- Other historical: ImportOldPicturesScreen + FuelViewModel integration, permissions (CAMERA), icon updates, various housekeeping/sync TODO updates, "update todo", "housekeeping: restore Critical Recovery roadmap", "Sync TODO.md with codebase reality and protocol hardening".
- Obsolete items removed from active: BitmapFactory→ImageDecoder replace (direct file ingestion in QuickFillup + experiment photo paths), ImageNet normalization, NativePaddleEngine→NativeVisionSystem rename.

(Full per-commit details live in git history and the versions/ extracted in dev-ai-interaction/todo-reconstruction/versions/.)

## Explicitly Rejected Ideas
- **Dynamic Veto Frequency Filter / Global IDF Word Filter:** ... (see historical versions)
- **Needle-Based Correction:** ...
- **Unclip Box for Recognition:** Tried; inferior to expandByValleyStop.
- **unclipBox expansion logic (Ratio 1.5):** (per current feedback; no implementation and superseded).
- Other ideas noted in older snapshots (e.g. Skip-Deskew discovery pipeline listed as [ ] in one but contextually rejected elsewhere).

Last reconstructed: 2026-06-23 (git history) + 2026-06-23 user feedback verification pass.
