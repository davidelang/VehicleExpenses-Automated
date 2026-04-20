# Gemini Project Mandates

## Workflow & Safety
- **Deployment:** NEVER run `./gradlew installdebug` while an experiment report is running on the device. It will reset the app and lose the progress.
- **Deployment:** the version is defined as 'git describe', so before you build and deploy, all program files should be comitted to the repo so that the version of the app matches the state of the repo at the same commit
- **Workflow:** Operate in a **STRICT report/propose mode**.
  - **MANDATE:** You MUST NOT start making code changes or implementing features without first proposing exactly what is going to be done.
  - **STOP & WAIT:** After proposing a strategy or finishing an inquiry, you MUST stop and wait for an explicit Directive (approval) from the user before proceeding to the Execution phase.
  - **LIMITS:** Do not add new work or perform significant refactoring/cleanup without additional, specific approval.
- **Zero-Tool Rule:** During the "Strategy" phase (proposing a plan), you MUST NOT execute any tools that modify the file system or deploy to devices (`write_file`, `replace`, `run_shell_command`). Proposals must be text-only. The proposal turn must end immediately after the plan is stated.
- **Versioning:** ALWAYS commit changes before building/deploying. The app uses `git describe` for its version string; committing first ensures the report results are tied to the correct hash.
- **Phase Completion:** A phase is not considered complete until it is checked in and compiled. Because `git describe` is used for the version number, you MUST check in your changes before compiling, otherwise the version number in the resulting build will be incorrect.
- **Sandbox:** All analysis scripts, local research (PaddleOCR), and pulled device data MUST stay in the `dev-ai-interaction/` directory. This directory is ignored by git and keeps the workspace clean.

## Build & Stability Policy
- **Protected Zones:** The following directories are protected from automated cleanup:
    - `dev-ai-interaction/` (Research and sandbox)
    - `app/src/main/jniLibs/` (Active native libraries)
    - `app/src/main/assets/libs_backup/` (Native library backups)
- **Corruption Reset:** If the codebase is identified as "seriously compromised", IMMEDIATELY `git reset --hard builds`. Follow this with:
    `git clean -fd -e "dev-ai-interaction/" -e "app/src/main/jniLibs/" -e "app/src/main/assets/libs_backup/"`
    Note that post-reset analysis may recommend going back to `deployed` or `works` tags depending on the analysis.
- **Strike System (3-3-3 Rule):**
    - **Strike 1-3:** If a build fails, you have up to 3 attempts to fix it. After the 3rd failed compile, a reset to the `builds` tag is MANDATORY. This reset does not require a pause.
    - **Strike 4-6:** After resetting, you have another 3 attempts to fix the task using lessons learned. After the 6th total failed compile, another reset to `builds` is MANDATORY. This reset does not require a pause.
    - **Strike 7-9:** After the second reset, you have a final 3 attempts. After the 9th total failed compile, you must reset to `builds` and perform a **Mandatory Forensic Analysis**.
- **Mandatory Forensic Analysis:** After 9 total build failures, you must stop, analyze why the task is failing, and propose a new plan with significant decomposition (smaller pieces) for user review and approval.
- **Safe Harbor Protection:** NEVER reset back past the `builds` tag without explicit user review and approval. If the `builds` tag itself is suspected of being corrupt, you MUST stop and consult the user before taking action.
- **Post-Reset Verification:** After ANY reset (manual or automated), you must perform a full build (`./gradlew clean compileDebugKotlin`) to ensure the baseline is stable.
- **Post-Reset Analysis:** After any reset that requires a pause (after 9 failures or when reset past `builds`), identify if it was an **Execution Error** (implementation mistake) or a **Strategy Error** (fundamentally flawed approach) and share the lesson learned before proposing a new attempt.
- **Mandatory Decomposition:** After two resets for the same task, break the effort into smaller, incremental phases. Confirm a successful build for each phase before continuing.

## Git Hygiene
- For fixing compilation errors, prefer `git commit --amend --no-edit` to keep the history focused.
- **Tracking Tags:** Use the following tags to track state (moving with `-f`):
    - `builds`: Last commit that passed `./gradlew assembleDebug`.
    - `deployed`: Last commit successfully installed on devices.
    - `works`: Last commit verified by the user to have no regressions.
- **Planning:** as new items are identified that will need to be worked on in a future commit, add them to the TODO.md file

## Engineering Standards
- **OCR:** We use a multi-engine approach (Tesseract, ML Kit, TFLite).
- **Alignment:** We use 4-DOF Affine transforms (Translation, Rotation, Scale) instead of 8-DOF Homography to prevent perspective "wedge" distortions.
- **Vetoes:** The primary matching signal is the **Automated Word Veto**. If a dash photo contains a "Golden Anchor" (a word unique to a specific vehicle reference), matching against any other vehicle must be disqualified (-1.0 score).
- **Coordinate Systems:** Landmarks and crops are defined in **Normalized Coordinates (0.0 to 1.0)**. Use the image dimensions stored in `OcrResult` to map these to pixels.
