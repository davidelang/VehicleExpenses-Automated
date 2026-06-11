# Grok Project Mandates (Overlay)

This is a thin overlay. The authoritative shared content is in `AGENT_MANDATES.md` (read it for bi-modal workflow, tags, reset rules, deploy ban, coordinates, forensic, etc.).

**Grok CLI specifics:**
- Tool mapping: Read, Write, StrReplace, Shell, Task, SwitchMode (plan/agent).
- Phase gating: Use plan mode (enter_plan_mode / SwitchMode) for research/strategy; only exit after explicit user approval of a plan and directive to implement.
- Old plans rule: as above.
- Coordinates: ICRS or pixel only.
- Git reset: three contexts with preflight.
- No deployment.
- Orchestration layer: awareness of all agents.

**Note:** The "re-read rules after compaction" is a shared mandate (see AGENT_MANDATES.md). It applies to all agent types.