# Gemini Project Mandates

## Workflow & Safety
- **Deployment:** NEVER run `./gradlew installdebug` while an experiment report is running on the device. It will reset the app and lose the progress.
- **Deployment:** the version is defined as 'git describe', so before you build and deploy, all program files should be comitted to the repo so that the version of the app matches the state of the repo at the same commit
- **Workflow:** Operate in a **STRICT report/propose mode**.
  - **MANDATE:** You MUST NOT start making code changes or implementing features without first proposing exactly what is going to be done.
  - **STOP & WAIT:** After proposing a strategy or finishing an inquiry, you MUST stop and wait for an explicit Directive (approval) from the user before proceeding to the Execution phase.
  - **LIMITS:** Do not add new work or perform significant refactoring/cleanup without additional, specific approval.
- **Versioning:** ALWAYS commit changes before building/deploying. The app uses `git describe` for its version string; committing first ensures the report results are tied to the correct hash.
- **Sandbox:** All analysis scripts, local research (PaddleOCR), and pulled device data MUST stay in the `dev-ai-interaction/` directory. This directory is ignored by git and keeps the workspace clean.
- **Git Hygiene:** For fixing compilation errors, prefer `git commit --amend --no-edit` to keep the history focused.
- **Git Hygiene:** if it takes more than 3 attempts to fix a compile error, revert to the last clean build and try again.
- **Git Hygiene:** if you revert more than 3 times on a single feature, rethink the problem and break it into smaller steps
- **Planning:** as new items are identified that will need to be worked on in a future commit, add them to the TODO.md file


## Engineering Standards
- **OCR:** We use a multi-engine approach (Tesseract, ML Kit, TFLite).
- **Alignment:** We use 4-DOF Affine transforms (Translation, Rotation, Scale) instead of 8-DOF Homography to prevent perspective "wedge" distortions.
- **Vetoes:** The primary matching signal is the **Automated Word Veto**. If a dash photo contains a "Golden Anchor" (a word unique to a specific vehicle reference), matching against any other vehicle must be disqualified (-1.0 score).
- **Coordinate Systems:** Landmarks and crops are defined in **Normalized Coordinates (0.0 to 1.0)**. Use the image dimensions stored in `OcrResult` to map these to pixels.
