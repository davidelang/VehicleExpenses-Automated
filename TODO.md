# TODO.md — Future work / backlog only

**Important discipline:**
- Only record high-level future items here (things not actively being worked on in the current cycle).
- Current cycle work: record the approved plan reference at start of execution (high-level only, 1-2 lines). Full phased steps, forensic details, and build gates belong in the plan file (`dev-ai-interaction/plans/xxx-plan.md`) and ENGINEERING_LOG.md (append-only).
- Do not paste execution transcripts or every sub-step into TODO.md.
- See AGENT_MANDATES.md for "First action: update TODO.md (record the approved plan)" and the rule that detailed progress lives in the active plan + ENGINEERING_LOG.

# Historical completed work (collapsed - details are in the original plans and ENGINEERING_LOG)

- [x] Safe leading env assignment prefix support (plan 2026-06-13)
- [x] Refactor Agent Workspace Syncing
- [x] Fix Sandbox Policy Permissions
- [x] Refine update-rules.sh Robustness
- [x] Cleanup Reports on Device
- [x] Enforce Git Reset and Validation Rigor
- [x] Refine Git Reset and Approval Policy
- [x] Recommend jq for JSON Parsing
- [x] Fix jq and Whitespace Permissions
- [x] Resolve jq Plan Mode Block
- [x] Refactor jq Rule and Whitelist ls
- [x] Fix Master Agent 'works' Tag Violation

# Meta plans (process / workflow improvements)
These are documented in AGENT_MANDATES.md, MULTI_AGENT_USER_INSTRUCTIONS.md, and the relevant plan files. They are kept here for historical reference only.

# Current cycle (execution)
- [x] Externalize P4 to files, reduce JSON bloat, add heap diagnostics — plan: dev-ai-interaction/plans/externalize-p4-to-files-reduce-json-bloat-and-add-heap-diagnostics-20260621-221000-plan.md

# Future work
(Only items that are not currently active in a plan.)

# Completed (high level only - see plans/ and ENGINEERING_LOG.md for details)
- [x] Separate the orchestration layer (orchestration-infra vs app source) — plan: dev-ai-interaction/plans/orchestration-layer-separation-and-cleanup-plan.md
  - Results ready to test.
