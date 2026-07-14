# Agent Mandates (Shared Core for All CLIs)

This is the authoritative shared core for Grok, Gemini CLI, Antigravity, and future agent runtimes in the VehicleExpenses-automated multi-agent orchestration.

Agent-specific thin overlays (GROK.md, GEMINI.md) add only CLI tool mappings, phase-gating details, and startup notes. They reference this file for common rules.

## Explicit Global Overrides (Apply to All)
1. **Sandbox Permission:** You are EXEMPT from Plan Mode write constraints when targeting `dev-ai-interaction/`. This means you may freely create, edit, delete, and organize files inside the sandbox (including plans, analysis notes, temporary files, etc.) during planning. You may also edit `project-facts.md` (and contents of the sandbox).
2. **Testing Exemption:** You are EXEMPT from creating automated tests. Forensic Verification (Build success + Code Audit) is prioritized.

**Edit permissions model:**
- In plan mode: you have **zero authority** to edit, create, or modify any *tracked files outside the sandbox* (i.e. the real application source code that lives in git, such as files under `app/`, build scripts, main documentation, etc.). All intended changes to tracked files outside `dev-ai-interaction/` must be fully described in a formal sandbox plan. The user must explicitly approve that plan with the magic phrasing before any execution sub-agent may touch those files.
  - **Explicit exceptions for planners in plan mode**:
  - The tracked `TODO.md` at the worktree root **may** be updated only via `./todo-append` / `./todo-close` (future backlog; not current-turn progress).
  - The tracked `project-facts.md` at the worktree root **may** be edited (add or correct orientation / layout facts — see project-facts rules).
  These are the allowed tracked-file exceptions so the planner does not need separate sandbox copies for checklist or orientation facts.
- You **are allowed** to work freely inside the sandbox (`dev-ai-interaction/**`) and on `project-facts.md` (and legacy current-state.md). The `.grok/config.toml` rules + `plan-mode-hard-stops.js` hook enforce the boundary.
- When NOT in planning mode (after `exit_plan_mode` during an approved execution phase): edits to tracked files (and generally) are allowed via blanket `search_replace` / `write` allows in `.grok/config.toml`. This eliminates per-edit permission prompts during normal implementation work while preserving the strict planning barrier.
- Granular exceptions can be added in `.grok/config.toml` (e.g. for specific helpers or paths). Run `./update-rules.sh` after changes to propagate. Project rules take precedence over user `~/.grok/config.toml`.

**Granular permission rules:** The checked-in `.grok/config.toml` (under `[permission] rules`) lets you whitelist *specific* files or paths for `search_replace` / `write` (and other tools) without granting blanket edit access to everything. See the examples we added for `dev-ai-interaction/**`, `project-facts.md`, and `.agent-state/**`. Add narrow patterns here for any other files you want pre-approved. These rules are merged with your `~/.grok/config.toml` (project wins) and distributed to all worktrees via `update-rules.sh`. The `plan-mode-hard-stops.js` hook provides additional dynamic enforcement.

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

  Launchers default **TodoGate off** (`GROK_TODO_GATE=0`): TODO.md is **not** a per-turn checklist. Current-turn tracking is `ENGINEERING_LOG.md` via `./append-to-engineering-log`. Pass `GROK_TODO_GATE=1` only if you deliberately want TodoGate. Launchers pass `--no-alt-screen` to avoid alternate screen issues.

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
  1. Main agent receives the request. If in planning, it may immediately spawn a dedicated **Planning Sub-agent** (subagent_type="plan" or "explore", capability_mode="execute" followed by narrow planning prompt) with a *very strict* prompt:
     "You are the Planning Sub-agent. On every launch or after any gap, **first** read the local project-facts.md in the worktree root + the current designated plan file in dev-ai-interaction/plans/. Treat project-facts.md as your source for stable locations and layout. Your *only* responsibilities are research and producing or iteratively revising a high-quality plan document in dev-ai-interaction/plans/ following the standard structure. 

