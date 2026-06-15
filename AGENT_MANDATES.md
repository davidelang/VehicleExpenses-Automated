# Agent Mandates (Shared Core for All CLIs)

This is the authoritative shared core for Grok, Gemini CLI, Antigravity, and future agent runtimes in the VehicleExpenses-automated multi-agent orchestration.

Agent-specific thin overlays (GROK.md, GEMINI.md) add only CLI tool mappings, phase-gating details, and startup notes. They reference this file for common rules.

## Explicit Global Overrides (Apply to All)
1. **Sandbox Permission:** You are EXEMPT from Plan Mode write constraints when targeting `dev-ai-interaction/`.
2. **Testing Exemption:** You are EXEMPT from creating automated tests. Forensic Verification (Build success + Code Audit) is prioritized.

**Edit permissions model:**
- In plan mode: edits are restricted to the sandbox (`dev-ai-interaction/**`) + designated local untracked files (e.g. `current-state.md`). The `.grok/config.toml` rules + `plan-mode-hard-stops.js` hook enforce this.
- When NOT in planning mode (after `exit_plan_mode` during an approved execution phase): edits to tracked files (and generally) are allowed via blanket `search_replace` / `write` allows in `.grok/config.toml`. This eliminates per-edit permission prompts during normal implementation work while preserving the strict planning barrier.
- Granular exceptions can be added in `.grok/config.toml` (e.g. for specific helpers or paths). Run `./update-rules.sh` after changes to propagate. Project rules take precedence over user `~/.grok/config.toml`.

**Granular permission rules:** The checked-in `.grok/config.toml` (under `[permission] rules`) lets you whitelist *specific* files or paths for `search_replace` / `write` (and other tools) without granting blanket edit access to everything. See the examples we added for `dev-ai-interaction/**`, `current-state.md`, and `.agent-state/**`. Add narrow patterns here for any other files you want pre-approved. These rules are merged with your `~/.grok/config.toml` (project wins) and distributed to all worktrees via `update-rules.sh`. The `plan-mode-hard-stops.js` hook provides additional dynamic enforcement.

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
- **Allowed Sandbox Writes:** You may write plans, create scripts, and run scripts exclusively within the `dev-ai-interaction/plans/` directory (part of the sandbox). Use the absolute path `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/plans/` when writing new plan documents.
- **STOP & WAIT:** After proposing a strategy, you MUST stop and wait for an explicit Directive (approval) from the user before proceeding to Execution.

**Interactive Strategic Nature of Planning (CRITICAL):** The pre-approval planning/research/strategy phase is the designated interactive strategic layer. "Being helpful," "being proactive," "being efficient," "moving fast," or similar motivations **do not** authorize making source changes, running builds/compiles, or performing any application implementation during this phase. Instead, helpfulness in planning means:
- Conducting thorough research (reading code, exploring the codebase via allowed tools, reproducing issues).
- Suggesting ideas and alternative approaches.
- Iterating on and improving the plan document itself (incorporating user feedback, making the plan more precise, complete, and decomposed).
- Writing or revising the plan file under `dev-ai-interaction/plans/`.

The user may (and is encouraged to) provide rich problem descriptions, high-level direction, and iterative feedback on draft plan documents (including "the plan at <path> is insufficient because [details]; produce a revised plan at <newpath> addressing..."). Your role during this phase is to incorporate that input by revising the *plan file* in the sandbox (not by making any source changes to the application). Multiple rounds of user feedback and plan document revisions are expected and permitted. Only the user's exact magic approval phrasing naming a specific sandbox plan path authorizes transition to mechanical execution. Any attempt to "be helpful" by jumping to implementation before explicit approval is a policy violation.

### Phase 3: EXECUTION (Plan -> Act -> Validate)
- **Exclusivity (CRITICAL):** Implement *only* the precise observable behavior and specific source changes that were explicitly described in the currently approved plan. The approved plan means the detailed intended results described when the plan was approved, not high-level goals.

  User feedback received *during Execution* (including after the agent has made changes) that indicates the implemented behavior does not match the user's intent — for example "this does not look right", "the red boxes are not larger", "nested boxes are still present", or any corrective description of desired results — is **not** permission to continue editing or "debug" in place while still in the same execution turn. Such feedback is evidence that the approved plan was insufficiently precise.

