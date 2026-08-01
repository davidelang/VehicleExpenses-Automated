# Changelog

## ui-followups (2026-07) — product chrome, reports, sync

- **Reports:** Product drawer **Reports** is Lab hub only; legacy production **Reports & Charts** / `ReportsScreen` removed. Efficiency multi-axis chart; filters All/Each/Single; Trip miles; share TEXT/CSV/PDF.
- **Top bar:** **ⓘ** page help (leading, next to menu); short titles on narrow phones so Info stays visible with `?N` / `!`.
- **Drawer:** Import Old Pictures under experiment gate; Expense list via Reports hub (not main drawer).
- **Start trip / Quick Fill:** Shared shutter/save chrome; trip stop-sign Personal-now; portrait camera ~45%.
- **Syncing:** Dest-edit **Sync now**; failure **Details** + full message; Sheets rate-limit wait/retry; bulk `batchGet` compare; orphan failure prune; Sync now survives leaving the screen.
- **Location:** Sole geo package is `location` JSON blob (Room v18); once-per-screen GPS + EXIF; non-blocking POI.

## v2.0 (2026) - Alignment & Multi-Engine Enhancements
- **Phases 25-30:** Implemented multi-engine OCR architectures (Paddle-Lite, TFLite, ML Kit). Switched to normalized (0.0 - 1.0) database crop coordinate bounds. Established engine-synchronized Tier 1 Veto sweeping.
- **Phases 31-34:** Deep JSON report normalization. Added rigorous global ASCII landmark sanitization (filter 32-126) and surgical trim policies.
- **Phases 35-37:** Implemented Landmark Manual Overrides in the UI with a "Show" vs "Run" split for instant manifest hydration. Cleaned up TFLite models. Upgraded UI to High-Contrast Material 3. Offloaded image preprocessing to `Dispatchers.IO` for instant dialog response.
- **Phases 38-41:** Synchronized research python scripts (`analyze_report.py`, `extract_veto_failures.py`) for the new JSON structure. Added status-split forensic logs and Veto Dictionary reporting.
- **Phases 42-48:** Upgraded Anchor-Triangulation alignment to report geometric scale, rotation, and translation variables independently per OCR engine. Implemented the "Least-Vetoed Rescue" Algorithm (1 vs 3+) to save matches in Mutual Veto scenarios. Synchronized HTML and JSON report line numbers.

## v1.0 (2025)
- Full two-way Google Sheets sync
- Configurable background sync
- Play Store ready