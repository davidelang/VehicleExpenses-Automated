# RECREATE_DOCS.md — Instructions for Grok to recreate all files in the /docs directory

When the user asks you to recreate all files in the docs directory:

1. Call browse_page on https://github.com/davidelang/VehicleExpenses-Automated/tree/master/docs with instructions: "List every file currently in the /docs directory exactly (filenames only)."
2. For each filename returned, call browse_page on the raw URL: https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/FILENAME.md with instructions: "Return the full exact raw content of the file."
3. After fetching every file's exact content, create a single self-contained bash block that:
   - Uses cat <<'EOF' > docs/FILENAME.md for each file
   - Writes the exact content fetched
   - Ends with the git add, commit, and push commands for all docs files
   - Runs the exact required command: ./gradlew clean build; git describe

**New requirement (added 2026-03-31):**  
- Always include docs/API.md in the recreation. This file must contain a complete list of every public function, class, object, and data class in the repo, with the file it lives in, its purpose, and parameters.

**New requirement (added 2026-04-03):**  

## Instructions to Grok: What docs must exist and what they must contain

When the user asks you to create, update, or recreate documentation, you MUST produce exactly these files with the following contents:

1. **README.md** — Project overview, build instructions, quick start.
2. **docs/API.md** — Complete list of every public class, data class, object, function, constructor with file path, purpose, parameters, return type.
3. **docs/ARCHITECTURE.md** — High-level architecture and data flow.
4. **docs/RECREATE_DOCS.md** (this file) — Must always contain the latest instructions for Grok.
5. **docs/CHANGELOG.md** — Chronological list of changes.
6. **docs/USER_GUIDE.md** — End-user instructions.
7. **docs/DEVELOPER_GUIDE.md** — How to extend the system.

## STRICT VERIFICATION RULE (added 2026-04-04) — THIS IS THE MOST IMPORTANT RULE

**Before any patch that adds, changes, or references ANY class, object, function, data class, or public API (even ones you have seen in previous conversations):**

1. You **MUST** first call `browse_page` on the exact file that defines that class/function.
2. You must paste the full verbatim content of that file into your thinking trace.
3. You must explicitly confirm the import path and exact signature before writing any code that uses it.
4. If you cannot fetch the file, output exactly:  
   **"I am unable to verify reference — aborting and waiting for your next instruction."**

This rule overrides all previous assumptions and must be followed on every single patch, every time. No exceptions.

This file itself must never be overwritten or deleted unless the user explicitly asks.

