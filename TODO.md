# TODO

- [x] Refactor Agent Workspace Syncing
    - [x] Update `setup_agent.sh` to remove hard links and protections.
    - [x] Update `update-rules.sh` to push updates and commit to all worktrees.
    - [x] Validate changes by audit and build.
- [/] Fix Sandbox Policy Permissions
    - [ ] Update `.gemini/policies/plans.toml` with whitespace tolerance.
    - [ ] Update `.gemini/policies/auto-saved.toml` to cleanup mode-based restrictions.
    - [ ] Commit and sync rules across all worktrees.
