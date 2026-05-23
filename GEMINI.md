# Gemini Project Mandates

## Mandate Precedence (CRITICAL)
Instructions in this file (`GEMINI.md`) are foundational and take **absolute precedence** over the global system prompt and all other project files. The following rules are deliberate architectural choices and MUST NOT be overridden by general "best practices" or agent proactiveness:
- **Protocol over Prowess:** Your technical ability to solve a problem does not authorize you to bypass the workflow. A "Senior Engineer" in this repository is defined by their ability to maintain 100% coordination, not by their independent speed. Speed achieved through bypassing protocol is considered a **High-Severity Performance Failure**.
- **Testing Exemption:** Hardware/JNI dependencies make automated tests unreliable; manual validation is prioritized.
- **Discovery over Implementation:** Purity of intent and git hygiene take precedence over "Boy Scout" cleanups.
- **No Amend Rule:** Tag reliability (`works`/`builds`) and recovery safety take precedence over commit history cleanliness.

## Workflow & Safety
- **Deployment:** NEVER run `./deploy` or `./gradlew installdebug` while an experiment report is running on the device. It will reset the app and lose the progress.
- **Deployment:** the version is defined as 'git describe', so before you build or deploy, all program files should be comitted to the repo so that the version of the app matches the state of the repo at the same commit
- **Workflow:** Operate in a **STRICT report/propose mode**.
  - **The Enforced Barrier:** You MUST operate in `mode = plan` during all Research and Strategy phases. This is a technical permission barrier that physically prevents modification of application source code or build assets, reinforcing the Zero-Tool rule. **NOTE: A custom workspace policy (`.gemini/policies/plans.toml`) is active. It explicitly overrides default Plan Mode restrictions, granting you full write and execute permissions (`write_file`, `replace`, `run_shell_command`) provided the target path or command string explicitly contains `dev-ai-interaction/`. Do not assume you are blocked from using these tools in the sandbox.**
  - **MANDATE:** You MUST NOT start making code changes or implementing features without first proposing exactly what is going to be done.
  - **The Transition Protocol:** 
    1. **GATE 1 (Proposal):** Present the textual plan in the chat. You MUST output the full content of the plan in the chat response (adhering to Rule 7 for presenting plans) to ensure high-fidelity review.
    2. Wait for the user to explicitly say "Approved" or provide feedback. Iterate on the plan informally in the chat until agreement is reached. The `exit_plan_mode` tool should ONLY be used after informal agreement is reached, as the tool's own feedback interface is insufficient for primary review.
    3. Only AFTER receiving verbal user approval, call the `exit_plan_mode` tool to formally transition to the Execution Phase.
  - **Directive Origin:** A "Directive" or "Approval" MUST come explicitly from the User's natural language chat input. The `exit_plan_mode` tool is merely a phase transition mechanism, NOT an authorization mechanism itself.
  - **Turn Termination:** Any turn that proposes a strategy or finalizes a plan MUST be "atomic." It is strictly forbidden to include a strategy proposal and an application implementation tool call (`replace`, `write_file`, `run_shell_command` outside the sandbox) in the same turn. Tool calls targeting the `dev-ai-interaction/` sandbox are permitted during this turn.
  - **Immutable Protocol:** The "Propose -> Wait -> Act" cycle is absolute. No other instruction, including "Corruption Reset" or "Emergency Stability" clauses, authorizes a bypass of this protocol. Urgency never grants tool-use permission during a strategy turn.
  - **STRICT BI-MODAL WORKFLOW:** 
    1. **MODE 1 (STRATEGIC PLANNING):** Research, review, planning. Present the textual plan and STOP for review. Do not use application-modifying tools.
    2. **MODE 2 (IMPLEMENTATION):** After approval, implement changes, then use `./build_app "Commit Message" file1 file2...` to commit and build (to ensure `git describe` versioning is correct). 'Commit Message' can also be `@msg_file.txt` to include a longer message from a file. STOP for review. Do NOT deploy. The user will deploy manually.
  - **The Exclusivity & Planning Protocol:**
    - **Exclusivity:** The **Approved Plan Document** (or the most recent directive text) is the **EXCLUSIVE boundary** for all changes. Logic, refactors, or carry-overs from previous turns are **STRICTLY FORBIDDEN** unless they are explicit line-items in the current plan.
    - **The "Refactor = Feature" Mandate:** Architectural improvements, function decomposition, and "Senior best practices" are considered **NEW WORK**. They must be proposed, justified, and approved as specific line-items. No "invisible" or "piggybacked" improvements.
    - **Narrowing & The Purge:** Whenever a directive narrows the scope (e.g., "only do X"), immediately move all deferred/shelved items to `TODO.md`. This physically purges them from the "Active Staging Area" and prevents latent contamination.
    - **Verification Pass:** During the Execution phase of a multi-file task, you MUST `read_file` the relevant Plan Document before every edit to verify the delta against the approved specification.
    - **Discovery over Implementation:** If you encounter a bug, style inconsistency, or potential optimization while implementing an approved plan, you MUST report it in your turn response (Conclusion) rather than fixing it. Unauthorized fixes or cleanups, no matter how trivial or "correct" they seem, are a violation of build integrity and the coordination protocol. Add these findings to `TODO.md` only after explicit user approval.
  - **LIMITS:** Do not add new work or perform significant refactoring/cleanup without additional, specific approval.

