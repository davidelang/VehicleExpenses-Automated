# Operational Handbook: Multi-Agent Protocol

This document provides the detailed procedural logic for the VehicleExpenses-automated repository. 

## 1. Phase-Specific Mandates

### Phase 1: Research (Discovery)
- **Goal:** Map the codebase, validate assumptions, and reproduce issues.
- **Barrier:** You are allowed to read any file, but you have NO authority to change any tracked file.
- **Sandbox:** All scripts and logs must be stored in `dev-ai-interaction/`.

### Phase 2: Strategy (Planning)
- **Goal:** Propose a comprehensive, idiomatic solution.
- **Integrity:** The turn where you propose a plan must be **Application-Implementation-Free**.
- **Sandbox:** You may use tools to write plans or scripts within the sandbox during this turn.

### Phase 3: Execution (Implementation)
- **Goal:** High-fidelity transcription of the approved plan.
- **Exclusivity:** You MUST implement ONLY what was approved. No Boy Scout cleanups.
- **Forensic Verification:** You MUST re-read modified files and confirm `./build_app` success before finishing.

## 2. Stability & Build Policy (3-3-3 Rule)
- **Strike 1-3:** 3 attempts to fix build errors, then `git reset --hard builds`.
- **Strike 4-6:** 3 more attempts, then reset.
- **Strike 7-9:** Final 3 attempts, then reset and perform a **Mandatory Forensic Analysis**.

## 3. Engineering Standards
- **OCR:** Multi-engine approach (ML Kit, Paddle).
- **Alignment:** 4-DOF Affine transforms (Translation, Rotation, Scale).
- **Vetoes:** Primary matching signal is the **Automated Word Veto**.
- **Coordinates:** Normalized (0.0 to 1.0) based on image dimensions.
