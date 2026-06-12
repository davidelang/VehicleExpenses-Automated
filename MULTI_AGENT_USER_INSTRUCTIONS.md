# MULTI_AGENT_USER_INSTRUCTIONS.md

**This is the authoritative, tracked, user-facing (human) document for the exact rituals, magic words, and forbidden phrases when interacting with agents (Grok, and by extension the other runtimes) in the VehicleExpenses-automated multi-agent orchestration.**

It lives at the orchestration root and is physically synced (via `update-rules.sh`) into `master/` and every `agent-N/` worktree so it is always present and versioned alongside AGENTS.md / AGENT_MANDATES.md.

Read this (and the referenced sections in AGENT_MANDATES.md and AGENTS.md) before giving any directive after a handoff or when starting new work with an agent. The goal is reliable plan/execute cycle boundaries: the agent produces a visible plan file in the sandbox that you review and explicitly approve by path; after handoff the turn is over and the next input starts a brand-new planning cycle.

## 1. After Any Handoff ("results ready to test", END OF EXECUTION TURN marker, or `./build_app` success that the agent announces)
The prior execution turn is **finished** (per AGENT_MANDATES "Completion and Handoff (CRITICAL)" and "Plan File Access..." rules). Any feedback you give is the start of a *new* planning/research cycle. The agent must treat it as such.

**Recommended (cleanest, gives forced clean slate every time):**
- Exit the current agent CLI session completely.
- Relaunch with the normal command, e.g.:
  ```
  cd <agent-dir or feature-name.wt>
  ../run-grok
  ```
  (or the equivalent for run-gemini / run-antigravity).
- Every launch via `run-grok` (etc.) injects the full fresh-session instruction. The agent will produce the Mandate Acknowledgment report, enter plan mode via the tool, and STOP. You then give your new request as the "specific directive".

This eliminates context bloat, accumulation in the harness `~/.grok/sessions/.../plan.md`, and "asking to approve things already done".

**If you prefer to stay in the same long chat session (fallback):**
- The agent is *required* (as part of its handoff completion steps) to write the current short gate text to `dev-ai-interaction/.post-handoff-gate.txt`.
- Use this trivial ritual (one-liner in your shell or copy-paste):
  ```
  cat dev-ai-interaction/.post-handoff-gate.txt
  ```
  Then immediately append (or type after) your actual new feedback or request.
- The gate file text is intentionally short. All the detailed rules, magic words, and "never say" lists are in this document + the re-read AGENT_MANDATES.md.

The agent must also have created (or the user explicitly designated) a fresh sandbox plan file for the *prior* cycle; your new input will cause it to create a *new* one under `dev-ai-interaction/plans/` for this cycle.

## 2. Magic Words / Required Phrases the User *Must* Use for Approval (these count as the "explicit Directive")
When you are ready to let the agent proceed from planning to execution, your approval message **must** reference the exact sandbox plan file path and use one of the following (or very close unambiguous equivalents). The agent is instructed to treat only these as authorizing source changes for that plan.

- `approved the plan at dev-ai-interaction/plans/<exact-filename>.md for the following request: [paste or clearly describe the work]`
- `The user approved the plan at dev-ai-interaction/plans/xxx-plan.md. Proceed with execution of exactly the steps described in that plan only.`
- `approved, use the plan at dev-ai-interaction/plans/my-task-20260612-plan.md for: [summary]. First action update TODO.md then follow the phased steps with forensic reads and build at milestones.`

After you send one of the above, the agent may exit plan mode (if still in it), update TODO.md (first execution action), re-read the designated plan file, and begin the small decomposed steps.

**Never rely on "looks good", "go ahead", "sounds right", "implement the changes", or similar vague language alone.** The path + "approved the plan at ..." is what the rules require for an unambiguous handoff from planning to execution.

## 3. Phrases the User Must *Never* Say (or Types of Feedback to Avoid) After a Handoff or When Starting a New Request
These have repeatedly been misinterpreted by agents as permission to continue editing the just-handoff'd plan, treat the message as continuation of the prior execution turn, skip creating a fresh sandbox plan file, or bypass the enter_plan_mode + exit + explicit path approval gate.

**Never say these (or close variants) in a message that follows an END OF EXECUTION TURN / "results ready to test" / handoff announcement without first giving a fresh approved plan path using the magic phrasing above:**

- "do you have any questions about this task"
- "can you also..." / "also..." / "and while you're at it..."
- "just fix the..." / "just make the..." / "can you make the..."
- Any specific result description of the prior turn's output used as if it is still the same turn, e.g. "the red boxes are not larger", "the html for set C is showing broken links", "the binarization of set C does not appear to be happening", "nested boxes are still present", "the deskew rotation of set C seems to be rotating the wrong direction"
- "looks good but..." / "almost there but..."
- "continue with..." / "keep going on..."
- "what about X" or "try Y" (as a direct follow-up without "approved the plan at <new path>")
- "the feedback came at the end of the turn / while I was testing so it is still part of the same execution turn"
- Any phrasing that could be read as "the previous plan was only high-level so you can fill in details now" or "small additional edit is fine"

