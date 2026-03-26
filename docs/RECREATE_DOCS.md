# RECREATE_DOCS.md — Instructions for Grok to recreate all files in the /docs directory

When the user asks you to recreate all files in the docs directory:

1. Call browse_page on https://github.com/davidelang/VehicleExpenses-Automated/tree/master/docs with instructions: "List every file currently in the /docs directory exactly (filenames only)."

2. For each filename returned, call browse_page on the raw URL:
   https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/FILENAME.md
   with instructions: "Return the full exact raw content of the file."

3. After fetching every file's exact content, create a single self-contained bash block that:
   - Uses cat <<'EOF' > docs/FILENAME.md for each file
   - Writes the exact content fetched
   - Ends with the git add, commit, and push commands for all docs files
   - Runs the exact required command: ./gradlew clean build; git describe

4. In the explanation section state: "This changeset recreates every file in /docs exactly as it exists on GitHub."

5. Never guess or modify content — use only the exact text returned by the raw fetches.

This file itself must never be overwritten or deleted unless the user explicitly asks.