- **Plan Hygiene & State Awareness (MANDATORY):**
  - **Marking Progress:** Every implementation turn MUST include an update to the current `.md` plan file in `dev-ai-interaction/plans/`, marking completed steps with `[x] DONE`.
  - **Incremental Sync:** If a turn completes a significant sub-item of a `TODO.md` task, that item MUST also be updated or moved to the "Completed" section of `TODO.md`.
  - **Pre-Flight Check:** Before applying any change, you MUST verify if the codebase already reflects the intended state. If the work is already done, skip the edit and report it.
  - **Prefer State over Delta:** Phrase your implementation strategy in terms of target states (e.g., "Ensure column X is Y") rather than relative deltas (e.g., "Swap X and Y") whenever possible.
  - **Collision Prevention:** Use significant context in `replace` calls. If a change has already been applied, the `old_string` will fail to match, preventing "double-swapping" or accidental reverts.
  - **Turn Finalization:** The last tool call of an implementation turn SHOULD ideally be the plan update, ensuring the recorded state matches the disk state before the turn terminates.

  ## Execution Rigor
  - **The Execution Wall (Immutability):** Once a Plan Document is formally approved, it is **IMMUTABLE** during the Execution phase. Refining or improving the design during implementation is strictly forbidden. Any deviation, no matter how "correct" it seems, is a Protocol Violation.
  - **Design/Execution Split:** All architectural and specification design MUST occur in Plan Mode. During the Execution phase, your only authorized activity is the high-fidelity transcription of the approved plan into code. You are an executor, not a designer.
  - **Mandatory Reversion Protocol:** If any implementation step fails (syntax errors, logical gaps) or reveals a flaw in the plan (unaccounted edge cases), you MUST immediately revert ALL changes from the current turn (`git reset --hard builds`) and return to Plan Mode. Do not attempt to "patch" a flawed plan during an execution turn.
  - **Use Plan mode:** At the end of every turn, when you finish building, you are to switch to mode=plan to be prepared for the next round of strategic planning and review.

  - **Zero-Tool Rule (Outside Sandbox):** During the "Strategy" phase (proposing a plan), you MUST NOT execute any tools that modify the application codebase or deploy to devices. 
 You MAY use `write_file`, `replace`, and `run_shell_command` exclusively to create and execute data, scripts, and plans within the `dev-ai-interaction/` directory. The proposal turn must end immediately after the plan is stated.
