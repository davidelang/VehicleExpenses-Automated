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

### 2.3. Handoff and Cleanup (PR Workflow)
Once work is completed in an agent worktree, follow this handoff protocol to merge into `master`:

#### Step A: Commit Cleanup (Feature Agent)
1. **Plan:** Propose a cleanup plan (e.g., `plans/cleanup.md`) that groups your messy "strike" commits into logical, compiling chunks. Ideally, target a single clean commit per feature.
2. **Backup:** Tag your current messy state: `git tag backup-<branch-name>`.
3. **Squash/Restructure:** 
   - **Path 1 (Structured):** Use `git reset --soft master` and commit your changes in the approved logical chunks. Verify each step compiles with `./build_app`.
   - **Path 2 (Atomic Squash):** Use `git commit-tree` to create a single pristine commit from your current tree.
4. **Verify Integrity:** Run `git diff HEAD backup-<branch-name>`. It MUST be empty. This guarantees your tested code is identical to your cleaned code.

#### Step B: Generate PR (Feature Agent)
Run the generation script, providing all plan documents used during the task:
```bash
./generate_pr.sh plans/initial-plan.md plans/cleanup.md
```
*This creates a Pull Request document in `dev-ai-interaction/PRs/PR-<branch-name>.md`.*

#### Step C: Review and Merge (Master Agent)
1. **Request Review:** Tell the user to notify the Master Agent: *"Please review PR-<branch-name>"*.
2. **Review:** The Master Agent (in the `master/` worktree) reads the PR document and performs a forensic audit: `git diff master..<branch-name>`.
3. **Merge:** If approved, the Master Agent merges:
   ```bash
   git merge --no-ff <branch-name> -m "Merge PR: <branch-name>"
   ./build_app
   git tag -f works
   ```

### 2.4. Removing the Agent Environment
Once the branch is merged into `master`, the worktree and branch can be removed from the root directory:

---

### 2.4. Operational Notes
- **Directory Naming:** Physical worktrees are named `agent-1`, `agent-2`, etc., to allow for re-use. Use the branch-name symlinks for navigation.
- **Shared Brain:** All agent directories use **hard links** for rules in `.gemini/` and `new_agent_prompt`. 
    - **⚠️ WARNING:** These files are set to **Read-Only**. Modifying them in one place changes them everywhere.
    - **To Update Rules:** You must be in the orchestration root, `chmod 644 <file>`, edit, and `chmod 444 <file>` to restore protection.
- **Sandbox Access:** If `read_file` is blocked by project-root checks, use `run_shell_command "cat dev-ai-interaction/..."` to read sandbox files.
- **Build/Deploy:** Always run `./build_app` and `./deploy` from **inside** the specific agent directory/symlink. They are branch-aware.
