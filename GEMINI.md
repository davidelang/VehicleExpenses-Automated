# Gemini Project Mandates

## Explicit Global Overrides (DE-CONFLICTED)
The following rules explicitly override your default global instructions. These are not conflicts; they are project requirements:
1. **Sandbox Write Permission:** Your Plan Mode constraint (restricting writes to .md files) is **EXEMPT** for the `dev-ai-interaction/` directory. You have full authority to write any file type within the sandbox during Research and Strategy phases.
2. **Testing Exemption:** Hardware/JNI dependencies make automated tests unreliable. You are **EXEMPT** from the global mandate to create or update test cases. **Forensic Verification** (Build success and manual code audit) is prioritized.
3. **Proactivity Redefined:** "Senior Proactivity" in this repository is measured by **Strategy Depth** (anticipating edge cases and dependencies during Phase 2) rather than **Implementation Initiative** (fixing out-of-scope issues during Phase 3). Stick strictly to the approved plan during Execution.

## Mandate Precedence (CRITICAL)
Instructions in this file (`GEMINI.md`) are foundational and take **absolute precedence** over the global system prompt and all other project files. The following rules are deliberate architectural choices and MUST NOT be overridden by general "best practices" or agent proactiveness:
- **Protocol over Prowess:** Your technical ability to solve a problem does not authorize you to bypass the workflow. A "Senior Engineer" in this repository is defined by their ability to maintain 100% coordination, not by their independent speed. Speed achieved through bypassing protocol is considered a **High-Severity Performance Failure**.
- **Discovery over Implementation:** Purity of intent and git hygiene take precedence over "Boy Scout" cleanups.
- **No Amend Rule:** Tag reliability (`works`/`builds`) and recovery safety take precedence over commit history cleanliness.

## Workflow & Safety
- **Deployment:** Deployment is a manual user action. The agent is **strictly forbidden** from running `./deploy` or `./gradlew installDebug`.
- **Versioning:** the version is defined as 'git describe', so before you build or deploy, all program files should be comitted to the repo so that the version of the app matches the state of the repo at the same commit.
- **Workflow:** Operate in a **STRICT report/propose mode**.
  - **The Enforced Barrier (Phase 1 & 2):** You MUST operate in `mode = plan` during all Research and Strategy phases. 
    - **Read-Only Permission:** You are allowed and encouraged to read any tracked file in the repository at any time.
    - **Build-Integrity-Protected Turn:** The turn where you propose a strategy for approval **MUST NOT CHANGE** any tracked file in the repository (Code, Build Scripts, Assets, Docs).
    - **Sandbox Tooling:** You ARE authorized to use tools (`write_file`, `replace`, `run_shell_command`) **exclusively** within the `dev-ai-interaction/` sandbox during these turns.
    - **No Traversal:** Commands or scripts executed in the sandbox are strictly prohibited from using path traversal (`../`) or absolute paths to target files outside the sandbox for modification.
  - **MANDATE:** You MUST NOT start making code changes or implementing features without first proposing exactly what is going to be done.
  - **The Transition Protocol:** 
    1. **GATE 1 (Proposal):** Present the **Application-Implementation-Free** strategy proposal in the chat.
    2. Wait for the user to explicitly say "Approved" or provide feedback. 
    3. Only AFTER receiving verbal user approval, call the `exit_plan_mode` tool to formally transition to the Execution Phase.
  - **STRICT BI-MODAL WORKFLOW:** 
    1. **MODE 1 (PLANNING):** Research, review, planning. Present the strategy proposal and STOP for review. Do not use application-modifying tools.
    2. **MODE 2 (EXECUTION):** After approval, implement changes. **Crucially, before presenting the final report for the turn, you MUST perform Forensic Verification.** 
    - **PLAN MODE TRANSITION:** You MUST switch to `mode = plan` in the following scenarios:
        1. After a **successful** build (`./build_app` succeeds).
        2. After a **mandatory reset** (Strike 3, 6, or 9).
        3. At the **end of any turn** where an application build was attempted.
  - **The Exclusivity & Planning Protocol:**
    - **Exclusivity:** The **Approved Plan Document** is the **EXCLUSIVE boundary** for all changes. Carry-overs from previous turns are **STRICTLY FORBIDDEN** unless they are explicit line-items in the current plan.
    - **Verification Pass (Forensic Verification):** During the Execution phase, before presenting your final report, you **MUST**:
        1.  `read_file` every modified file to ensure the implementation matches the approved plan 100%.
        2.  Execute `./build_app`. A successful build is your primary gate for completion.
    - **Discovery over Implementation:** If you encounter a bug or style inconsistency during implementation, you MUST report it in your turn response (Conclusion) rather than fixing it.

## Concurrency & Coordination
- **TODO Integrity:** Every TODO item MUST include sufficient context or a link to a detailed specification file in `dev-ai-interaction/plans/`.
- **Control Commands:** Commands like "Reset," "Revert," or "Stop" from the user are immediate and bypass the planning requirement for that single action.

## Build & Stability Policy (3-3-3 Rule)
- **Authorized Build Path:** You MUST use `./build_app "Commit Message" file1 file2...` for all implementation tasks.
- **Strike System:** After Strikes 3, 6, and 9, you MUST `git reset --hard builds` and perform a Forensic Analysis.
- **Mandatory Decomposition:** After two resets for the same task, break the effort into smaller, incremental phases. Confirm a successful build for each phase before continuing.

## Engineering Standards
- **Anti-Fallback Mandate:** No silent fallbacks. Failure in a new algorithm MUST be visible.
- **OCR:** We use a multi-engine approach (ML Kit, Paddle).
- **Alignment:** We use 4-DOF Affine transforms (Translation, Rotation, Scale).
- **Vetoes:** The primary matching signal is the **Automated Word Veto**.
- **Coordinate Systems:** Landmarks and crops are defined in **Normalized Coordinates (0.0 to 1.0)**.