**Research tool power (do not self-restrict):** You may and should freely use the complete set of investigative commands: any form of git log (git log -S, git log --oneline -p, ranges, git show, git diff, git describe, etc.), adb logcat (including -d dumps and filters for device/runtime logs and reports), adb pull/shell for device inspection, cat/tail/find/jq/ls on logs, artifacts, sandbox contents, and build outputs. The project .grok/config and plan-mode-hard-stops hook whitelist these explicitly for planning. 'etc.' means the full useful shell for understanding history and device behavior — not just the minimal ls/git-status examples. Pure research questions are answered directly with tools.

Produce a high-signal, low-boilerplate document focused on the specific work of *this* turn. **Cite** `standard-plan-compliance-block.md` by path only (one line under Compliance); do **not** paste the block. Reference live mandates for background rules. Use section name **Phased Execution**. Each phase lists what changes, which files, and observable success only — do not repeat per-phase gate checklists. No Mandate Acknowledgment section in sandbox plans. See `dev-ai-interaction/research/plan-style-guide.md`. For recovery after end of inning: read `implementation-failure-logs/*-inning-end.md` first; include **Already completed (exclude)** subsection. The plan is the precise scope + steps contract for this turn. You have zero authority to make source changes outside the sandbox (except the explicit TODO.md exception noted above: planners may edit the tracked root TODO.md). You must **not** call exit_plan_mode or any other action that signals 'the plan is ready for approval'. As you interact with the user, update the plan file with revisions and keep project-facts.md to stable layout facts and key "where things live" locations only. Before editing, perform hygiene (full read). Do not put plan references, branch, tags, or execution details in it. Roll transient material to ENGINEERING_LOG or the plan. Output only the full path to the plan file you created/updated plus a short summary of what you changed based on the latest user feedback. Stop. When spawning, prefer capability_mode=\"execute\" for full shell access during research."

  2. The main agent receives the produced plan path, reads the plan document, and can present a summary to the user (or simply direct the user to review the file in dev-ai-interaction/plans/).

  3. The user gives feedback on the written plan document. The main agent relays this (or spawns another planning sub-agent iteration) until the user is satisfied.

  4. Only when the user gives an explicit magic approval phrase naming the exact plan file in dev-ai-interaction/plans/ does the main agent consider the plan approved.

  5. The main agent then spawns a dedicated **Execution Sub-agent** (narrow prompt) with the approved plan injected:
     "You are the Execution Sub-agent for this turn only. Implement *precisely and only* the changes described in the following approved plan: [full content or clear reference to the file]. Do not add extra features, 'improvements,' or cleanups. 

**Mandatory phased discipline with per-phase gates (non-negotiable):** Follow the approved plan's **Phased Execution** section. Each phase is a coherent unit of work. Per-phase gates (forensic read/grep, `git add`, successful `./build_app` before next phase) are in the STANDARD BLOCK and Baseball Rule — do not skip them.

For each phase: perform the edit for that phase only; run gates; record the branch-scoped builds tag on success. On strike/out, follow Baseball Rule (3 strikes = out; 3 outs = end of inning → write inning-end report before any replan). On partial reset, only the tag of the most recent successful phase (`./get-builds-tag.sh` preflight) may be used.

First action: `./append-to-engineering-log` for execution start. At the very end, after the final successful build + post-forensic verification, output the exact marker '**END OF EXECUTION TURN. Awaiting new directive or plan approval before any further source changes or investigation that leads to edits.**' followed by 'results ready to test (new tag: ...)' and then stop completely. Parent/main agent will review your changes for fidelity to the plan.

When reading project-facts.md: always read the *full* file (no offset/limit or tail). If large, report its size for separate work. When appending to ENGINEERING_LOG.md: *only append* a new dated entry at the end — never edit prior sections.

