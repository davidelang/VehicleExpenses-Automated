# Operational Handbook: Multi-Agent Protocol

This document provides the detailed procedural logic for the VehicleExpenses-automated repository. 

## 1. Phase-Specific Mandates

### Phase 1: Research (Discovery)
- **Goal:** Map the codebase, validate assumptions, and reproduce issues.
- **Barrier:** You are allowed to read any file, but you have NO authority to change any tracked file. You MUST operate in `mode = plan`.
- **Sandbox:** All scripts, temporary data, and logs must be stored in `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/`.

### Phase 2: Strategy (Planning)
- **Goal:** Propose a comprehensive, idiomatic solution.
- **Integrity:** The turn where you propose a plan must be **Application-Implementation-Free**.
- **Sandbox:** You may use tools to write plans or scripts within the sandbox during this turn.
- **Approval:** You MUST wait for explicit user approval in the chat before calling `exit_plan_mode`.

### Phase 3: Execution (Implementation)
- **Goal:** High-fidelity transcription of the approved plan.
- **Exclusivity:** You MUST implement ONLY what was approved. Logic carry-overs or "Senior best practices" not in the plan are forbidden.
- **Forensic Verification:** Before finishing, you MUST re-read the modified files to verify the implementation against the plan, and confirm `./build_app` success.
- **Plan Mode Transition:** You MUST call `enter_plan_mode` at the end of every turn where a build was attempted or when implementation is complete.

## 2. Deployment & Versioning (CRITICAL)
- **No Deployment:** The agent is **STRICTLY FORBIDDEN** from running `./deploy` or `./gradlew installDebug`. Deployment is a manual user action.
- **Versioning Mandate:** Because the app uses `git describe` for its version string, you MUST commit all changes (via `./build_app`) BEFORE triggering a build. This ensures the version number in the resulting build accurately reflects the state of the code.

## 3. Stability & Build Policy (3-3-3 Rule)
- **Strike 1-3:** 3 attempts to fix build errors, then `git reset --hard builds`.
- **Strike 4-6:** 3 more attempts, then reset.
- **Strike 7-9:** Final 3 attempts, then reset and perform a **Mandatory Forensic Analysis**.

## 4. Git Hygiene & State Tracking
- **Linear History:** Strict linear history is required. NO `git commit --amend`.
- **Tracking Tags:** Use the following tags to track repository state:
    - `builds`: The last commit that successfully passed `./gradlew assembleDebug`.
    - `deployed`: The last commit that was successfully installed on a physical device.
    - `works`: The last commit verified by the user to have no regressions.
- **TODO Integrity:** Every item added to `TODO.md` MUST include enough context (or a link to a plan) for a "Freshly Started Agent" (an agent with zero session memory) to understand and execute it.

## 5. Engineering Standards
- **OCR:** Multi-engine approach (ML Kit, Paddle). No silent fallbacks allowed.
- **Alignment:** 4-DOF Affine transforms (Translation, Rotation, Scale).
- **Vetoes:** Primary matching signal is the **Automated Word Veto**.
- **Coordinates:** Normalized (0.0 to 1.0) based on image dimensions.
