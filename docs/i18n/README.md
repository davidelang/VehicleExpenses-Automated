# Multi-locale user manuals (GitHub hosted)

Each subdirectory (`es/`, `fr/`, `pt-BR/`, …) holds:

| File | Role |
|------|------|
| `user-manual.md` | Edit source (translated) |
| `user-manual.html` | Rendered HTML (regenerate with `./scripts/render-user-manual.sh`) |
| `images/*.jpg` | Screenshots for that locale |

English remains at repo root: `docs/user-manual.md` / `.html` / `user-manual/images/`.

## Screenshots

**Target:** UI screenshots captured with the app in that language.

**Interim (this landing):** images may still be English UI copies so HTML layout works. Reshoot after deploy when validating each language (see `docs/reference/USER_MANUAL_BUILD.md`). Agents cannot `./deploy`; device capture is a human step after ready-to-test.

jsDelivr serves published branch content only after merge.
