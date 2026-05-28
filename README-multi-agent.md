# Multi-Agent Orchestration Layout

This repository uses a **Container/Worktree** layout designed for multiple AI agents working in parallel.

## 1. Directory Structure

- **Root (`VehicleExpenses-automated/`)**: Checked out to the `orchestration` branch. Contains shared infrastructure (rules, build scripts, sandbox).
- **`master/`**: A permanent worktree for the `master` branch. Used for oversight and merges.
- **`agent-N/`**: Transient worktrees for feature development.
- **`dev-ai-interaction/`**: Shared sandbox repository.

---

## 2. User Workflows

### 2.1. Creating a New Agent Environment
To assign a task to a new agent:
1.  Navigate to the project root.
2.  Run the setup script:
    ```bash
    ./setup_agent.sh agent-1 feature-name
    ```
    *This creates the `agent-1/` directory, checks out the branch (creating it if needed), sets up symlinks to the shared brain/sandbox, and creates a versioning anchor tag.*

### 2.2. Merging Work to Master
Once an agent has completed a task and cleaned up their history:
1.  Navigate to the `master/` directory.
2.  Review the agent's branch:
    ```bash
    git diff master..feature-name
    ```
3.  Merge the branch:
    ```bash
    git merge feature-name
    ```
4.  Update the `works` tag:
    ```bash
    git tag -f works
    ```

### 2.3. Cleaning Up / Deleting an Agent Worktree
When a feature is merged and you want to reclaim the directory:
```bash
git worktree remove agent-1
git branch -d feature-name
```

### 2.4. Re-using an Agent Directory
You do **not** need to manually "clean" a directory to re-use it. Simply delete the worktree as shown in 2.3, and then run `setup_agent.sh` again with the same `agent-N` name but a new branch. The script will recreate the environment fresh.

---

### 2.5. Operational Notes
- **Shared Brain:** All agent directories symlink to `.gemini/` in the root. Changing a rule in the root immediately affects all agents.
- **Build/Deploy:** Always run `./build_app` and `./deploy` from **inside** the specific agent directory. They are branch-aware and will manage your `branch/builds` and `branch/deployed` tags automatically.
- **Git Describe:** The version string will automatically include your branch name and the number of commits since the branch started.