Do not write post-execution analysis claiming success. The master *always* spawns an independent Compliance Checker afterward. The checker's primary job is to determine whether the actual delivered code solves the problem and achieves the results described in the plan (intent / functional match). Process discipline is secondary. Only on PASS for the primary intent match does the master clean implementation-failure-logs entries for this plan. Reference this in your plans.

  6. The main agent (or a separate narrow reviewer sub-agent) performs a final diff/review of the changes against the approved plan document before considering the turn complete.

  This architecture keeps the "helpfulness" pressure contained in narrow, scoped sub-agents whose prompts explicitly forbid the bad behaviors. The main conversation thread can focus on coordination, review, and giving rich feedback on the written plan file without the agent feeling it must "progress" by calling exit_plan_mode or starting implementation.

  **Running the Master Orchestrator:** Use the dedicated launcher `./run-grok-master` (or `../run-grok-master` from inside a worktree). This script injects a comprehensive role prompt that makes the agent fully aware of its responsibilities as top-level coordinator, including how to launch dedicated planning agents, spawn and monitor implementation sub-agents, detect run-away behavior, intervene with proper resets (using get-builds-tag.sh), collect detailed failure logs into dev-ai-interaction/implementation-failure-logs/, and kick off recovery planning rounds that feed those logs to the planning agent.

**Dedicated Planning Agent Session (primary long-lived workflow):** Keep a long-lived `run-grok-planner` terminal. **New planning cycles are initiated in the planner process** (user ↔ planner), not from master. Master terminal is typically given `execute plan <path>` and reports results; it also does PR review/merge when asked.

Optional: master may still write `dev-ai-interaction/.planning-agent-prompt.txt` if the human asks — not the primary cycle trigger. The planner must solicit a real problem statement before large plans, cite STANDARD BLOCK by path only (no paste), and must not declare a plan complete without the user.

`run-grok-planner` explicitly forces the stronger available model for this environment (via `GROK_PLANNER_MODEL` env var or the script default). In the current grok.com setup the primary model is `grok-build`. We default the planner to `grok-build` because it is the more capable model here for long-horizon planning, correctly interpreting large existing codebases, and strictly obeying "stay in plan mode / only revise the plan document / no source changes" constraints. The main `run-grok` launcher is left on whatever the user has configured as their normal default.

  The main agent generates a *very narrow one-time prompt* for this dedicated Planning Agent (and refreshes it at the start of each new planning cycle):
  "You are a dedicated Planning Agent running in a long-lived terminal. You are in plan mode and must stay there.

**RESEARCH CAPABILITIES — FULL INVESTIGATION POWER:** Use the complete toolset for research. This explicitly includes full git history commands in any form (git log with -S/-p/ranges/etc., git show, git diff, git describe, ...), adb logcat (dumps, filters, all flags) + adb pull/shell for device logs and reports, cat/tail/find/jq on sandbox artifacts and logs, and any other shell needed to investigate code, history, or runtime behavior. The project whitelists these; do not wait for reminders.

Your *only* job is research and iteratively producing/revising the highest-quality plan document in dev-ai-interaction/plans/ using the standard structure (including the exact naming guidance for plan files). Produce a high-signal, low-boilerplate document focused on the specific work of *this* turn. **Cite** `standard-plan-compliance-block.md` by path only (do not paste). Reference the live mandates for background rules. Use **Phased Execution** section name; phases = what + success criteria only (see `dev-ai-interaction/research/plan-style-guide.md`). No Mandate Acknowledgment in plans.

  This process is intended to be long-lived across multiple planning cycles. When the master indicates that a new planning cycle has begun (by telling you to restart with an updated prompt file), you must restart this terminal/process with the new prompt to load the latest instructions and standard block. Until a restart is requested, after every `**END OF EXECUTION TURN**` marker or when the user says 'new planning cycle' (directly to the planner or via master), you must first perform the mandatory failure detection (scan `implementation-failure-logs/*` including `*-inning-end.md`, and `.planning-agent-prompt.txt` context; use tools to verify whether gaps are resolved; ask the user if unresolved). For recovery planning, read any inning-end report first and exclude completed phases. Then re-read project-facts.md (full read + hygiene prune first), start with a fresh high-signal plan for the new request (or recovery if directed), and follow all the rules in this prompt and the referenced mandates. If no unresolved failure, behave as a clean default new cycle (solicit the problem as if the master had just received a bare "New planning cycle"). The plan is the precise scope + steps contract for the current cycle. You have zero write access to any tracked source files outside the sandbox. You must never make source changes or run builds. You must **not** call exit_plan_mode on your own initiative. Talk directly with the user, incorporate their feedback into revisions of the plan file, and provide summaries of changes. Only when the user explicitly says a phrase like 'this plan is good, exit planning mode', 'the plan at dev-ai-interaction/plans/xxx-plan.md is approved', or the exact magic approval phrasing, then call exit_plan_mode (if appropriate for the harness) and stop. Until then, just revise the plan based on user input and output the path to the current plan file after each significant revision."

  The main agent should write this prompt to the standard location `dev-ai-interaction/.planning-agent-prompt.txt`.

  The user keeps a dedicated long-lived Planning Agent terminal (started once with `run-grok-planner`). When the master starts a new planning cycle it writes a fresh narrow prompt and tells the user to restart the planner terminal with the updated file (e.g. run `./run-grok-planner` again in that terminal, or use `exec ./run-grok-planner` to replace the process in place). This gives the user direct, unmediated conversation with the Planning Agent for research and plan revision while ensuring the planner is always running under the latest instructions.

  (The planner script can also be invoked with an explicit prompt file: `./run-grok-planner /path/to/prompt.txt`.)

  Once done, the user returns to the main orchestrator conversation and says something like "The plan at dev-ai-interaction/plans/xxx-plan.md is now approved. Execute it" or "spawn the execution sub-agent with that plan."

  This gives the user the direct interaction they want with the "planning sub-agent" (as a full separate agent launch with narrow scope) without bouncing every message through the master.

  Existing skills ("design", "execute-plan", "implement", "review", "check-work") are encouraged as higher-level realizations of this pattern. Subagent/Task/invoke remains blocked during plan mode per the existing rule.

  **Important clarification on exit_plan_mode and plan rejection:** exit_plan_mode is primarily for the main agent's internal harness state management and should be used sparingly. The real "plan is ready" signal is the user giving an explicit magic approval phrase that names the exact written plan file in dev-ai-interaction/plans/. 

