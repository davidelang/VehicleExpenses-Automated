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
1.  Navigate to the project root (orchestration root on `orchestration` branch).
2.  Run the setup script with the feature branch name:
    ```bash
    ./setup_agent.sh feature-name
    ```
    *This automatically picks the next available `agent-N` directory, creates the worktree, and sets up a `feature-name` symlink for easy access.*

3.  **Start the agent** (Recommended Process):
    To ensure the agent is properly confined to its worktree and to avoid CLI crashes, use absolute paths for the root and the binary. Use the appropriate launcher:
    ```bash
    cd feature-name
    # For Grok CLI (preferred for new work):
    ../run-grok
    # Paste on launch: "Read new_grok_agent_prompt and follow its instructions."

    # For Gemini CLI (legacy/parallel):
    GEMINI_PROJECT_ROOT=$(pwd) ~/git/gemini/bin/gemini
    # Paste: "Read new_agent_prompt and follow its instructions."
    ```
    The launcher sets up the sandbox (`dev-ai-interaction`) and passes the fast-track prompt. Grok/Gemini will then read `AGENT_CONTEXT.md` + overlay + `AGENT_MANDATES.md`.

    *Note: The Gemini CLI uses the terminal's Alternate Buffer for its interactive UI. If it crashes, your terminal scrollback might be temporarily hidden. Type `reset` or `clear` in your terminal to restore the main buffer.*

### 2.1.5. Post-Handoff / New Cycle Start (relaunch or short gate file)
After any agent handoff ("results ready to test" + **END OF EXECUTION TURN** marker + `./build_app` creating the builds tag), the prior execution turn is finished. 

**Recommended (cleanest boundary):** Exit the current CLI session completely and relaunch with the normal command (`cd <agent-dir-or-symlink> ; ../run-grok` from the orchestration root, or the equivalent for the runtime). Every `run-grok` (etc.) injects the full fresh-session instruction that forces the Mandate Acknowledgment report, enter_plan_mode, and STOP. This gives a clean harness plan.md session (new uuid) and eliminates accumulation.

**If staying in the same long chat session:** The agent is required (as part of handoff completion) to write the current short gate text to `dev-ai-interaction/.post-handoff-gate.txt`. Use the trivial ritual:

```
cat dev-ai-interaction/.post-handoff-gate.txt
<then type or paste your actual feedback/request here>
```

The gate file itself is short; the authoritative lists of exact magic approval phrases the user *must* type (e.g. "approved the plan at dev-ai-interaction/<name>.md for the following...") and phrases the user must *never* say after a handoff live in the tracked `MULTI_AGENT_USER_INSTRUCTIONS.md`. For low-cost continuity, also read/maintain the local untracked `current-state.md` (or `.agent-state/current-state.md`) in the worktree root (gitignored, per-branch). See AGENT_MANDATES.md (Sandbox Plan File as Primary Artifact + Interactive Strategic Nature of Planning + local state), AGENTS.md, and the updated Plans Directory Rule.

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
