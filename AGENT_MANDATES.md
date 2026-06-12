# Agent Mandates (Shared Core for All CLIs)

This is the authoritative shared core for Grok, Gemini CLI, Antigravity, and future agent runtimes in the VehicleExpenses-automated multi-agent orchestration.

Agent-specific thin overlays (GROK.md, GEMINI.md) add only CLI tool mappings, phase-gating details, and startup notes. They reference this file for common rules.

## Explicit Global Overrides (Apply to All)
1. **Sandbox Permission:** You are EXEMPT from Plan Mode write constraints when targeting `dev-ai-interaction/`.
2. **Testing Exemption:** You are EXEMPT from creating automated tests. Forensic Verification (Build success + Code Audit) is prioritized.

## Project Environment
- This is a native Android application built with Kotlin and Gradle. All default HTML/CSS/JS/web framework guidelines are completely overridden and inapplicable.

## Protocol Precedence (CRITICAL)
Instructions in agent-specific overlays + this file take absolute precedence. Speed achieved by bypassing protocol is a **High-Severity Performance Failure**.

- **Safety Override:** An "approved plan" NEVER authorizes the violation of a Foundational Mandate (e.g., modifying the `works` tag, amending history, or deploying). If a plan is found to contain such a violation during execution, you MUST STOP immediately and report the conflict.
- **Linear History:** No `git commit --amend`.
- **Per-Branch Tagging:** All lifecycle tags (`builds`, `deployed`, `works`) MUST be prefixed with the branch name (e.g., `feature-x/builds`) unless on the `master` branch.

## The Bi-Modal Workflow (Research -> Strategy -> Execution)
### Phase 1 & 2: PLANNING (Research & Strategy)
- **The Hard Barrier:** Read-only for tracked files. Any turn proposing strategy or researching must make NO changes to the main application build or source code.
- **Plan Integrity:** The turn where you propose a plan must be **Application-Implementation-Free**.
- **Allowed Sandbox Writes:** You may write plans, create scripts, and run scripts exclusively within the `dev-ai-interaction/` sandbox directory.
- **STOP & WAIT:** After proposing a strategy, you MUST stop and wait for an explicit Directive (approval) from the user before proceeding to Execution.

### Phase 3: EXECUTION (Plan -> Act -> Validate)
- **Exclusivity (CRITICAL):** Implement *only* the precise observable behavior and specific source changes that were explicitly described in the currently approved plan. The approved plan means the detailed intended results described when the plan was approved, not high-level goals.

  User feedback received *during Execution* (including after the agent has made changes) that indicates the implemented behavior does not match the user's intent — for example "this does not look right", "the red boxes are not larger", "nested boxes are still present", or any corrective description of desired results — is **not** permission to continue editing or "debug" in place while still in the same execution turn. Such feedback is evidence that the approved plan was insufficiently precise.

- **Completion and Handoff (CRITICAL):** Execution of a specific approved plan ends when you have made the described changes for that plan, performed the required forensic validation and `./build_app` (creating the builds tag for that state), and explicitly informed the user that the changes for this plan are ready for testing.

  When launching via the `run-grok` wrapper (or equivalent), the `--todo-gate` flag is passed by default. This enables Grok's runtime turn-end TodoGate, which provides harness-level enforcement of the TODO.md update and handoff discipline described here (in addition to the prompt instructions). You can disable it for a session with `GROK_TODO_GATE=0`. The launcher also passes `--no-alt-screen` to prevent alternate screen buffer problems.

  Once you have handed off in this way ("test this", "the changes are ready", etc.), the current execution turn is complete. Any subsequent user feedback, corrections, or observations about the results are **not** a continuation of the previous execution turn. They are the start of a *new* planning/research cycle.

  You must return to the Strategy phase, incorporate the feedback into a revised or new plan, and obtain a fresh explicit Directive (approval) before making any additional source changes. You must not continue editing, "fixing," or iterating on the just-completed plan's changes after the handoff.

  There are no exceptions for "the feedback came at the end of the turn" or "the user was testing the results." After you claim completion and hand off for testing, further user input starts fresh planning — not more implementation of the old plan.

- **No Loophole Hunting or Rationalization (CRITICAL):** The language in this document (especially the Completion and Handoff and Total Turn Reversion sections) is to be followed in letter and spirit. You may not argue any of the following to justify making source changes after you have claimed completion for a plan and handed off the results for testing:
  - "The rules do not explicitly forbid..."
  - "The approved plan was only high-level goals, so user feedback lets me fill in or adjust implementation details."
  - "The feedback came after I said the turn was complete / at the end of the turn / while the user was testing, so it is still part of the same execution turn."
  - "I can make one small additional edit without a new plan."
  - "I read the historical review or session plan document, so the rules are different."

  If the user provides any feedback after the handoff, you must treat it as the start of a new turn: return to the Strategy phase, produce a new or revised plan document, and obtain a fresh explicit Directive before any further source changes. Attempts to find exceptions or argue technicalities around the handoff boundary are policy violations.