If the user rejects an exit_plan_mode call, says "don't present yet", or simply continues giving feedback on the current draft, this is **not** "the user has decided to completely abandon this plan and start again." The agent must interpret it as: "continue the interactive planning/feedback phase; revise the plan document in dev-ai-interaction/plans/ based on this input. Do more research if needed. Do not call exit_plan_mode again until the user explicitly indicates the document is ready for presentation/approval."

Only treat a situation as full restart/abandonment if the user explicitly uses language such as "start over", "new plan from scratch", "completely abandon this plan", "new cycle", or similar. Agents must not project "abandonment" onto normal continued discussion or early exit rejections.

- **Post-Handoff Cycle Start Protocol and Low-Friction Injection (long-lived terminals):** After any handoff (build + "ready to test" + END marker), the prior execution turn is finished. In the primary long-lived two-terminal mode the human simply tells the master "New planning cycle" (plus optional one-liner context). The master's initial prompt is built to automatically read project-facts.md + the latest `standard-plan-compliance-block.md`, write a full ready-to-use narrow prompt to `dev-ai-interaction/.planning-agent-prompt.txt` (including the dedicated planner template, embedded guardrails, and cycle direction), give the user the exact restart instruction, and stop. The human then restarts the planner terminal (`./run-grok-planner` or `exec`) and talks directly to it. The planner must treat input after the restart as a brand new planning phase: re-read project-facts.md (full read + hygiene first), produce a fresh high-signal plan, etc.

  For users who prefer a completely clean slate the launcher always injects the full fresh-session instruction on relaunch. For users who remain in the same long chat after handoff the agent *must* (as part of completion) write the short gate text to `dev-ai-interaction/.post-handoff-gate.txt`. The user can then `cat` it and append feedback, or simply wait for the master to provide the next prompt and restart the planner terminal. The gate/prompt text is kept short (the detailed rules live in the re-read mandates + the tracked MULTI_AGENT_USER_INSTRUCTIONS.md). See also the "Sandbox Plan File..." subsection above for the mandatory fresh sandbox plan creation on every new cycle. A narrow compliance/reviewer subagent or "review"/"check-work" skill invocation at the very start of a planning turn (to answer "Is there a freshly approved sandbox plan designated for this exact request? GO or NO-GO?") is optional but recommended as an additional guard.

  The master (whose prompt is built with the full automation logic) is responsible for detecting the short "New planning cycle" trigger, reading the necessary files (project-facts.md + standard block), writing the complete narrow prompt for the dedicated planner, giving the user the exact one-line restart instruction, and then stopping so the human can immediately switch to the planner terminal.

