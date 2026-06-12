// .grok/hooks/plan-mode-hard-stops.js
// PreToolUse hook for hard plan-mode enforcement (project-scoped, trusted).
//
// This provides hard stops analogous to .gemini/policies/plans.toml for Gemini.
// Native enter_plan_mode / SwitchMode + these hooks + [permission] rules in config.toml
// together enforce the bi-modal barrier (no writes to tracked files outside sandbox during planning).
//
// Key policy (as of 2026-06):
// - In plan mode: deny write/edit (search_replace/write) and most bash to paths
//   outside dev-ai-interaction/ (the sandbox). Use the config rules for granular
//   exceptions inside the sandbox and for local state files.
// - When NOT in plan mode (normal execution after approved plan + exit_plan_mode):
//   edits to tracked files are allowed (see blanket allow for search_replace/write
//   in .grok/config.toml). This avoids constant permission prompts during execution.
// - Deny Task / subagent / invoke_agent during plan mode.
// - Allow read/grep/jq/git-status/etc. broadly.
// - Log or report violations.
//
// The hook is executed by the Grok harness before tool use. Return allow/deny/ask as appropriate.
//
// Placeholder / starting point. Expand with real JS logic matching the Grok PreToolUse contract
// (see Grok documentation for the exact hook signature and return values).
// For now this file exists to declare the intent and be checked in as part of the project policy.

console.log("plan-mode-hard-stops hook loaded (placeholder)");
// TODO: implement actual pre-tool checks for sandbox confinement + plan mode restrictions.