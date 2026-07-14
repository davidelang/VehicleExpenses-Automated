# project-facts.md — Stable orientation map (orchestration)

Cold-start map so agents do not hunt or invent wrong procedures. Anything discovered that would help the *next* agent is a **candidate** to add here (short pointer). Merge process validates and prunes if large.

Read in full early on startup/new cycle.

## Sandbox (dev-ai-interaction)
- Absolute path: `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/`
- `dev-ai-interaction/plans/` — designated active plan (user names exact file)
- `dev-ai-interaction/historical-plans/` — archived plans
- `dev-ai-interaction/implementation-failure-logs/` — scan on planner startup / recovery
- `dev-ai-interaction/PRs/PR-<branch>.md` — local PR docs for Master
- `dev-ai-interaction/.planning-agent-prompt.txt` — optional planner prompt file

## Device / crash logs
- Prefer `adb logcat -d` (or device-specific) into sandbox once; analyze locally. Do **not** start broad `find … *.log` hunts on the host.

## At orchestration root
- `.gradle-shared/` — project `GRADLE_USER_HOME` (multi-user)
- `.android-shared/` — shared Android home / debug keystore
- `ENGINEERING_LOG.md` — append only via `./append-to-engineering-log`
- `TODO.md` — future backlog via `todo-append` / `todo-close`
- Launchers: `run-grok-orchestrator`, `run-grok-master`, `run-grok-planner`, `run-grok-coder`, bare `run-grok` (dlang)
- `ve-env` — `source ./ve-env` sets umask 002; if session groups stale, re-execs shell via setuid `ve-refresh-shell` (no full desktop logout). One-time: build + `sudo chmod 4755 ve-refresh-shell` (or `sudo ./fix-perms`). Never `newgrp` for multi-group. See `./ve-env how-to-fix-groups`
- Scripts: `update-rules.sh`, `build_app` and `deploy` (no raw gradlew; both pass `--no-daemon`; deploy wipes kspCaches before compile), `get-builds-tag.sh`, `fix-perms` (rare), `setup_agent.sh` (creates worktree; does **not** auto-start CLI), `remove_worktree.sh`, `generate_pr.sh`, `cleanup_pr.sh`
- `.grok/config.toml` + `.grok/hooks/` + `.grok/skills/` (prepare-local-pr, master-merge)
- `MASTER_AGENT_MANDATE.md` — Master review/merge SoT
- `standard-plan-compliance-block.md` — cite by path in plans

## Worktree layout
- App worktrees (`agent-N/`, `master/`): `app/` + root scripts + symlink `dev-ai-interaction -> ../dev-ai-interaction`
- Orchestration root: source of brain for `update-rules.sh`; may lack `app/` depending on layout mode
- Sessions: human `cd`s into worktree once, then launcher; agents keep that cwd (no per-command `cd && helper`)

## Permissions targets
- Source: dirs **2775**, files **664**, umask **002**
- Build: dirs **2770**, files often **660**
- See `docs/specs/PERMISSIONS_MODEL.md`

Update only with orientation facts valid for future work. Effort/plan details → plan file or ENGINEERING_LOG.