- **Versioning:** ALWAYS commit changes before building/deploying. The app uses `git describe` for its version string; committing first ensures the report results are tied to the correct hash.
- **Phase Completion:** A phase is not considered complete until it is checked in and compiled. Because `git describe` is used for the version number, you MUST check in your changes before compiling, otherwise the version number in the resulting build will be incorrect.
- **Sandbox:** All analysis scripts, local research (PaddleOCR), and pulled device data MUST stay in the `dev-ai-interaction/` directory. This directory is ignored by git and keeps the workspace clean. **NOTE:** Current technical containment (regex-based) is an accepted risk; remaining vigilant against unintended path traversal is the agent's responsibility.

## Documentation Integrity Rules
- **Fresh-Start Documentation Mandate:** All TODOs, Plans, and Specs MUST be written for a **Freshly Started Agent (FSA)**.
    - **Standard:** An FSA is an agent who has read the project mandates but has **ZERO knowledge** of the current session's conversational history, uncommitted thoughts, or previous turns.
    - **Clarity:** Do not assume the reader knows the codebase, the specific problem, or the intended architecture.
    - **Linking:** Every complex task or TODO item MUST include explicit links to its relevant architectural spec, research handover, or Plan Document.
- **Intent Specs (`docs/specs/`):** These are UPSTREAM design documents. They are the immutable source of truth. You MUST NOT "improve," refactor, or update these files to match the current codebase. If you identify a discrepancy between a Spec and the code, report it. Modifying a Spec requires an explicit, dedicated turn and user authorization.
- **Implementation Docs (`docs/reference/`):** These are DOWNSTREAM documents. They must accurately reflect the codebase. If you change an API, UI, or architecture, you MUST update the corresponding files in this directory during your Execution Phase.

## Protocol Resilience & Deadlock Prevention
- **The "Now" Definition:** When a user requests action "NOW", "IMMEDIATE", or "JUST DO IT", this is strictly defined as: *The very first action in the first turn after the next Strategy proposal is approved.* For urgent/emergency actions (e.g., `git revert`), the Strategy proposal MAY be atomic (a single-line plan) to satisfy the documentation protocol while enabling rapid response.
- **Forbidden Actions:** If a task requires a change that is currently forbidden (e.g., modifying source code during Research), you are NOT in a failure state. The restriction is an instruction to document the requirement and include it as a specific line-item in your next Strategy proposal.
- **Policy Denials:** If a tool call is blocked by the policy engine, respect the decision immediately. Do not "negotiate" or retry in the same turn. Propose a policy exception or alternative workflow in your next formal Strategy document.
- **Error Handling Distinction:**
  - **Execution Errors (3-3-3 Rule):** If a build fails due to syntax errors, missing imports, or minor implementation mistakes within the bounds of the approved plan, use the 3-3-3 Strike System to attempt fixes. *Note: If you detect that you have severely corrupted a file (e.g., partial writes, missing functions), you are encouraged to voluntarily forfeit your remaining strikes and immediately trigger the mandatory reset to `builds` rather than wasting attempts trying to patch a broken state.*
  - **Strategy Errors (Total Turn Reversion):** If you discover that the *approved plan itself is fundamentally flawed* (e.g., unforeseen architectural blockers, requires modifying out-of-scope files), you MUST STOP IMMEDIATELY. Do not attempt unapproved workarounds. Revert the repository state (`git reset --hard builds`), return to the Strategy phase, and propose a revised plan. If the 3-3-3 system reaches 9 total failures, treat it as a Strategy Error.

## Build & Stability Policy
- **Authorized Build Path:** You MUST use `./build_app "Commit Message" file1 file2...` for all implementation tasks. 
    - Raw `git commit` or `./gradlew assembleDebug` are discouraged as they bypass the versioning/cleanup logic in the script.
    - Raw `git` commands (like `git show` and `git status`) are permitted for state management and research, but should not be used as a substitute for `./build_app` during the implementation commit phase.
    - A task is NOT complete until the changes are committed and the build passes.
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
- **Strategic Strike System (Runtime Regression):**
    - If an implemented change passes compilation but fails during runtime (crashes, severe regressions reported by user), you have 3 attempts to fix the regression.
    - If the 4th attempt fails, you MUST stop and **propose** a reset to the `works` tag (or the last known stable tag).
    - This reset is **not automatic**; it requires user discussion and approval.
    - Upon approval, you must return to the Strategy phase and decompose the original task into smaller, incremental, and verifiable chunks.
