---
type: implementation-reference
status: dynamic
ai_directive: "Update when the user-manual render pipeline or package paths change."
---

# User manual — edit vs browser (HTML)

## Why two formats

| Artifact | Audience | Notes |
|----------|----------|--------|
| **`docs/user-manual.md`** | Authors / git | **Edit source (English).** Markdown is easy to review and diff. |
| **`docs/user-manual.html`** | **Browsers / web** | **Rendered** with screenshots. Browsers do **not** treat raw `.md` as a document with images. |
| **`docs/user-manual/images/*.jpg`** | English web | Screenshots (phone + chrome). |
| **`docs/i18n/<tag>/user-manual.md`** | Authors | Locale edit source (`es`, `fr`, `pt-BR`, …). |
| **`docs/i18n/<tag>/user-manual.html` + `images/`** | **Browsers / web** | Hosted per-locale manual + screenshots. |
| **`app/src/main/assets/user-manual/`** | Optional offline EN | English-only WebView fallback; **not** multi-locale in APK. |

**Never** send end users to a raw GitHub `.md` URL for the full manual — they only see plain text.

## Hosted URLs (jsDelivr)

| Language | Path on master (after merge/publish) |
|----------|--------------------------------------|
| English | `https://cdn.jsdelivr.net/gh/davidelang/VehicleExpenses-Automated@master/docs/user-manual.html` |
| Other | `https://cdn.jsdelivr.net/gh/davidelang/VehicleExpenses-Automated@master/docs/i18n/<tag>/user-manual.html` |

App: `UserManualDocs.openFullManual` → Custom Tabs to `AppLanguage.onlineManualHtmlUrl` (active language). Optional offline English: `UserManualDocs.openOfflineEnglishManual`.

## How to update the manual

1. Edit **`docs/user-manual.md`** and/or **`docs/i18n/<tag>/user-manual.md`**.
2. Screenshots: English under `docs/user-manual/images/`; other locales under `docs/i18n/<tag>/images/` (reshoot UI in that language when possible).
3. Regenerate:

   ```bash
   ./scripts/render-user-manual.sh
   ```

   (Implementation: `scripts/render_user_manual.py`. Requires Python 3; installs `markdown` if missing.)

4. Commit **together**: md + html + images for each locale touched; English may also refresh `app/src/main/assets/user-manual/**`.
5. `./build_app` as usual when app code changed.

If you change only Markdown and forget the script, **web manuals will be stale**. jsDelivr reflects **published** branch content after merge.

## Screenshot workflow (per language)

1. Set Settings → Language to that pack.
2. Capture the manual image set (same filenames as English where possible).
3. Drop into `docs/i18n/<tag>/images/` (English: `docs/user-manual/images/`).
4. Re-run `./scripts/render-user-manual.sh` and commit.

Space-driven exception: until reshoot, locale trees may temporarily use English screenshots (document in PR); text should still be localized.

## Related

- Language packs: [I18N.md](I18N.md)
- Condensed user reference: [USER_GUIDE.md](USER_GUIDE.md)
- Orientation: worktree `project-facts.md` (user-manual paths)
