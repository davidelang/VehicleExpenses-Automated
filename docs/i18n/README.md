# Multi-locale user manuals (GitHub hosted)

Each subdirectory (`es/`, `fr/`, `pt-BR/`, …) holds:

| File | Role |
|------|------|
| `user-manual.md` | Edit source (translated) |
| `user-manual.html` | Rendered HTML (`./scripts/render-user-manual.sh`) |
| `images/*.jpg` | **Locale UI screenshots** (same 28 basenames as English) |

English remains at repo root: `docs/user-manual.md` / `.html` / `user-manual/images/`.

## Screenshots

Captured on device with **Settings → Language** (or `cmd locale set-app-locales` + `app_language` pref) set to that pack, then navigated through the manual shot list.

- **Device used (2026-08-01):** `emulator-5556`
- **Helper:** `scripts/capture-manual-shot.sh`, `scripts/capture-i18n-manual-screenshots.py`
- **No deploy by agents** — use the build already on the emulator.

Some camera/OCR and nested form shots are best-effort (placeholder preview, opened existing sync dest, etc.). Chrome (drawer, titles, Help, Settings) should reflect the locale.

Gaps / weaker automation for a language (e.g. flaky drawer during `id` capture) may leave a few screens less distinct; re-run:

```bash
python3 scripts/capture-i18n-manual-screenshots.py --serial emulator-5556 --locales id
./scripts/render-user-manual.sh
```

jsDelivr serves published branch content only after merge.

## Related

- `docs/reference/I18N.md`
- `docs/reference/USER_MANUAL_BUILD.md`
