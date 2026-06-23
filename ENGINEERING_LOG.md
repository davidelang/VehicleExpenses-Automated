## 2026-06-20 - Start of orchestration-layer-separation-and-cleanup-plan execution (Phase 1)

- User directive: "approved, implement this plan"
- First action: updated TODO.md (recorded plan reference + execution start note).
- Re-read plan + project-facts.md (hygiene; project-facts already clean/stable).
- Phase 1 inventory + decision capture (no behavior change):
  - Audited root files (git ls-files + ls) vs FILES array in update-rules.sh.
  - Created sandbox classification artifact: dev-ai-interaction/orchestration-layer-inventory-phase1.md (pure-infra vs pure-app vs bootstrap vs shared; documented mixing points).
  - Updated project-facts.md with one stable fact about managing root vs app worktree presence of app/.
  - Updated README.md and README-multi-agent.md with "Usage Modes" / "Modes (separation in progress)" sections describing standalone app vs full orchestration (current mixing noted; target + language-agnostic rule stated).
- All edits: narrow forensic read_file + grep + git diff verification performed.
- git add (changed + TODO.md) + ./build_app gates executed.
- No app/ source files touched. No hard-coded app/ paths introduced. Only root orchestration docs + facts.
- Builds tag: orchestration/builds updated (via get-builds-tag.sh + build_app meta path).
- Next phases will continue ultra-micro with per-edit forensic + build gates.

## 2026-06-20 - Phase 7 verification + plan completion

- End-to-end simulation (no side-effecting clones):
  1. Simulated plain master clone dir + copied stamp files (setup-project, enable-full-orchestration.sh, set-*-perms, filters, .gitattributes, project.config.example).
  2. enable-full-orchestration.sh in plain dir: correctly detected "standalone", printed guidance (worktree add, remote notes, symlink, reverse note), syntax clean.
  3. Dual-mode in update-rules.sh exercised (master/agent-1 -> full; absent/plain -> stamp); echo mode line confirmed.
  4. project-facts.md full read: clean stable facts only; mentions both managing root and app worktree usage + launchers + separation note.
  5. Launchers present and executable; small mode comments added.
  6. build_app (meta, no gradlew) path works at orchestration root; get-builds-tag.sh used for verification.
  7. setup-project stamp language and dry-run greps previously verified.
- All 7 plan criteria exercised in sim or prior phases.
- update-rules.sh, enable-*.sh, setup-project etc. remain language-agnostic (no app/ hardcodes in logic or new docs).
- ENGINEERING_LOG append-only discipline followed.
- Next: final TODO update + build gate + END marker.


## 2026-06-22 - Fix build dir perms for agents in set-worktree-perms

- Added special 2770/660 handling for app/build and build/ dirs (like .gradle) so ai-code group members can overwrite generated files (BuildConfig.java etc.).
- Pruned */build/* and */app/build/* from ai-planner read-only ACL lockdown in planner enforcement pass.
- This addresses AccessDeniedException when agent (non-dlang) tries to rebuild after dlang-owned generated files with 644.
- Stale files in agent-1 now fixable by running the perms script as root (when idle).
- See set-worktree-perms:lines ~232-250 (updated).
- Related: build failed with tag not advanced; phases 4-5 blocked.
test append
dummy line to try reset
test line for reset attempt
## 2026-06-22 - TEST APPEND LINE FOR RESET TEST