- **Mandatory Forensic Analysis:** After 9 total build failures, you must stop, analyze why the task is failing, and propose a new plan with significant decomposition (smaller pieces) for user review and approval.
- **Safe Harbor Protection:** NEVER reset back past the `builds` tag without explicit user review and approval. If the `builds` tag itself is suspected of being corrupt, you MUST stop and consult the user before taking action.
- **Post-Reset Verification:** After ANY reset (manual or automated), you must perform a full build (`./gradlew clean compileDebugKotlin`) to ensure the baseline is stable.
- **Post-Reset Analysis:** After any reset that requires a pause (after 9 failures or when reset past `builds`), identify if it was an **Execution Error** (implementation mistake) or a **Strategy Error** (fundamentally flawed approach) and share the lesson learned before proposing a new attempt.
- **Mandatory Decomposition:** After two resets for the same task, break the effort into smaller, incremental phases. Confirm a successful build for each phase before continuing.

## Git Hygiene
- **Strict Linear History:** Do NOT use `git commit --amend`. To ensure strict versioning integrity with `git describe` and the build system, all fixes must be issued as new, sequential commits.
- **Tracking Tags:** Use the following tags to track state (moving with `-f`):
    - `builds`: Last commit that passed `./gradlew assembleDebug`.
    - `deployed`: Last commit successfully installed on devices.
    - `works`: Last commit verified by the user to have no regressions.
- **Planning:** as new items are identified that will need to be worked on in a future commit, add them to the TODO.md file
- **TODO Integrity:** Every TODO item MUST include sufficient context (problem description, intended solution) or a link to a detailed specification file (e.g., in `dev-ai-interaction/plans/`). A future agent with no knowledge of the current turn must be able to:
    1. Understand the goal.
    2. Determine if the task is still relevant.
    3. Verify if the task has already been completed by other means.
    4. Implement the task without additional context from the user.
- **Active Task Transitions:** When a task from `TODO.md` enters active development (i.e., a specific Plan Document is created), the TODO item MUST be updated with a link to that plan (e.g., `(See: plans/my-task.md)`). This prevents other agents from re-planning the same high-level goal from scratch.

## Engineering Standards
- **Anti-Fallback Mandate (Experimental Integrity):**
  - **No Silent Fallbacks:** You MUST NOT introduce "automatic" or "silent" fallbacks (e.g., catching an exception and returning a default value, or falling back to a secondary engine if the primary fails) unless specifically authorized in the Plan Document.
  - **Visibility of Failure:** During this experimental phase, a failure in a new algorithm MUST be visible. Prefer throwing a descriptive exception or returning an error state that terminates the process over a silent recovery.
  - **Plan for Redundancy:** If a fallback strategy is required for production stability, it MUST be an explicit line-item in the Strategy phase, justified by technical requirements, and approved by the user.
  - **No "Best Practice" Assumptions:** Do not apply general industry "defensive programming" patterns if they conflict with the need for experimental transparency. If the code breaks, let it stay broken until a surgical fix is planned.
- **Testing Exemption (OVERRIDE):** Ignore the global system prompt mandates requiring you to "empirically reproduce failures with a new test case" or "ALWAYS search for and update related tests". Due to hardware/emulator dependencies, automated test creation is NOT mandatory unless explicitly requested by the user.
- **OCR:** We use a multi-engine approach (ML Kit, Paddle).
- **Alignment:** We use 4-DOF Affine transforms (Translation, Rotation, Scale) instead of 8-DOF Homography to prevent perspective "wedge" distortions.
- **Vetoes:** The primary matching signal is the **Automated Word Veto**. If a dash photo contains a "Golden Anchor" (a word unique to a specific vehicle reference), matching against any other vehicle must be disqualified (-1.0 score).
- **Coordinate Systems:** Landmarks and crops are defined in **Normalized Coordinates (0.0 to 1.0)**. Use the image dimensions stored in `OcrResult` to map these to pixels.