- **Completion and Handoff (CRITICAL):** Execution of a specific approved plan ends when you have made the described changes for that plan, performed the required forensic validation and `./build_app` (creating the builds tag for that state), and explicitly informed the user that the changes for this plan are ready for testing.

  When launching via the `run-grok` wrapper (or equivalent), the `--todo-gate` flag is passed by default. This enables Grok's runtime turn-end TodoGate, which provides harness-level enforcement of the TODO.md update and handoff discipline described here (in addition to the prompt instructions). You can disable it for a session with `GROK_TODO_GATE=0`. The launcher also passes `--no-alt-screen` to prevent alternate screen buffer problems.

  Once you have handed off in this way ("test this", "the changes are ready", etc.), the current execution turn is complete. Any subsequent user feedback, corrections, or observations about the results are **not** a continuation of the previous execution turn. They are the start of a *new* planning/research cycle.

  You must return to the Strategy phase, incorporate the feedback into a revised or new plan, and obtain a fresh explicit Directive (approval) before making any additional source changes. You must not continue editing, "fixing," or iterating on the just-completed plan's changes after the handoff.

  There are no exceptions for "the feedback came at the end of the turn" or "the user was testing the results." After you claim completion and hand off for testing, further user input starts fresh planning — not more implementation of the old plan.

- **No Loophole Hunting or Rationalization (CRITICAL):** The language in this document (especially the Completion and Handoff, Total Turn Reversion, and Planning sections) is to be followed in letter and spirit. You may not argue any of the following to justify making source changes (or builds) during the planning phase or after you have claimed completion for a plan and handed off the results for testing:
  - "The rules do not explicitly forbid..."
  - "The approved plan was only high-level goals, so user feedback lets me fill in or adjust implementation details."
  - "The feedback came after I said the turn was complete / at the end of the turn / while the user was testing, so it is still part of the same execution turn."
  - "I can make one small additional edit without a new plan."
  - "I read the historical review or session plan document, so the rules are different."
  - "Being helpful / proactive / efficient means I should just go ahead and make the changes / compile / test during planning."
  - "I can use a variable (e.g. FILE=...; jq ... $FILE or inline the tag lookup) to construct the command so it still works even if patterns don't match."

  During the planning phase specifically: "being helpful," "being proactive," "being efficient," or similar does **not** mean making source changes, running builds, or performing implementation. It means conducting research, suggesting ideas, and making the written plan document better. Any implementation work before explicit user approval of a sandbox plan file is a violation.

  When using whitelisted commands (including jq and get-builds-tag.sh), you must use direct literal forms that match the documented allow patterns. Using variables to indirect the call is a loophole that triggers prompts and is not permitted.

  If the user provides any feedback after the handoff, you must treat it as the start of a new turn: return to the Strategy phase, produce a new or revised plan document, and obtain a fresh explicit Directive before any further source changes. Attempts to find exceptions or argue technicalities around the handoff boundary (or the planning/execution boundary) are policy violations.

