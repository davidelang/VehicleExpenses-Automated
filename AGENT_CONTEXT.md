# AGENT_CONTEXT.md Template

- **Agent ID:** master
- **Current Branch:** master
- **Active Plan:** [Link to dev-ai-interaction/plans/...]
- **Status:** IDLE

---
## Instructions for the Agent
1.  **Identity:** You are working in a dedicated Git worktree.
2.  **Sandbox:** Access the shared sandbox via the `./dev-ai-interaction/` symlink.
3.  **Brain:** Your rules and system prompts are shared via the `./.gemini/` symlink.
4.  **Tagging:** Use `./build_app` and `./deploy`. They will automatically create branch-scoped tags (e.g., `branch/builds`).
5.  **State:** Keep this file updated if your status changes.
