# Multi-Agent Orchestration Layout

This repository uses a **Container/Worktree** layout designed for multiple AI agents working in parallel.

## 1. Directory Structure

- **Root (`VehicleExpenses-automated/`)**: Checked out to the `orchestration` branch. Contains shared infrastructure (rules, build scripts, sandbox).
- **`master/`**: A permanent worktree for the `master` branch. Used for oversight and merges.
- **`agent-N/`**: Transient worktrees for feature development.
- **`dev-ai-interaction/`**: Shared sandbox repository.

---

### 2.1. Creating a New Agent Environment
To assign a task to a new agent:
1.  Navigate to the project root.
2.  Run the setup script:
    ```bash
    ./setup_agent.sh agent-1 feature-name
    ```
    *This creates the `agent-1/` directory, checks out the branch, and sets up the shared infrastructure.*
3.  **Start the agent** (Recommended Process):
    To avoid a known CLI crash (`EBADF`) when using interactive prompts, start the agent first, then paste the bootstrap instruction:
    ```bash
    cd agent-1
    ../gemini/bin/gemini
    ```
    Once the agent is running, paste:
    > "Read new_agent_prompt and follow its instructions."

---
### 2.2. Sandbox Access Note
Due to security restrictions in the Gemini CLI, the built-in `read_file` tool may be blocked from accessing the `dev-ai-interaction/` symlink because it resolves outside the worktree. 

**If `read_file` fails on a sandbox path:** Instruct the agent to use `run_shell_command` with `cat` (e.g., `run_shell_command "cat dev-ai-interaction/plans/my-plan.md"`) instead. This bypasses the project-root security check.

---
### 2.3. Directory Structure Detail
- **Shared Rules:** Files in `.gemini/` and `new_agent_prompt` are **hard links** to the orchestration root. They are physically inside the worktree (satisfying security checks) but share the same disk data (maintaining a Shared Brain).
- **Local Memory:** The `.gemini/plans` directory is unique to each worktree.
...
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