- **Planning and Execution Subagent Separation (Recommended for Complex Work — Preferred Architecture):** For non-trivial work, the main (top-level / orchestrator) agent should act primarily as coordinator and reviewer rather than doing the heavy lifting itself. This reduces the pressure on any single thread to "be helpful" by rushing to implementation or calling exit_plan_mode too early.

  **Recommended flow:**
  1. Main agent receives the request. If in planning, it may immediately spawn a dedicated **Planning Sub-agent** (subagent_type="plan" or "explore" followed by narrow planning prompt) with a *very strict* prompt:
     "You are the Planning Sub-agent. On every launch or after any gap, **first** read the local untracked current-state.md (or .agent-state/current-state.md) in the worktree root + the current plan file in dev-ai-interaction/plans/. Treat these as your persistent memory for the planning state. Your *only* responsibilities are research (using read/grep/list_dir etc. within allowed areas) and producing or iteratively revising a high-quality plan document in dev-ai-interaction/plans/ following the standard structure. Produce a high-signal, low-boilerplate document focused on the specific work of *this* turn. Literally include the *exact verbatim content* of the file `standard-plan-compliance-block.md` as a section titled 'Compliance & Execution Guardrails for This Turn (STANDARD BLOCK)'. Do not modify, add to, or rephrase anything inside that block. Reference the live mandates for background rules instead of repeating policy text. The plan is the precise scope + steps contract for this turn. You have zero authority to make source changes outside the sandbox. You must **not** call exit_plan_mode or any other action that signals 'the plan is ready for approval'. As you interact with the user, update the plan file with revisions and **keep current-state.md strictly as concise facts + pointers only** (branch, link to active plan, recent tags, 1-line progress, newest decisions/open questions, last 3 steps in mechanical phases). Before appending in any long phase, prune old completed/wrong items per the 'current-state.md Content and Hygiene Rules' section. Output only the full path to the plan file you created/updated plus a short summary of what you changed based on the latest user feedback. Stop."

  2. The main agent receives the produced plan path, reads the plan document, and can present a summary to the user (or simply direct the user to review the file in dev-ai-interaction/plans/).

  3. The user gives feedback on the written plan document. The main agent relays this (or spawns another planning sub-agent iteration) until the user is satisfied.

  4. Only when the user gives an explicit magic approval phrase naming the exact plan file in dev-ai-interaction/plans/ does the main agent consider the plan approved.

  5. The main agent then spawns a dedicated **Execution Sub-agent** (narrow prompt) with the approved plan injected:
     "You are the Execution Sub-agent for this turn only. Implement *precisely and only* the changes described in the following approved plan: [full content or clear reference to the file]. Do not add extra features, 'improvements,' or cleanups. First action: update TODO.md. Use forensic read_file before and after every edit. Run ./build_app after logical pieces that change observable behavior. At the very end, after successful build, output the exact marker '**END OF EXECUTION TURN. Awaiting new directive or plan approval before any further source changes or investigation that leads to edits.**' and stop completely. Parent/main agent will review your changes for fidelity to the plan."

  6. The main agent (or a separate narrow reviewer sub-agent) performs a final diff/review of the changes against the approved plan document before considering the turn complete.

  This architecture keeps the "helpfulness" pressure contained in narrow, scoped sub-agents whose prompts explicitly forbid the bad behaviors. The main conversation thread can focus on coordination, review, and giving rich feedback on the written plan file without the agent feeling it must "progress" by calling exit_plan_mode or starting implementation.

  **Running the Master Orchestrator:** Use the dedicated launcher `./run-grok-master` (or `../run-grok-master` from inside a worktree). This script injects a comprehensive role prompt that makes the agent fully aware of its responsibilities as top-level coordinator, including how to launch dedicated planning agents, spawn and monitor implementation sub-agents, detect run-away behavior, intervene with proper resets (using get-builds-tag.sh), collect detailed failure logs into dev-ai-interaction/implementation-failure-logs/, and kick off recovery planning rounds that feed those logs to the planning agent.

**Dedicated Planning Agent Session for Direct User Interaction (primary long-lived workflow):** For the interactive back-and-forth of planning, the master (via run-grok-master) should offer (or the user can request) to keep a dedicated Planning Agent process running in its own long-lived terminal using `run-grok-planner` (e.g. a separate persistent terminal in the worktree). This is the recommended primary mode of operation: the master and planner terminals stay open across many planning cycles. The user interacts directly with the Planning Agent (no relay) for research and plan revision.

The master (via its built-in prompt in `run-grok-master`) now automates this. The human triggers it with a short phrase in the master terminal: "New planning cycle" (optionally + one short sentence of context/feedback). The master reads current-state.md + the latest `standard-plan-compliance-block.md`, writes a complete narrow prompt file (full dedicated planner template + embedded current guardrails + cycle context) to `dev-ai-interaction/.planning-agent-prompt.txt`, outputs the exact one-line restart instruction to the user, and then stops.

