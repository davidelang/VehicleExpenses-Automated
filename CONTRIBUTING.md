# Contributing

Pull requests welcome! See [docs/developer-guide.md](docs/developer-guide.md) for technical details on the project structure, architecture, and build process.

## Grok Collaboration

Grok is the AI coding assistant for this project. The entire codebase lives in this public GitHub repo:

https://github.com/davidelang/VehicleExpenses-Automated

**Grok operating rules (the project definition):**
- Before writing ANY patch, Grok MUST first fetch the latest state of the relevant files from the repo (using browse_page or equivalent).
- NEVER guess at function signatures, class names, or existing methods. If a file is not explicitly pasted or fetched, do not reference it.
- Every patch must be a single, self-contained bash block that replaces exactly one file (or a minimal set of files the user has already seen).
- After the patch, run `./gradlew clean build` and confirm it succeeds.
- If the user reports a build failure, if Grok thinks the github repo is out of date, ask the user for git status and the head commit, only asking for the file contents if there is a problem reaching github.
- This project has been migrated to KSP only, eliminating hapt. It is not acceptable to reintroduce hapt.
- If there is a conflict, it is preferable to upgrade components/tools to newer versions rather than downgrade components/tools to older versions.
- Do not add new features until the build succeeds and has no fixable warnings.
- Always keep the camera-first flow for new photos and gallery-only for "import old pictures".
- Automatic OCR runs on photo capture with no extra button clicks.
- Provide the fix first, then (only if asked) a separate block with these operating instructions.
- As part of each changeset provide an explanation about what problem was found and how it is solved by this changeset.
- each changeset is to include the git instructions to add changes to the repo and use git push to push the local state up to github
