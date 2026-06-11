// .grok/hooks/plan-mode-hard-stops.js
// PreToolUse hook for hard plan-mode enforcement (project-scoped, trusted).
//
// This provides hard stops analogous to .gemini/policies/plans.toml for Gemini.
// Native enter_plan_mode / SwitchMode + these hooks + [permission] rules in config.toml
// together enforce the bi-modal barrier (no writes to tracked files outside sandbox during planning).
//
// Example behaviors to implement (tune as needed for exact Grok hook API):
// - In plan mode: deny write/edit/bash to any path not under dev-ai-interaction/
// - Deny Task / subagent / invoke_agent during plan mode
// - Allow read/grep/jq/git-status/etc. broadly
// - Log or report violations
//
// The hook is executed by the Grok harness before tool use. Return allow/deny/ask as appropriate.
//
// Placeholder / starting point. Expand with real JS logic matching the Grok PreToolUse contract
// (see Grok documentation for the exact hook signature and return values).
// For now this file exists to declare the intent and be checked in as part of the project policy.

console.log("plan-mode-hard-stops hook loaded (placeholder)");
// TODO: implement actual pre-tool checks for sandbox confinement + plan mode restrictions.