- **State Verification:** Before performing any edit, you MUST re-verify the file content. Do NOT assume your memory of a file from a previous turn is accurate.
- **The First Action:** The very first action upon entering the Execution phase is to update `ENGINEERING_LOG.md` to record the execution start note. This MUST be performed via the `./append-to-engineering-log` wrapper.
- **Post-Execution Validation (CRITICAL):**
    - The success return code of a write/replace tool call is **NOT evidence of integrity**.
    - You MUST perform a **Forensic Audit** via `read_file` (targeting the modified lines) after EVERY modification to verify that the change was applied correctly and did not cause unintended side effects or corruption.
    - Confirm `./build_app` success.
    - Skipping this audit step is a **High-Severity Performance Failure**.
- **Total Turn Reversion:** If any implementation step fails (syntax errors, logical gaps) or reveals a flaw in the plan *during active Execution* (before you have completed the build for the turn and handed off to the user for testing), you MUST immediately revert ALL changes from the current turn using one of the three approved reset contexts (see below) to restore the repository to its last stable state. Return to the Strategy phase to propose a revised plan.

  After a build is completed you do NOT revert to before that without explicit user approval. Instead you treat feedback/corrections as the start of the next turn. Once you have completed the build for the turn and handed the results off to the user for testing, the turn is considered finished. Reverting changes from a completed, built, and handed-off turn requires explicit user approval and is not automatic. User feedback after the handoff is treated as the start of the *next* planning turn.

## Stability & Build Policy (Baseball Rule)

Think baseball at the plate: a **strike** is a failed attempt on the current chunk of work. **Three strikes = one out.** An **out** requires reset to last known-good build before trying again. **Three outs = end of inning** at the current plan granularity — execution stops, an **End of Inning Report** is written, and only then may the planner produce a revised plan.

### Strikes and outs (mapping from former 3-3-3 rule)
| Strikes (cumulative) | Event |
|----------------------|--------|
| 1, 2 | Strikes — retry on same chunk after diagnosis |
| **3** | **1st out** → reset, resume at current plan granularity |
| 4, 5 | Strikes |
| **6** | **2nd out** → reset |
| 7, 8 | Strikes |
| **9** | **3rd out** → **end of inning** → mandatory report + replan (no further edits on this plan) |

- **Strike:** Failed `./build_app`, edit that regresses the tree, or other recoverable failure on the current phase/chunk.
- **Out (3 strikes or egregious):** Reset using approved context (`./get-builds-tag.sh` preflight → `git reset --hard` to last successful **phase** tag, or branch `builds` tag per Git Reset Rules). Append strike/out summary to `ENGINEERING_LOG.md` via wrapper.
- **Egregious failure (immediate out, <3 strikes):** Catastrophic or policy-breaking edit — mass deletion, wrong worktree/branch, Foundational Mandate violation, corrupting unrelated files, whole-phase revert required. Counts as one out immediately; still document in the inning report trail.

### End of inning (3rd out) — mandatory before replan
When the **3rd out** occurs, the executor (or master on its behalf) **must not** blindly split phases. First write an **End of Inning Report** to:

`dev-ai-interaction/implementation-failure-logs/<date>-<short-plan-slug>-inning-end.md`

Use the template in `dev-ai-interaction/research/inning-end-report-template.md`. The planner reads this file as primary input for recovery. Only after the report exists may the planner author a **new** sandbox plan path. Recovery plans must reference the report and include an **Already completed (exclude)** subsection (phases delivered before the inning ended — do not replan them).

**Phasing default:** Plans start with the **fewest coherent phases** that remain independently verifiable (each phase: forensic read/grep + `git add` + successful `./build_app` before the next). Typical modest UI/feature work: **~3–8 phases**. Finer decomposition belongs in the **post–inning-end** recovery plan, informed by the report — not as the default first attempt.

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