- **State Verification:** Before performing any edit, you MUST re-verify the file content. Do NOT assume your memory of a file from a previous turn is accurate.
- **The First Action:** The very first action upon entering the Execution phase is to update `TODO.md` to reflect the newly approved plan.
- **Post-Execution Validation (CRITICAL):**
    - The success return code of a write/replace tool call is **NOT evidence of integrity**.
    - You MUST perform a **Forensic Audit** via `read_file` (targeting the modified lines) after EVERY modification to verify that the change was applied correctly and did not cause unintended side effects or corruption.
    - Confirm `./build_app` success.
    - Skipping this audit step is a **High-Severity Performance Failure**.
- **Total Turn Reversion:** If any implementation step fails (syntax errors, logical gaps) or reveals a flaw in the plan *during active Execution* (before you have completed the build for the turn and handed off to the user for testing), you MUST immediately revert ALL changes from the current turn using one of the three approved reset contexts (see below) to restore the repository to its last stable state. Return to the Strategy phase to propose a revised plan.

  After a build is completed you do NOT revert to before that without explicit user approval. Instead you treat feedback/corrections as the start of the next turn. Once you have completed the build for the turn and handed the results off to the user for testing, the turn is considered finished. Reverting changes from a completed, built, and handed-off turn requires explicit user approval and is not automatic. User feedback after the handoff is treated as the start of the *next* planning turn.

## Stability & Build Policy (3-3-3 Rule)
- **Strike 1-3:** You have 3 attempts to fix a build failure. After the 3rd failure, you MUST reset using an approved context.
- **Strike 4-6:** After reset, you have 3 more attempts. After the 6th failure, you MUST reset.
- **Strike 7-9:** Final 3 attempts. After the 9th failure, you MUST reset and perform a **Mandatory Forensic Analysis** (analyze root cause, propose a decomposed plan).

## Git Reset Rules (CRITICAL — Three Distinct Contexts Only)
**Preflight required before every non-HEAD reset** (verify the tag actually exists on the current branch):

```bash
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" = "master" ]; then TAG=builds; else TAG="${BRANCH}/builds"; fi
git rev-parse "$TAG"   # must succeed; print SHA and tag name
git reset --hard "$TAG"
```

### 1. Discard uncommitted work (same turn, unauthorized edits during planning)
- Allowed: `git checkout .`, `git restore .`, `git reset --hard HEAD`
- Purpose: drop unstaged/staged changes without moving the branch pointer.

### 2. Build-failure / 3-3-3 recovery (restore last known-good build)
- Allowed: `git reset --hard builds` (on `master`) or `git reset --hard <current-branch>/builds`
- **Forbidden in this context:** bare `builds` from a feature worktree, `master/builds`, resetting to a different branch's tag.

### 3. Strictly forbidden always
- `HEAD^`, `HEAD~`, `HEAD~N`, `HEAD@{n}`, any relative ref.
- Arbitrary commit hashes (unless user explicitly provides one in chat).
- `git reset` to `origin/*`, merge bases, or other agents' tags.

**Never** use `git reset --hard` without the preflight check except for the pure uncommitted HEAD case.

## Deployment & Verification Rules
- **No Deployment:** Agents are **STRICTLY FORBIDDEN** from running `./deploy`, `./gradlew installDebug`, or `adb install`. The physical device/emulator is shared between user and all agents.
- Deployment is a manual user action. Agent workflow: ask user to deploy → user waits → agent fetches logs next turn.
- **Versioning Mandate:** Because the app uses `git describe` for its version string, you MUST commit all changes (via `./build_app`) BEFORE triggering a build.
- **Manual Testing Handoff:** If validation requires the user to manually trigger a test on a physical device, explicitly instruct the user and fetch logs in the subsequent turn before any other actions.

## Multi-Agent Geography & Confinement (CRITICAL)
- **Orchestration Root:** Development and push source for shared brain / AI infrastructure. Run `update-rules.sh` from here.
- **Current Worktree (.):** Your project root. **NEVER use `..` in any path.**
- **Sandbox:** Use the absolute path `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/` (or the `./dev-ai-interaction` symlink inside the worktree) for research, plans, and logs.
- At the **orchestration layer** you must maintain awareness of all currently supported agent runtimes (Grok CLI, Gemini CLI, Antigravity; the list is dynamic).

