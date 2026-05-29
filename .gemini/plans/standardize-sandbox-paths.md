# Plan: Standardize Sandbox References to Absolute Paths

This plan updates all remaining references to the `./dev-ai-interaction` symlink with the absolute path `/home/dlang/git/VehicleExpenses-automated/dev-ai-interaction/`. This ensures compatibility with the Gemini CLI `--include-directories` configuration and avoids crashes related to symlink terminal resizing in `node-pty`.

## Phase 1: Policy Updates (Critical)
Update the policy files to allow the absolute path in Plan Mode.

- **File:** `.gemini/policies/plans.toml`
    - Update `argsPattern` for `write_file` and `replace` to include the absolute path.
    - Update `argsPattern` for `run_shell_command` `dir_path` to include the absolute path.
- **File:** `.gemini/policies/auto-saved.toml`
    - Update `argsPattern` for `write_file` to include the absolute path.

## Phase 2: Core Documentation
Update foundational project files.

- **File:** `GEMINI.md`
    - Replace `./dev-ai-interaction` and `dev-ai-interaction/` with the absolute path.
- **File:** `MASTER_AGENT_MANDATE.md`
    - Ensure all mentions use the absolute path.
- **File:** `README-multi-agent.md`
    - Update mentions of the shared sandbox location.
- **File:** `agent_reminder`
    - Update the authorization reminder.

## Phase 3: System Prompts
Update the agent's internal instructions.

- **File:** `.gemini/system.md`
    - Update Sandbox Analysis and Sandbox Integrity sections.
- **File:** `.gemini/system_prompt.md`
    - Update Sandbox Analysis and Integrity sections.

## Phase 4: Project Metadata
- **File:** `AGENT_CONTEXT.md.template`
    - Update the template to match the new standard.
- **File:** `AGENT_CONTEXT.md`
    - Update the current context.

## Verification
- Enter Plan Mode and verify that `write_file` to a test file in the absolute path is allowed by the updated policy.
- Verify that `list_directory` works on the absolute path.
