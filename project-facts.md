# project-facts.md — Stable "where things live" facts (orchestration)

Contains only locations and structure facts that remain true across efforts on this tree (after merge + new worktree for different work).

Read in full early on startup/new cycle to avoid discovery searches.

## Sandbox (dev-ai-interaction)
- Absolute path: `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/`
- `dev-ai-interaction/plans/` — designated active plan (user names the exact file)
- `dev-ai-interaction/historical-plans/` — archived/rolled plans
- `dev-ai-interaction/implementation-failure-logs/` — scan on planner startup and "new planning cycle"
- `dev-ai-interaction/.planning-agent-prompt.txt` — prompt written by master for planner restarts

## At worktree root
- `ENGINEERING_LOG.md` (append-only at end)
- `project-facts.md` (this file)
- `TODO.md`
- Launchers: `run-grok`, `run-grok-planner`, `run-grok-master`
- Scripts: `update-rules.sh` (run here to sync to worktrees), `build_app`, `get-builds-tag.sh`, `fix-perms`, `setup-project`, `setup_agent.sh`, `remove_worktree.sh`, `generate_pr.sh`, `cleanup_pr.sh`, `sync_infrastructure.sh`
- `deploy` — `--restore-data` standalone restore (no build/uninstall); `--preserve-data` backups under `test-data-backups/<dev>-<ts>/` include `ve_source_zips.tar.gz` (device `/sdcard/Download` zips)
- `.grok/config.toml` + `.grok/hooks/`
- `project.config.example`

## Worktree layout
- App worktrees: `app/` + gradle bits + root scripts/launchers + symlink `dev-ai-interaction -> ../dev-ai-interaction`
- This root (orchestration) is source for shared scripts/brain; use update-rules.sh to push
- At managing orchestration root: app/ source tree is absent (only in dedicated worktrees); project-facts.md and facts remain stable for both managing root and app worktree usage.
- Launchers (run-grok*) and setup assume full layout with dev-ai symlink (created by setup_agent or enable-full-orchestration in stamped plain trees). Plain master remains independently usable.

Update only with new stable location facts valid for future unrelated work. Effort/plan details belong in the active plan or ENGINEERING_LOG.md.
