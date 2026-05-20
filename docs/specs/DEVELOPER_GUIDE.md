---
type: intent-spec
status: locked
ai_directive: "This is an upstream specification. DO NOT modify this document to match the codebase. If the code deviates from this spec, the code is wrong. Modifications to this file require a dedicated 'Strategy' turn and explicit user approval."
---

# Vehicle Expenses Automated — Developer Guide

## Sandboxed Research Environment
The repository contains a strict analytical sandbox under `dev-ai-interaction/`.
- **Sandbox Rule:** All analysis scripts, raw device JSON, and diagnostic testing must stay in `dev-ai-interaction/` to keep the primary Android project clean. This directory is heavily git-ignored.
- **Python Analytics:** The sandbox contains specialized python scripts (`analyze_report.py`, `extract_veto_failures.py`, `list_landmarks.py`) that strictly expect the `"results"` array JSON layout introduced in Phase 31.
- **Forensic Extraction:** `./extract_veto_failures.py [report.json]` generates 9 distinct forensic files (split by Engine: ML Kit, Paddle-Lite, Hybrid; and Status: MUTUAL_VETO, AMBIGUITY, MISIDENTIFICATION). These match the HTML report line numbers.

## Current Architecture

### OCR & Alignment Flow
1. **Photo Capture**: Handled by `PhotoPicker.kt`.
2. **Discovery & Veto**: `OcrHarness.kt` extracts landmarks using multiple engines. `ImageAlignmentUtils.kt` evaluates identities using the 1-vs-3+ Veto Rescue algorithm.
3. **Alignment**: The query photo is rotated and mapped using ORB and `AnchorTriangulationEngine` (Geometric Scale/Rotate/Pan).
4. **Extraction & OCR**: Multi-pass filtering (Bilateral, CLAHE, etc.) via `OdometerOcrUtils.kt` cleans crops for final digit evaluation.

## Git Hygiene & Versioning
- The application relies on `git describe` for version strings on the device.
- **MANDATORY**: Always `git commit` before running `./gradlew installDebug`.
- **Tracking Tags:**
  - `builds`: The last commit that passed `./gradlew assembleDebug`.
  - `deployed`: The last commit successfully installed on the device.
  - `works`: The last functionally validated state.

## Debugging

### ADB Logcat
For multiple devices:
```bash
adb -s <serial-number> logcat | grep "com.davidlang.vehicleexpensesautomated"
```
Look for the `OCR_PERF` tag to measure JSON serialization performance.

## Build Process
- Use `./gradlew clean build` to verify changes.
- Project uses **KSP** (Kotlin Symbol Processing).