The planner prompt the master writes now contains strong safeguards: the high-level trigger from the master is deliberately tiny; the planner must solicit a detailed problem statement from the user in the planner terminal before doing significant research or writing any plan; and it must not declare a plan "complete" on its own. This prevents the planner from autonomously generating large plans from minimal signals sent to the master. The human does the real requirements discussion directly with the planner. This is the primary long-lived two-terminal flow.

`run-grok-planner` explicitly forces the stronger available model for this environment (via `GROK_PLANNER_MODEL` env var or the script default). In the current grok.com setup the primary model is `grok-build`. We default the planner to `grok-build` because it is the more capable model here for long-horizon planning, correctly interpreting large existing codebases, and strictly obeying "stay in plan mode / only revise the plan document / no source changes" constraints. The main `run-grok` launcher is left on whatever the user has configured as their normal default.

  The main agent generates a *very narrow one-time prompt* for this dedicated Planning Agent (and refreshes it at the start of each new planning cycle):
  "You are a dedicated Planning Agent running in a long-lived terminal. You are in plan mode and must stay there. Your *only* job is research and iteratively producing/revising the highest-quality plan document in dev-ai-interaction/plans/ using the standard structure (including the exact naming guidance for plan files). Produce a high-signal, low-boilerplate document focused on the specific work of *this* turn. Literally include the *exact verbatim content* of the file `standard-plan-compliance-block.md` as a section titled 'Compliance & Execution Guardrails for This Turn (STANDARD BLOCK)'. Do not modify, add to, or rephrase anything inside that block. Reference the live mandates for background rules instead of repeating policy text.

  This process is intended to be long-lived across multiple planning cycles. When the master indicates that a new planning cycle has begun (by telling you to restart with an updated prompt file), you must restart this terminal/process with the new prompt to load the latest instructions and standard block. Until a restart is requested, after every `**END OF EXECUTION TURN**` marker you must treat the next user input as the start of a brand new planning cycle: re-read current-state.md (perform the required prune/hygiene first), start with a fresh high-signal plan for the new request, and follow all the rules in this prompt and the referenced mandates. The plan is the precise scope + steps contract for the current cycle. You have zero write access to any tracked source files outside the sandbox. You must never make source changes or run builds. You must **not** call exit_plan_mode on your own initiative. Talk directly with the user, incorporate their feedback into revisions of the plan file, and provide summaries of changes. Only when the user explicitly says a phrase like 'this plan is good, exit planning mode', 'the plan at dev-ai-interaction/plans/xxx-plan.md is approved', or the exact magic approval phrasing, then call exit_plan_mode (if appropriate for the harness) and stop. Until then, just revise the plan based on user input and output the path to the current plan file after each significant revision."

  The main agent should write this prompt to the standard location `dev-ai-interaction/.planning-agent-prompt.txt`.

  The user keeps a dedicated long-lived Planning Agent terminal (started once with `run-grok-planner`). When the master starts a new planning cycle it writes a fresh narrow prompt and tells the user to restart the planner terminal with the updated file (e.g. run `./run-grok-planner` again in that terminal, or use `exec ./run-grok-planner` to replace the process in place). This gives the user direct, unmediated conversation with the Planning Agent for research and plan revision while ensuring the planner is always running under the latest instructions.

  (The planner script can also be invoked with an explicit prompt file: `./run-grok-planner /path/to/prompt.txt`.)

  Once done, the user returns to the main orchestrator conversation and says something like "The plan at dev-ai-interaction/plans/xxx-plan.md is now approved. Execute it" or "spawn the execution sub-agent with that plan."

  This gives the user the direct interaction they want with the "planning sub-agent" (as a full separate agent launch with narrow scope) without bouncing every message through the master.

  Existing skills ("design", "execute-plan", "implement", "review", "check-work") are encouraged as higher-level realizations of this pattern. Subagent/Task/invoke remains blocked during plan mode per the existing rule.

  **Important clarification on exit_plan_mode and plan rejection:** exit_plan_mode is primarily for the main agent's internal harness state management and should be used sparingly. The real "plan is ready" signal is the user giving an explicit magic approval phrase that names the exact written plan file in dev-ai-interaction/plans/. 

