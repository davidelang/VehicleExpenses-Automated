# Multi-Agent Orchestration Layout

This repository uses a **Container/Worktree** layout designed for multiple AI agents working in parallel.

## 1. Directory Structure

- **Root (`VehicleExpenses-automated/`)**: Checked out to the `orchestration` branch. Contains shared infrastructure (rules, build scripts, sandbox).
- **`master/`**: A permanent worktree for the `master` branch. Used for oversight and merges.
- **`agent-N/`**: Transient worktrees for feature development (e.g., `agent-1`, `agent-2`).
- **`dev-ai-interaction/`**: Shared sandbox repository.

---

## 2. User Workflows

### 2.1. Creating a New Agent Environment
To assign a task to a new agent:
1.  Navigate to the project root.
2.  Run the setup script with the feature branch name:
    ```bash
    ./setup_agent.sh feature-name
    ```
    *This automatically picks the next available `agent-N` directory, creates the worktree, and sets up a `feature-name` symlink for easy access.*

3.  **Start the agent** (Recommended Process):
    To avoid a known CLI crash (`EBADF`) when using interactive prompts, start the agent first, then paste the bootstrap instruction:
    ```bash
    cd feature-name
    ../gemini/bin/gemini
    ```
    Once the agent is running, paste:
    > "Read new_agent_prompt and follow its instructions."

---

### 2.2. Merging and Cleanup
Once work is completed and merged into `master`:

1.  **Merge the branch** (from the `master/` directory):
    ```bash
    cd master
    git diff master..feature-name
    git merge feature-name
    git tag -f works
    ```

2.  **Remove the worktree and branch** (from the project root):
    ```bash
    cd ..
    ./remove_worktree.sh feature-name
    ```
    *This script verifies that all changes are committed and merged before removing the `agent-N` directory, the branch symlink, and the branch itself.*
    *Use `-f` or `--force` to bypass safety checks.*

---

### 2.3. Operational Notes
- **Directory Naming:** Physical worktrees are named `agent-1`, `agent-2`, etc., to allow for re-use. Use the branch-name symlinks for navigation.
- **Shared Brain:** All agent directories use **hard links** for rules in `.gemini/` and `new_agent_prompt`. 
    - **⚠️ WARNING:** These files are set to **Read-Only**. Modifying them in one place changes them everywhere.
    - **To Update Rules:** You must be in the orchestration root, `chmod 644 <file>`, edit, and `chmod 444 <file>` to restore protection.
- **Sandbox Access:** If `read_file` is blocked by project-root checks, use `run_shell_command "cat dev-ai-interaction/..."` to read sandbox files.
- **Build/Deploy:** Always run `./build_app` and `./deploy` from **inside** the specific agent directory/symlink. They are branch-aware.