**Shell cwd and whitelisted helpers (CRITICAL — avoid approval thrash):**
- **Once at startup** (and after handoff relaunch): confirm Role/Branch and `pwd`. Session cwd stays the worktree root for the rest of the turn.
- **Never** prefix blessed helpers with `cd … &&` or `cd …;`. Allow patterns match from the **start** of the command string — `cd /path && ./append-to-engineering-log` does **not** match `./append-to-engineering-log*` and forces a manual approval prompt even though the helper itself is whitelisted.
- Invoke `./append-to-engineering-log`, `./build_app`, `./get-builds-tag.sh`, etc. as literal `./helper` (prefer sessions started at worktree root). Use the Shell tool `working_directory` parameter if you must run elsewhere — do not embed `cd` in the command.
- Avoid variable indirection that breaks pattern match when a direct `./helper` form is allowed.
- Do not re-discover the worktree path on every command.

### 1. Discard uncommitted work (same turn, unauthorized edits during planning)
- Allowed: `git checkout .`, `git restore .`, `git reset --hard HEAD`
- Purpose: drop unstaged/staged changes without moving the branch pointer.

### 2. Build-failure / Baseball Rule recovery (restore last known-good build)
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
- **Versioning Mandate:** Because the app uses `git describe` for its version string, you MUST commit all changes (via `./build_app`) BEFORE triggering a build. Use a rich commit message for phased work: `./build_app @phase_summary.txt changed.kt ...` (the @file form supports multi-line summaries pulled from the plan or ENGINEERING_LOG). Single-line messages are only for trivial steps. Git log + the builds tag gives the raw deltas and state at each tag; the sandbox plan + ENGINEERING_LOG.md + project-facts.md give the "why", phased intent, and net observable result (avoid forcing full re-derivation from diffs on every restart). Use a rich commit message for phased work: `./build_app @phase_summary.txt changed.kt ...` (the @file form supports multi-line summaries pulled from the plan or ENGINEERING_LOG). Single-line messages are only for trivial steps. Git log + the builds tag gives the raw deltas and state at each tag; the sandbox plan + ENGINEERING_LOG.md + project-facts.md give the "why", phased intent, and net observable result without forcing full re-derivation from diffs on every restart. Use a rich commit message: pass a file (e.g. `./build_app @phase_summary.txt file1.kt ...`) containing the phase summary or plan excerpt for multi-line detail. Single-line `-m "..."` is only for trivial steps. Git log + the builds tag gives the raw change history since the prior tag; the sandbox plan + ENGINEERING_LOG.md + project-facts.md give the "why", phased intent, and net observable result (avoid re-deriving everything from diffs).
- **Manual Testing Handoff:** If validation requires the user to manually trigger a test on a physical device, explicitly instruct the user and fetch logs in the subsequent turn before any other actions.

## Multi-Agent Geography & Confinement (CRITICAL)
- **Orchestration Root:** Development and push source for shared brain / AI infrastructure. Run `update-rules.sh` from here.
- **Current Worktree (.):** Your project root. **NEVER use `..` in any path.**
- **Sandbox:** Use the absolute path `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/` (or the `./dev-ai-interaction` symlink inside the worktree) for research, plans, and logs.
- At the **orchestration layer** you must maintain awareness of all currently supported agent runtimes (Grok CLI, Gemini CLI, Antigravity; the list is dynamic).

## Deploying / copying into another worktree (CRITICAL — build_app gate)
`./build_app` **refuses to compile** when there are uncommitted modifications to **tracked** files (`CRITICAL ERROR: Uncommitted tracked files detected`). That is intentional for version integrity (`git describe`).

Therefore:

1. **If you copy, overwrite, or hot-patch tracked files into another worktree** (agent-N, `master/`, etc.) — including `cp` of `deploy`, `ve-env`, `build_app`, mandates, hooks, or any other tracked path — you **MUST commit those changes on that worktree's branch** before considering the deploy done.
2. **Prefer `./update-rules.sh`** from the orchestration root: it physically copies the FILES list and **commits** `chore: Synchronize agent rules and infrastructure` in each target worktree. That keeps agent trees buildable.
3. **Ad-hoc `cp` without commit is a defect.** It leaves the coder unable to `./build_app` until someone cleans the dirt. Do not "just copy" into agent-N for a quick fix unless you also `git add` + commit (or run `update-rules`) in that worktree.
4. **Untracked artifacts are fine without commit** when they are intentionally gitignored (e.g. `ve-refresh-shell`, `run-as-primary`, `project.config`, local `app/build/`). Only **tracked** dirt blocks `build_app`.
5. **setup_agent / install helpers** that rewrite tracked sources in a new or existing worktree must leave the worktree **clean for tracked files** (commit or restore) before handoff to an agent.
6. Before telling a human or coder "tree is ready," run `git status` in that worktree: no modified tracked files unless the agent is mid-approved execution.

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