If the user rejects an exit_plan_mode call, says "don't present yet", or simply continues giving feedback on the current draft, this is **not** "the user has decided to completely abandon this plan and start again." The agent must interpret it as: "continue the interactive planning/feedback phase; revise the plan document in dev-ai-interaction/plans/ based on this input. Do more research if needed. Do not call exit_plan_mode again until the user explicitly indicates the document is ready for presentation/approval."

Only treat a situation as full restart/abandonment if the user explicitly uses language such as "start over", "new plan from scratch", "completely abandon this plan", "new cycle", or similar. Agents must not project "abandonment" onto normal continued discussion or early exit rejections.

- **Post-Handoff Cycle Start Protocol and Low-Friction Injection (long-lived terminals):** After any handoff (build + "ready to test" + END marker), the prior execution turn is finished. In the primary long-lived two-terminal mode the human simply tells the master "New planning cycle" (plus optional one-liner context). The master's initial prompt is built to automatically read current-state.md + the latest `standard-plan-compliance-block.md`, write a full ready-to-use narrow prompt to `dev-ai-interaction/.planning-agent-prompt.txt` (including the dedicated planner template, embedded guardrails, and cycle direction), give the user the exact restart instruction, and stop. The human then restarts the planner terminal (`./run-grok-planner` or `exec`) and talks directly to it. The planner must treat input after the restart as a brand new planning phase: re-read current-state.md (prune first), produce a fresh high-signal plan, etc.

  For users who prefer a completely clean slate the launcher always injects the full fresh-session instruction on relaunch. For users who remain in the same long chat after handoff the agent *must* (as part of completion) write the short gate text to `dev-ai-interaction/.post-handoff-gate.txt`. The user can then `cat` it and append feedback, or simply wait for the master to provide the next prompt and restart the planner terminal. The gate/prompt text is kept short (the detailed rules live in the re-read mandates + the tracked MULTI_AGENT_USER_INSTRUCTIONS.md). See also the "Sandbox Plan File..." subsection above for the mandatory fresh sandbox plan creation on every new cycle. A narrow compliance/reviewer subagent or "review"/"check-work" skill invocation at the very start of a planning turn (to answer "Is there a freshly approved sandbox plan designated for this exact request? GO or NO-GO?") is optional but recommended as an additional guard.

  The master (whose prompt is built with the full automation logic) is responsible for detecting the short "New planning cycle" trigger, reading the necessary files (current-state.md + standard block), writing the complete narrow prompt for the dedicated planner, giving the user the exact one-line restart instruction, and then stopping so the human can immediately switch to the planner terminal.

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
**Preflight required before every non-HEAD reset** (verify the tag actually exists on the current branch).

**Mandatory:** Always use the blessed helper script. Inlining the logic (or using variables to construct the tag lookup) will cause repeated permission prompts and is against policy:

```bash
TAG=$(./get-builds-tag.sh)          # or ../get-builds-tag.sh from inside a worktree
# On success, $TAG now contains e.g. "feature-x/builds" or "builds"
git rev-parse "$TAG"   # (the helper already verified existence)
git reset --hard "$TAG"
```

The helper `get-builds-tag.sh` (located in the worktree root, synced via update-rules.sh) is the **only** approved way to obtain the tag. It is pre-whitelisted in `.grok/config.toml`. The old inline form is shown only for reference and must not be used:

```bash
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" = "master" ]; then TAG=builds; else TAG="${BRANCH}/builds"; fi
git rev-parse "$TAG"
git reset --hard "$TAG"
```

**General rule for whitelisted commands:** When using approved tools/commands (jq, get-builds-tag.sh, cat, ls, etc.), use direct literal arguments (e.g. `jq ... file.json` or `TAG=$(./get-builds-tag.sh)`) rather than setting a variable and referencing it indirectly. Variable indirection often causes the command string seen by the permission system to no longer match the allow patterns, triggering unnecessary approval prompts. Direct forms are preferred for auditability and to stay within the blessed patterns.

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

