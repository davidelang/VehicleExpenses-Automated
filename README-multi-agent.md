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
    To ensure the agent is properly confined to its worktree and to avoid CLI crashes, use absolute paths for the root and the binary:
    ```bash
    cd feature-name
    GEMINI_PROJECT_ROOT=$(pwd) ~/git/gemini/bin/gemini
    ```
    Once the agent is running, paste:
    > "Read new_agent_prompt and follow its instructions."

    *Note: The Gemini CLI uses the terminal's Alternate Buffer for its interactive UI. If it crashes, your terminal scrollback might be temporarily hidden. Type `reset` or `clear` in your terminal to restore the main buffer.*

### 2.2. Critical Troubleshooting: EBADF Crash
If the Gemini CLI crashes with `An unexpected critical error occurred:Error: ioctl(2) failed, EBADF`, it is likely due to a **Policy Violation Race Condition**.

**The Cause:** The agent attempted to use a forbidden path traversal (e.g., `../`) in a command. The CLI correctly blocked the command, but the interactive UI crashed while trying to display the error.

**The Fix:**
1.  Restart the agent.
2.  **Instruction:** Explicitly tell the agent: *"You just crashed due to an EBADF error. Do not use '..' in any path. Use the project-local './dev-ai-interaction/' symlink instead."*

---

### 2.3. Merging and Cleanup
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
    *This script removes the worktree, the branch symlink, and the branch itself.*
    * **Auto-Cleanup:** If the branch is merged OR has no unique commits, it is deleted automatically.
    * **Force Levels:** 
        * `-f`: Removes worktree despite uncommitted changes.
        * `-ff`: Force deletes the branch even if not merged.

---

### 2.4. Operational Notes
- **Directory Naming:** Physical worktrees are named `agent-1`, `agent-2`, etc., to allow for re-use. Use the branch-name symlinks for navigation.
- **Shared Infrastructure:** Agent rules and mandates are physical copies synced via `./update-rules.sh`.
- **Sandbox Access:** Access the sandbox using the absolute path: `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/`.
- **Build/Deploy:** Always run `./build_app` and `./deploy` from **inside** the specific agent directory/symlink. They are branch-aware.

### 2.5. Infrastructure Constraints (CRITICAL)

**Plan Mode "Safe-List":**
While in Plan Mode, the `run_shell_command` tool is restricted to a hardcoded "Safe List" of binaries (e.g., `cat`, `grep`, `awk`, `python3`, `git`). Commands NOT on this list, including `jq`, are blocked with a "Execution of scripts is blocked" error, even if whitelisted in policies.