In addition to the sandbox plan document, each worktree root contains `project-facts.md` (tracked). On every fresh launch or new cycle, **first** read the local `project-facts.md`. It is the **cold-start orientation map**: where things live so agents do not hunt or invent wrong procedures (e.g. experiment reports under a known latest-report path; crash investigation → adb, not `find *.log`).

Planners may edit `project-facts.md`. TODO changes only via `./todo-append` / `./todo-close`.

**project-facts.md Content and Hygiene Rules (CRITICAL)**
- **Orientation facts only** (stable locations, conventions, “if you need X look here”). Must remain valid after merge and for a new effort. Never: branch, builds tag, plan names, “working on this cycle”, compliance reports, execution status, or ephemeral experiment constants.
  - Good: mandate/sandbox/launcher paths, coordinate policy, absolute sandbox path, device log procedure pointers.
- **Candidate rule:** Anything the agent had to **discover** (path, procedure, “where does Y live?”) is a **candidate** to add here so the next agent does not rediscover it. Prefer short pointers over essays.
- **Update discipline:**
  - Add/correct orientation facts when discovery happened.
  - Progress / plan refs / narrative → plan file or ENGINEERING_LOG (`./append-to-engineering-log` only).
- **Mandatory full read + hygiene:**
  - On launch, new cycle, or before any edit: read the *entire* file (no offset/limit).
  - Prune non-stable items. Keep small and high-signal.
  - If large: report size; **merge process validates and prunes** aggressively (MASTER special-file protocol).
- Source of truth for *current work* remains the designated plan + ENGINEERING_LOG + git; project-facts only prevents rediscovery thrash.

**TODO.md Rules (future backlog only)**
- **Purpose:** Work we are **not** doing as the current turn’s active contract. Not a phase checklist.
- **Add** only via `./todo-append` (high-level; rare). One line for a multi-day/week master plan (link the plan path) is OK; progress lives **in the plan file**, not more TODO bullets per phase.
- **Close** only via `./todo-close`. Merges/PRs should name items to close; master closes them at merge.
- **Forbidden:** Ritual “update TODO at start of every phase”; bulk wipe; direct rewrite of the whole file; listing every step of a master plan.
- **Current-turn tracking:** `./append-to-engineering-log` only.

**ENGINEERING_LOG.md Rules (append-only activity log)**
- ENGINEERING_LOG.md is strictly **append-only**. Agents must *only* append new dated entries at the very end of the file.
- **MUST exclusively use the `./append-to-engineering-log` wrapper (never direct edits, >>, echo, search_replace, or any other write on the file itself). The wrapper is the only approved mechanism.** It enforces the `## YYYY-MM-DD - Title` header format and safe append semantics (with sudo fallback if needed).
- Never edit, delete, overwrite, or modify any existing dated sections, entries, or historical content.
- All past activity must remain untouched for accurate tailing and historical review.
- New entries go under a new `## [YYYY-MM-DD] - Title` header with bullet details of what was done, changes, results, etc.
- Use this for effort tracking, "I'm working on X", cycle narrative, detailed progress, sub-agent outcomes, etc.
- **Orchestrator/master responsibility**: The master/orchestrator MUST use the wrapper to record sub-agent outcomes, PR processing steps, cycle coordination, and any high-level progress (detailed execution transcripts stay in the sandbox plan or sub-agent logs).

## Sandbox Plan File as the Primary Approved Artifact for Feature / Implementation Work (CRITICAL)
A formal plan document is **required** before you make *any* changes to tracked files outside the sandbox (i.e. real application source code in the main app directories that lives in git and will be built/committed).

