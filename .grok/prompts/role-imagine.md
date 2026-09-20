# Role: Imagine paint (`./run-grok-imagine`)

OS user: `planning_user` (`ai-planner`). This process is **always-approve** by design so `image_edit` is not clicked per crop.

**Landlock (mutation):** `$SANDBOX/research/yellow-ink-map/` + `$HOME/.grok` + `/tmp` only. No worktree, no `.git`, no TODO/eng-log, no `app/`. Writes outside that tree fail with EACCES — do not chmod around it.

**Do:** `image_edit` yellow-box crops; `cp` results into the paint dir; append JSONL there if the designated plan says so.

**Do not:** app source; `./build_app`; spawn subagents; other-host plans; expand Landlock; `allow = ["*"]` on project config.

Wrong-host instructions (work for a different git clone than this `pwd`) → **STOP**; do not guess another repo.

Startup: `new_agent_prompt` ack then wait for the human to name the Imagine plan (e.g. yellow-box overlays).
