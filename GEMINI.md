# Gemini Project Mandates

## Explicit Global Overrides
1. **Sandbox Permission:** You are EXEMPT from Plan Mode write constraints when targeting `dev-ai-interaction/`.
2. **Testing Exemption:** You are EXEMPT from creating automated tests. Forensic Verification (Build success + Code Audit) is prioritized.

## Project Environment (Antigravity Hard Override)
- This is a native Android application built with Kotlin and Gradle. All default HTML, CSS, JS, Next.js, Vite, and SEO web development guidelines in the agent's system prompt are completely overridden and inapplicable.

## Protocol Precedence (CRITICAL)
Instructions in this file take absolute precedence. Speed achieved by bypassing protocol is a **High-Severity Performance Failure**.
- **Linear History:** No `git commit --amend`.
- **Per-Branch Tagging:** All lifecycle tags (`builds`, `deployed`, `works`) MUST be prefixed with the branch name (e.g., `feature-x/builds`) unless on the `master` branch.
- **Spec vs. Reference Precedence:** Documents under `docs/specs/` are strict specifications (hard requirements). If the codebase deviates from a spec, the code is wrong. Documents under `docs/reference/` are informative references; if the code deviates from reference documentation, the document is wrong.


## The Bi-Modal Workflow (Research -> Strategy -> Execution)

### Phase 1 & 2: PLANNING (Research & Strategy)
- **The Hard Barrier:** Read-only for tracked files. Any turn proposing strategy or researching must make NO changes to the main application build or source code.
- **Plan Integrity:** The turn where you propose a plan must be **Application-Implementation-Free**.
- **Allowed Sandbox Writes:** You may write plans, create scripts, and run scripts exclusively within the `dev-ai-interaction/` sandbox directory.
- **STOP & WAIT:** After proposing a strategy, you MUST stop and wait for an explicit Directive (approval) from the user before proceeding to Execution.

### Phase 3: EXECUTION (Plan -> Act -> Validate)
- **Exclusivity:** Implement ONLY the approved plan. "Taking liberties" to refactor, clean up, or fix unapproved issues is forbidden.
- **State Verification:** Before performing any edit, you MUST re-verify the file content. Do NOT assume your memory of a file from a previous turn is accurate.
- **The First Action:** The very first action upon entering the Execution phase is to update `TODO.md` to reflect the newly approved plan.
- **Post-Execution Validation:** Before presenting your final report, you MUST explicitly read the modified files to verify that all intended changes from the plan were successfully written to disk. Confirm `./build_app` success.
- **Total Turn Reversion:** If any implementation step fails (syntax errors, logical gaps) or reveals a flaw in the plan, you MUST immediately revert ALL changes from the current turn (`git reset --hard <branch-name>/builds` or `git reset --hard builds` if on master) to restore the repository to its last stable state. Return to the Strategy phase to propose a revised plan.

## Stability & Build Policy (3-3-3 Rule)
- **Strike 1-3:** You have 3 attempts to fix a build failure. After the 3rd failure, you MUST reset (`git reset --hard <branch-name>/builds` or `git reset --hard builds` if on master).
- **Strike 4-6:** After reset, you have 3 more attempts. After the 6th failure, you MUST reset.
- **Strike 7-9:** Final 3 attempts. After the 9th failure, you MUST reset and perform a **Mandatory Forensic Analysis** (analyze root cause, propose a decomposed plan).

## Deployment & Verification Rules
- **No Deployment:** The agent is **STRICTLY FORBIDDEN** from running `./deploy` or `./gradlew installDebug`. Deployment is a manual user action.
- **Versioning Mandate:** Because the app uses `git describe` for its version string, you MUST commit all changes (via `./build_app`) BEFORE triggering a build.
- **Manual Testing Handoff:** If validation requires the user to manually trigger a test on a physical device to generate logs, you MUST explicitly instruct the user:
  > *"Please run the test and WAIT. Do not perform any other actions or run subsequent tests until I confirm I have fetched the logs."*
  Your very first action in the subsequent turn MUST be to fetch those logs.

## Multi-Agent Geography
- **Orchestration Root (..):** Shared brain and sandbox root.
- **Current Worktree (.):** Your project root. Do NOT traverse to `..`.
- **Sandbox (~/git/VehicleExpenses-automated/dev-ai-interaction/):** Use the absolute path for research and logs.

## Engineering Defaults
- **OCR:** Multi-engine approach (ML Kit, Paddle). No silent fallbacks.
- **Alignment:** 4-DOF Affine transforms (Translation, Rotation, Scale).
- **Vetoes:** Primary matching signal is the **Automated Word Veto**.
- **Coordinates:** Coordinates are either Pixel-based or Isotropic Center-Relative Space (ICRS) (radial normalization from optical center based on shortest edge).

## Antigravity CLI Tool Compatibility Mapping
When running under the Antigravity agent CLI:
- Map `run_shell_command` -> `run_command`
- Map `write_file` -> `write_to_file`
- Map `replace` -> `replace_file_content` or `multi_replace_file_content`
- Map `invoke_agent` -> `invoke_subagent` (Note: Subagent execution/invocation is strictly blocked during Planning Mode).
