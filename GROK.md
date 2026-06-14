# Grok Project Mandates (Overlay)

This is a thin overlay. The authoritative shared content is in `AGENT_MANDATES.md` (read it for bi-modal workflow, tags, reset rules, deploy ban, coordinates, forensic, etc.).

**Grok CLI specifics:**
- Tool mapping: Read, Write, StrReplace, Shell, Task, SwitchMode (plan/agent).
- Phase gating: Use plan mode (enter_plan_mode / SwitchMode) for research/strategy; only exit after explicit user approval of a plan and directive to implement. During planning, "being helpful/proactive/efficient" means research, suggesting ideas, and improving the plan document (in dev-ai-interaction/plans/) — it does **not** mean making source changes or compiling. Do not call exit_plan_mode to present until the user has had full interactive discussion on the written plan file in dev-ai-interaction/plans/. User rejection or continued feedback on a draft = "continue revising the plan document", not "abandon the plan." For complex work, use sub-agents: spawn a narrow Planning Sub-agent (research + plan doc only; it must not call exit_plan_mode) for the back-and-forth, review the produced file, get explicit user approval of the written plan, then spawn an Execution Sub-agent with the plan injected. The main agent orchestrates and reviews fidelity. The primary planning artifact is always a fresh plan file written to dev-ai-interaction/plans/ (see AGENT_MANDATES "Sandbox Plan File as the Primary..." + MULTI_AGENT_USER_INSTRUCTIONS.md); the harness session plan.md is process log only (roll + minimal prepend, never the approved work plan).
- Old plans rule: as above (plus harness plan hygiene and explicit sandbox plan file requirement in AGENT_MANDATES).
- Coordinates: ICRS or pixel only.
- Git reset: three contexts with preflight.
- No deployment.
- Orchestration layer: awareness of all agents.

**Note:** The "re-read rules after compaction" is a shared mandate (see AGENT_MANDATES.md). It applies to all agent types.