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

1. **README.md**  
   - Project overview  
   - How to build and run the app  
   - Quick start guide for users  
   - Link to the full documentation index

2. **docs/API.md**  
   - Complete list of every public class, data class, object, function, and constructor  
   - For each item: file path, purpose, parameters/fields, return type, and usage notes  
   - Organized by package/module

3. **docs/ARCHITECTURE.md**  
   - High-level architecture diagram description  
   - Explanation of main modules (UI, data, util, etc.)  
   - How data flows between layers (Room, ViewModel, Compose, OpenCV, OCR)

4. **docs/RECREATE_DOCS.md** (this file)  
   - Must always contain the latest instructions for Grok on how to recreate docs  
   - Must include this exact section listing what docs to create

5. **docs/CHANGELOG.md**  
   - Chronological list of major changes with dates and commit hashes  
   - Summary of each release or significant patch

6. **docs/USER_GUIDE.md**  
   - Step-by-step instructions for end users (adding vehicles, taking photos, quick fill-up, viewing reports)  
   - Explanation of crop boxes and OCR

7. **docs/DEVELOPER_GUIDE.md**  
   - How to add a new vehicle type  
   - How to extend the alignment / OCR system  
   - How to run experiments and interpret reports

When recreating docs, always use the exact content fetched from GitHub for existing files and only add/update the sections above if the user explicitly asks or if the file is missing.

This file itself must never be overwritten or deleted unless the user explicitly asks.

You can’t perform that action at this time.
