# Vehicle Expenses Automated (Orchestration Root)

This is the **orchestration root** for a multi-agent development environment.

## 🚀 Quick Start
For instructions on how to manage agents, create branches, and merge work, see:
**[README-multi-agent.md](README-multi-agent.md)**

## 📂 Layout Overview
- **`master/`**: Main development worktree.
- **`agent-N/`**: Dynamic worktrees for feature agents.
- Shared brain files (`.gemini/*`, `GEMINI.md`, `AGENT_MANDATES.md`, `AGENTS.md`, `GROK.md`, `new_*_prompt`, etc.): tracked on the branch and delivered by `git worktree add` (from master tip) or hotfixed via `update-rules.sh` from the orchestration root. The sandbox symlink (`dev-ai-interaction`) is the only one created locally.

---
## Repository History
The full source code and application history are managed via Git worktrees. Navigate to `master/` to see the primary application code.