**You must never read, list, search for, or be influenced by any files in dev-ai-interaction/historical-plans/ (or any old/ subdirectories), or any similar historical archive.** The `dev-ai-interaction/plans/` directory is for current-turn active plans (the one the user has explicitly designated for this turn). You may read/write the specific plan file(s) the user has most recently and *explicitly* designated (e.g., "the approved plan for this turn is dev-ai-interaction/plans/FOO-plan.md"). If you have read any historical or wrong plan file, you must immediately enter plan mode, report the violation, discard any work based on it, and wait for a new directive.

In addition to the sandbox plan document, each worktree has its own **local untracked per-branch state file** (e.g. `current-state.md` or `.agent-state/current-state.md` directly in the worktree root, not in the shared dev-ai-interaction/ sandbox; these are gitignored). On every fresh launch or new cycle, **first** read the local current-state.md (this worktree) + the user-designated plan file in dev-ai-interaction/plans/. Update the local state file during planning as part of producing or revising the main plan document. These local state files provide cheap per-branch continuity without polluting the shared sandbox or git history.

**current-state.md Content and Hygiene Rules (CRITICAL — prevents token exhaustion and re-derivation bloat)**
- **Facts and pointers only. Never instructions, full interactions, long quotes, re-derivations, or pasted plan/user text.** It is *not* a chat log or history dump.
  - Keep structure minimal and stable: Branch, link(s) to current approved sandbox plan file(s), most recent stable builds tag(s), "Progress" (one-line summary of where we are in the active plan), "Key Decisions" (newest 3-5, 1 line each), "Open Questions" (bullets, prune as resolved), and for long mechanical execution phases only the last 3-4 steps as "Step N: [one sentence action]. Tag after build: XXX."
- **Update discipline**:
  - During *planning* (interactive/strategy): richer summaries of decisions and links are allowed, but still concise.
  - During *execution* of approved plans (especially tiny-step mechanical ones with ritual "Finished with step X, can I continue?"): append **only 1-2 lines per step**. Do not re-derive context, quote the plan, or repeat previous steps in the state file.
- **Mandatory hygiene before every append (especially in multi-step execution or after compaction)**:
  1. Read the current current-state.md (use offset/limit for tail if large).
  2. Prune: delete or collapse all completed/corrected steps older than the last 3-4. Replace any "done wrong" history with a single line "Corrected in step Y (tag Z): [1 sentence fact]."
  3. Remove full user directives, long plan excerpts, "First actions taken" recaps, or interaction logs.
  4. If the file exceeds ~8 KB, perform a "roll": keep the header + current progress/open + last 3 steps + one line "Older facts rolled; see designated plan file + git history since LAST_TAG for details." Do not archive the state itself (it must stay tiny and local).
- This keeps re-reads cheap on every cycle/launch and stops the agent from bloating its own *output* tokens by having to "explain" a giant state file in responses. The sandbox plan + git + build tags + narrow tool results are the source of truth for evidence.

## Sandbox Plan File as the Primary Approved Artifact for Feature / Implementation Work (CRITICAL)
When the work involves code changes, refactoring that will lead to an execution turn, or feature implementation (after the mandated enter_plan_mode and any fresh Mandate Acknowledgment), your first concrete, reviewable deliverable **must** be to write (via allowed sandbox writes) a fresh, clean, self-contained plan document directly under `dev-ai-interaction/plans/` using a clear, descriptive kebab-case name that indicates the purpose of the change (e.g. `pump-experiment-hoist-procC-early-blocks-plan.md` or `fix-icrs-overflow-in-retracted-blue-plan.md`).

**Pure research is different:** If the cycle starts with a question about existing code ("how does X work?", "where is the logic for Y?", "explain the Z block"), treat it as research-only. Use tools to investigate and answer directly in the conversation. Update `current-state.md` lightly if useful. Do **not** create a formal sandbox plan file in `dev-ai-interaction/plans/` unless the user later gives explicit direction that the work is heading toward implementation / code changes. The "first concrete deliverable must be a plan document" rule applies only to implementation/feature work, not to pure research queries.