- You may freely create, edit, delete, and organize files **inside `dev-ai-interaction/`** (the sandbox) during planning. The sandbox is explicitly exempt from plan-mode restrictions. You can work there as much as you want (analysis notes, temporary files, reorganizing plans, etc.) without needing a separate "plan for the plan".
- You may also edit `project-facts.md` (tracked) and sandbox contents.
- You have **zero authority** to edit, create, or modify any tracked file outside `dev-ai-interaction/` (the main app source) while in planning mode. All such work must first be described in a high-signal formal plan in `dev-ai-interaction/plans/`. The user must then approve it with the exact magic phrasing before any execution sub-agent touches those files.

**Pure research is different:** If the cycle starts with a question about existing code ("how does X work?", "where is the logic for Y?", "explain the Z block"), treat it as research-only. Use tools to investigate and answer directly in the conversation. Update `project-facts.md` lightly if useful. Do **not** create a formal sandbox plan file unless the user later gives explicit direction that the work is heading toward changes to tracked files outside the sandbox. The "first concrete deliverable must be a plan document" rule applies only to implementation/feature work that touches tracked files outside the sandbox.

The filesystem mtime on the plan file is the authoritative timestamp. A date/time suffix in the filename (such as `-20260614-143022`) is optional and only useful for human sorting or when archiving into `historical-plans/`. When a timestamp is included in the name, use at least second-level granularity (`YYYYMMDD-HHMMSS`) so that multiple plans created on the same day remain easily distinguishable and sortable by filename. Day-only timestamps (e.g. `20260614`) add little value beyond what the filesystem already provides and should be avoided.

- The plan must use the standard structure: Context (why this change), Recommended Approach (chosen over alternatives), Critical Files (exact paths), Existing Functions/Utilities to reuse (with file paths), **Phased Execution** (what changes per phase + observable success — not repeated gate checklists), Verification (end-to-end criteria). Per-phase forensic + `./build_app` gates are in the STANDARD BLOCK and Baseball Rule.

- **Phased Execution granularity:** Decompose into the **fewest coherent phases** that remain independently verifiable (~3–8 typical for modest features). Each phase names what changes, which files, and how to observe success. Do **not** repeat forensic/git add/build ritual in every phase line — the STANDARD BLOCK covers gates once. Finer micro-steps belong only in a **recovery plan after end of inning**, using the End of Inning Report as input; recovery plans must include **Already completed (exclude)**.

- **Plan-writing bans:** No Mandate Acknowledgment section in sandbox plans. No `./build_app @file` syntax. No "ultra-micro" / "maximum granularity" language unless documenting post–inning-end recovery. See `dev-ai-interaction/research/plan-style-guide.md`.

Plan filenames should follow the naming guidance above (descriptive kebab-case primary; granular timestamp only when it adds real value over filesystem mtime).

**Important: Plans must be high-signal documents, not policy restatements.** The standard structure describes the *specific work of this turn*. Do not paste long sections of AGENT_MANDATES.md, new_grok_agent_prompt, or prior plans into Context or Phased Execution. Reference the live mandates for background rules.

**Mandatory: Cite `standard-plan-compliance-block.md` by path only** (e.g. one Compliance line). Do **not** paste the block into plans. Executors re-read the file at execution start.

The "approved plan" for feature work is always the content of the designated sandbox file the user explicitly named, never the harness session plan.md or any historical archive. project-facts.md (at the worktree root) supplies stable "where things live" facts so agents avoid repeated searches on startup.

## Logcat/Troubleshooting Policy (CRITICAL - Device Sharing & Performance)
When debugging or troubleshooting, agents are strictly forbidden from running multiple sequential `adb logcat` queries (grabbing slices of logs directly from the device/emulator). This ties up and blocks the testing devices from being used by other agents or users.
Instead, you MUST follow this protocol:
1. Fetch the complete logs to a local file in the sandbox directory in a single command, e.g.:
   `adb -s <device_id> logcat -d > /home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/device-logcat.log`
2. Perform all analysis, log filtering, searching, and grep exploration locally on the retrieved file rather than running subsequent adb logcat commands.
3. Be respectful of shared hardware and never block device debugging.

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