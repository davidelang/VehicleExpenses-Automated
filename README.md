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

## Usage Modes (orchestration layer separation in progress)
This repository supports two views (target state after plan completion; current state still has mixing via physical brain copies):

- **Standalone app view** (plain clone or `master` branch checkout): A clean checkout of the application sources (`app/`, Gradle files, app docs). Includes opt-in bootstrap documentation and the minimal stampable files (setup-project, set-*-perms, project.config.example, filter scripts, relevant README sections) so a user can optionally enable the full multi-agent orchestration layout.
- **Full orchestration layout** (current primary at this root on `orchestration` branch): Managing tree containing the complete shared brain (update-rules.sh, launchers, all mandates/AGENTS files, .grok/, policies, setup scripts, etc.). `master/` and `agent-N/` are worktrees receiving physical copies of infra. `dev-ai-interaction/` sandbox is shared via symlink.

No orchestration-infra sync changes app/ source content (language-agnostic; explicit FILES list + guards in update-rules.sh enforce this). See the approved plan at dev-ai-interaction/plans/orchestration-layer-separation-and-cleanup-plan.md and README-multi-agent.md for details. Current state has brain files present in worktree roots on all branches; separation will reduce leakage into plain master view.