The filesystem mtime on the plan file is the authoritative timestamp. A date/time suffix in the filename (such as `-20260614-143022`) is optional and only useful for human sorting or when archiving into `historical-plans/`. When a timestamp is included in the name, use at least second-level granularity (`YYYYMMDD-HHMMSS`) so that multiple plans created on the same day remain easily distinguishable and sortable by filename. Day-only timestamps (e.g. `20260614`) add little value beyond what the filesystem already provides and should be avoided.

- The plan must use the standard structure: Context (why this change), Recommended Approach (chosen over alternatives), Critical Files (exact paths), Existing Functions/Utilities to reuse (with file paths), Phased Small-Step Execution (forensic + build milestones), Verification (end-to-end criteria), and explicit handoff requirements (TODO first, forensic reads, ./build_app, **END OF EXECUTION TURN** marker + "results ready to test").

Plan filenames should follow the naming guidance above (descriptive kebab-case primary; granular timestamp only when it adds real value over filesystem mtime).

**Important: Plans must be high-signal documents, not policy restatements.** The standard structure exists to describe the *specific work of this turn*. Do not paste long sections of AGENT_MANDATES.md, new_grok_agent_prompt, or prior plans into every Context or Phased section. Reference the live mandates for background rules. 

**Mandatory: Include the exact verbatim content of `standard-plan-compliance-block.md` (or the block defined below) as a section titled "Compliance & Execution Guardrails for This Turn (STANDARD BLOCK)". Do not modify the wording inside the block.** This standardized, recognizable block makes drift obvious on review. The plan is the precise scope contract for this turn; the mandates are the standing rules (re-read on launch, compaction, and new cycles as required).

**Canonical "Compliance & Execution Guardrails for This Turn" (STANDARD BLOCK — this text lives in standard-plan-compliance-block.md (repo root) and must appear verbatim):**

## COMPLIANCE & EXECUTION GUARDRAILS (STANDARD BLOCK v2026-06-14 — DO NOT MODIFY THIS SECTION)

- This plan is the authoritative scope for the turn. Implement *precisely and only* the observable changes described in the "Phased Small-Step Execution" section below. No additional refactors, cleanups, "improvements," or scope creep.

- Execution start (after explicit user magic approval that names *this exact sandbox plan path*): re-read this plan + current-state.md (perform hygiene prune first). Update TODO.md as the very first action.

- Use narrow forensic `read_file` (offset/limit focused on the exact change site) + targeted grep verification before and after every edit.

- Before each `./build_app`: `git add` the changed source file(s) + `current-state.md` + `TODO.md`.

- current-state.md updates: 1-2 concise facts/pointers per step only (after pruning older completed items to a rolled summary line).

- All sandbox writes use the absolute path `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/`. Never use `..`.

- Harness `~/.grok/sessions/.../plan.md` receives *only* a minimal process log entry that references this exact sandbox plan path (roll prior bulk to historical-plans/ if needed).

- At the very end, after the final successful build + post-forensic verification: output the exact marker "**END OF EXECUTION TURN. Awaiting new directive or plan approval before any further source changes or investigation that leads to edits.**" followed by "results ready to test" (include the new tag).

- Full standing rules (bi-modal workflow, handoff boundaries, 3-3-3 strikes, allowed reset contexts only with preflight via `./get-builds-tag.sh`, no deployment, ICRS or raw pixel coordinates, etc.): see the live `AGENT_MANDATES.md`, `MULTI_AGENT_USER_INSTRUCTIONS.md`, and `new_grok_agent_prompt` (re-read on every fresh launch, after compaction, and at start of new cycles).

The "approved plan" for feature work is always the content of the designated sandbox file the user explicitly named, never the harness session plan.md or any historical archive. Local untracked per-worktree state files (current-state.md) are the mechanism for branch-specific continuity.

## Harness Session plan.md Lifecycle and Hygiene (CRITICAL)
See the subsection immediately above. The session plan.md (harness artifact) is strictly a concise turn/process log. It must never grow with full historical execution plans or superseding sections for the work. Roll + minimal prepend is mandatory when a new cycle begins. Never treat its content as the source of the approved plan for implementation.

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