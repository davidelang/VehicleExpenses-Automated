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

- **Device used (2026-08-01 / 2026-08-02):** `emulator-5556`
- **Helper:** `scripts/capture-manual-shot.sh`, `scripts/capture-i18n-manual-screenshots.py`
- **Strict mode:** `--strict` (package focus, localized nav proof, no silent EN image copy, retry consecutive MD5). Prefer for `id`.
- **Indonesian note (2026-08-02):** `id` was re-captured with `--strict` after `values-in/` + AppLanguage bcp47 `in` (Android resource match). Metrics: `identical_to_en == 0`, unique hashes ≥ 20, Indonesian drawer (`Pengaturan` / `Pengisian Cepat`).
- **No deploy by agents** unless locale fix / missing app requires it for capture quality.

Some camera/OCR and nested form shots are best-effort (placeholder preview, opened existing sync dest, etc.). Chrome (drawer, titles, Help, Settings) should reflect the locale.

Re-run:

```bash
python3 scripts/capture-i18n-manual-screenshots.py --serial emulator-5556 --locales id --strict
./scripts/render-user-manual.sh
```

jsDelivr serves published branch content only after merge.

## Related

- `docs/reference/I18N.md`
- `docs/reference/USER_MANUAL_BUILD.md`
