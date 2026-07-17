---
type: implementation-reference
status: dynamic
ai_directive: "Update when the user-manual render pipeline or package paths change."
---

# User manual — edit vs browser (HTML)

## Why two formats

| Artifact | Audience | Notes |
|----------|----------|--------|
| **`docs/user-manual.md`** | Authors / git | **Edit source.** Markdown is easy to review and diff. |
| **`docs/user-manual.html`** | **Browsers / web** | **Rendered** with screenshots. Browsers do **not** treat raw `.md` as a document with images. |
| **`docs/user-manual/images/*.jpg`** | Both | Screenshots (phone + chrome). |
| **`app/src/main/assets/user-manual/`** | **In-app** | Offline HTML + images for Help / About. |

**Never** send end users to a raw GitHub `.md` URL for the full manual — they only see plain text.

## In-app entry points

- Help / About → `UserManualDocs.openFullManual` → `UserManualActivity` (WebView loads `file:///android_asset/user-manual/index.html`).
- Optional published web HTML (after master has the file): `UserManualDocs.ONLINE_HTML_URL`  
  `https://cdn.jsdelivr.net/gh/davidelang/VehicleExpenses-Automated@master/docs/user-manual.html`  
  (no GitHub login; images via relative `user-manual/images/` paths next to the HTML).

## How to update the manual

1. Edit **`docs/user-manual.md`** (and add/replace images under **`docs/user-manual/images/`** if needed).
2. Regenerate browser HTML + app assets:

   ```bash
   ./scripts/render-user-manual.sh
   ```

   (Implementation: `scripts/render_user_manual.py`. Requires Python 3; installs `markdown` if missing.)

3. Commit **together**:
   - `docs/user-manual.md`
   - `docs/user-manual.html`
   - `docs/user-manual/images/*` (if changed)
   - `app/src/main/assets/user-manual/**` (regenerated)

4. `./build_app` as usual.

If you change only Markdown and forget the script, the **in-app and web manuals will be stale**.

## Related

- Condensed user reference: [USER_GUIDE.md](USER_GUIDE.md)
- Orientation: worktree `project-facts.md` (user-manual paths)
