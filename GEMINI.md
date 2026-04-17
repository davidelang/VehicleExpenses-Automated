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
- **Corruption Reset:** If the codebase is identified as "seriously compromised" (e.g., accidental mass deletion of core methods, mass unresolved references), IMMEDIATELY `git reset --hard builds`. Do not attempt manual file rewrites to rescue the state. Note that post-reset analysis may recommend going back to `deployed` or `works` tags depending on the analysis.
- **Strike System:** After the 2nd attempt to fix a build failure (Strike 3 total), a reset to the `builds` tag is MANDATORY.
- **Post-Reset Analysis:** After any reset, you must perform a forensic analysis of the failure. Identify if it was an **Execution Error** (implementation mistake) or a **Strategy Error** (fundamentally flawed approach) and share the lesson learned before proposing a new attempt.
- **Post-Reset Verification:** After any reset, perform a full build (`./gradlew clean assembleDebug`) to ensure all non-git resources (libraries, internal assets) are available and the state is valid.
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
