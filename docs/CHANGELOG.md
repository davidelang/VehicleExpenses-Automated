# Changelog

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