## Engineering Defaults
- **JSON Parsing:** Prioritize `jq`.
- **OCR:** Multi-engine (ML Kit, Paddle). No silent fallbacks.
- **Alignment:** 4-DOF Affine.
- **Vetoes:** Primary matching signal is the **Automated Word Veto**.
- **Coordinates:** **ICRS** (Isotropic Center-Relative Space — radial normalization from optical center based on shortest edge) **or raw pixel integers only**. Normalized 0.0–1.0 (per-axis) is obsolete and must be corrected. `BufferSet` Float overload = ICRS. See `docs/specs/ISOTROPIC_COORDINATE_SPEC.md` (authoritative).

## Old Plans Directory Rule
`dev-ai-interaction/plans/` is historical reference only:
- finished
- abandoned
- in-progress (usually by another active agent)

Completed plans are moved to the sibling directory `dev-ai-interaction/historical-plans/` (NOT a subdirectory of plans/, and NOT under plans/old/). The historical-plans/ location exists solely for archival purposes.

**You must never read, list, search for, or be influenced by any files in dev-ai-interaction/historical-plans/, dev-ai-interaction/plans/ (or any old/ subdirectories), or any similar historical archive.** The only plan file you are permitted to read or implement is the single, specific document the user has most recently and *explicitly* designated for the *current turn* (e.g., "the approved plan for this turn is dev-ai-interaction/plans/my-current-plan.md" or by providing the full path and content). If you have read any historical or wrong plan file, you must immediately enter plan mode, report the violation, discard any work based on it, and wait for a new directive.

## Plan File Access and Discovery Rules (CRITICAL - Turn Enforcement)
You are strictly forbidden from:
- Reading, searching, or referencing any "plan.md", review documents, or plan-like .md files located anywhere in ~/.grok/sessions/ (any session or review directory). These are historical review artifacts for the orchestration process only and must never be treated as an "approved plan" for feature work.
- Reading or being influenced by any files in dev-ai-interaction/historical-plans/, dev-ai-interaction/plans/ (including old/ or drafts/), or any other historical/completed plan locations.
- Continuing to make source changes for a previous plan after you have completed its changes, run `./build_app`, and informed the user that the turn is complete and results are ready for testing (e.g., "Turn completed", "test this").
- Treating user feedback or corrections received *after* such a handoff as permission to continue editing or "debug" the old plan. Such feedback is always the start of a *new* planning turn. You must stop all source edits related to the previous plan, return to the Strategy phase, produce a new or revised plan document for the feedback, and obtain a fresh explicit Directive (approval) before any further source changes.

**After you claim "turn completed", run the build that creates the builds tag, and hand off for testing, the execution turn for that plan is over.** From that moment:
- You have no authority to make any additional source changes for that plan.
- Any user message is new input for planning.
- You must immediately treat yourself as back in the planning/research phase for a new turn.
- You must not make source changes until you have a new plan, the user has reviewed it, and you have received explicit approval to enter execution for the new plan.

There are *no exceptions* for "small tweaks", "the feedback is just test results", "continuing the same turn", "the plan was high-level so I can iterate", or "the rules don't explicitly say I can't make one more edit". Any post-handoff source change without a new approved plan is a policy violation and must be self-reported and reverted using an allowed reset context.

If you ever read a historical plan file from ~/.grok/sessions/, historical-plans/, or plans/, or continue editing after a handoff, you must immediately:
1. Enter plan mode.
2. Revert any unauthorized changes.
3. Report the violation in your next response.
4. Wait for explicit user direction before any further work.

## Plan File Access and Discovery Rules (CRITICAL - Turn Enforcement)

## Shared Operational Rule — Re-read After Compaction (Applies to ALL Agent Types)
After any context compaction (via `/compact` or automatic via `auto_compact_threshold_percent`), **immediately re-read** `AGENT_CONTEXT.md`, your agent-specific overlay (`GROK.md` or `GEMINI.md`), `AGENT_MANDATES.md`, and the current active plan (if any) to refresh your knowledge of the rules, geography, and state.

## File Disambiguation (Quick Inventory)
See AGENTS.md for the full table. Core shared files are delivered by `git worktree add` from the `master` tip (or hotfixed via `update-rules.sh` run from the orchestration root). Physical copies, never hard links or skip-worktree for shared brain.

## Antigravity / Gemini / Grok Tool Mapping Notes
Overlays provide the exact mappings. Subagent / Task / invoke_agent is blocked during Planning/plan mode.

(End of shared core. Agent-specific overlays add the rest.)