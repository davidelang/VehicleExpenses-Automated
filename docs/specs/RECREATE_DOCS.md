---
type: intent-spec
status: locked
ai_directive: "This is an upstream specification. DO NOT modify this document to match the codebase. If the code deviates from this spec, the code is wrong. Modifications to this file require a dedicated 'Strategy' turn and explicit user approval."
---

# RECREATE_DOCS.md — Instructions for AI to recreate all files in the /docs directory

When the user asks you to create, update, or recreate documentation, you MUST produce exactly these files with the following contents:

1. **README.md** — Project overview, build instructions, quick start.
2. **docs/API.md** — Complete list of every public class, data class, object, function, constructor with file path, purpose, parameters, return type.
3. **docs/ARCHITECTURE.md** — High-level architecture and data flow.
4. **docs/RECREATE_DOCS.md** (this file) — Must always contain the latest instructions for Grok/Gemini.
5. **docs/CHANGELOG.md** — Chronological list of changes.
6. **docs/USER_GUIDE.md** — End-user instructions.
7. **docs/DEVELOPER_GUIDE.md** — How to extend the system and use research scripts.

## STRICT VERIFICATION RULE (added 2026-04-04) — THIS IS THE MOST IMPORTANT RULE

**Before any patch that adds, changes, or references ANY class, object, function, data class, or public API (even ones you have seen in previous conversations):**

1. You **MUST** first call `browse_page` or `read_file` on the exact file that defines that class/function.
2. You must paste the full verbatim content of that file into your thinking trace.
3. You must explicitly confirm the import path and exact signature before writing any code that uses it.
4. If you cannot fetch the file, output exactly:  
   **"I am unable to verify reference — aborting and waiting for your next instruction."**

This rule overrides all previous assumptions and must be followed on every single patch, every time. No exceptions.

This file itself must never be overwritten or deleted unless the user explicitly asks.