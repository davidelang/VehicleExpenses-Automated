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