If you want to give test observations or corrections on a just-handoff'd turn, first say the magic approval for a *new or revised* plan that incorporates the feedback (or say "relaunch and read the new request below" and give the feedback as a fresh directive). The agent is instructed that post-handoff feedback = start of new planning cycle; it must produce (or be given) a new designated sandbox plan before any further source edits.

See AGENT_MANDATES.md "No Loophole Hunting or Rationalization (CRITICAL)" and "Execution handoff rule" for the exact policy the agent must follow (and self-report violations of).

## 4. Quick Checklist for Every New Request or Post-Handoff Message
1. If this follows a handoff/END marker: prefer relaunch (`../run-grok`). Fallback: `cat dev-ai-interaction/.post-handoff-gate.txt` then your text.
2. In planning the agent must create a *new* plan file under dev-ai-interaction/plans/ (not supersede or continue an old one in the harness session plan.md). Read (and help maintain) the local untracked `current-state.md` in the worktree root for per-branch continuity.
3. When ready to execute: use one of the magic approval phrases in section 2 that names the *exact* sandbox plan path.
4. After the agent says results are ready + END marker + has run `./build_app`: the turn for that plan is over. Treat your next input as a new cycle (relaunch or short gate ritual).
5. Never read or let the agent rely on historical plans/ or the harness session plan.md as the approved contract for work.
6. If the agent appears to be continuing edits without a new approved path, remind it of the END marker / this document and the handoff rules; it must self-report, revert if needed, enter plan mode, and wait for a fresh directive + sandbox plan.

## 4.5 Interactive Strategic Planning and Low-Cost Continuity (using local per-worktree state)
The pre-approval planning phase is the interactive strategic layer. You are encouraged to give rich problem descriptions, high-level direction, and detailed iterative feedback on draft plan documents (e.g. "The plan at dev-ai-interaction/plans/xxx-plan.md correctly identifies the issue but under-specifies Y. Revise and write the updated plan to dev-ai-interaction/plans/xxx-v2-plan.md addressing: [your details].").

To keep re-familiarization cheap across relaunches or cycles:
- Each worktree maintains a local untracked `current-state.md` (or `.agent-state/current-state.md`) directly in the worktree root (gitignored, per-branch by nature).
- On every new message after handoff (or fresh launch), include language such as: "Continue strategic planning for the work described in current-state.md (this worktree) and the previous plan at dev-ai-interaction/plans/xxx-plan.md. New feedback: [details]. Produce a revised plan at dev-ai-interaction/plans/yyy-plan.md."
- The agent is required to read the local current-state.md first (for branch-specific recent context, decisions, and links) + the designated sandbox plan file (in dev-ai-interaction/plans/), then update the local state file while revising the plan document.

This gives you low-cost interactive guidance without paying full re-derivation cost on every turn, while the safety rules (written sandbox plan + explicit magic approval) remain in force. The local state file travels with the branch/worktree and is never treated as a substitute for the approved plan document.

## 5. References (Always Re-Read These on Fresh Launch / After Compaction / Post-Handoff)
- `AGENT_MANDATES.md` (the authoritative shared core, especially Bi-Modal Workflow, Completion and Handoff, Plan File Access and Discovery Rules, Sandbox Plan File as Primary Artifact, Harness Session plan.md Lifecycle and Hygiene, Planning and Execution Subagent Separation, Post-Handoff Cycle Start Protocol).
- `AGENTS.md` (bootstrap, Plans Directory Rule, geography).
- `new_grok_agent_prompt` (the report the agent must give on fresh launch or after compaction; it now includes explicit confirmations about the sandbox plan file, harness log hygiene, and post-handoff behavior).
- `MULTI_AGENT_USER_INSTRUCTIONS.md` (this file — the human rituals).
- The specific sandbox plan file the user has most recently and explicitly designated for the *current turn* only.
- Existing example plans in dev-ai-interaction/plans/ (for structure): `buffer_set_lock_removal_plan.md`, `jpg_removal_plan.md`, etc.

## 6. Notes for Agent Implementers / This Document's Maintenance
This file is maintained in the orchestration root (like the other core instruction files) and distributed by `update-rules.sh`. When the protocol or rituals evolve, update this file + the corresponding sections in AGENT_MANDATES.md / AGENTS.md / new_grok_agent_prompt, then run the sync from the orchestration root on the orchestration branch.

Agents must treat the lists here as non-negotiable (the "letter and spirit" of the handoff boundary rules).

(End of MULTI_AGENT_USER_INSTRUCTIONS.md. Created as part of the approved meta-plan for robust plan/execute cycle enforcement.)
