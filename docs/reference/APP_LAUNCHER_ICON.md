# App launcher icon (VehicleExpenses)

## Design source (Grok Imagine)

Canonical Imagine post (keep for redesign / color iterations):

**https://grok.com/imagine/post/c2fefde0-449d-4795-b48a-8f4d96cd0b8c**

Public share image (backup):

https://imagine-public.x.ai/imagine-public/share-images/c2fefde0-449d-4795-b48a-8f4d96cd0b8c.jpg

## Visual design

- Composition: car in front of a taller vehicle (van), black outlines, camera-shutter wheel (upper left), green badge with white `$` and `+`.
- Lightened palette (avoids dark grey “blob” on the launcher):
  - **Car:** very pale yellow body, **white** windshield
  - **Van:** very pale blue body, **white** windshield
- Format preference: **PNG RGBA** masters (raster Imagine art — not vector). Square **1:1**.

## Master assets (sandbox — do not delete)

Directory: `dev-ai-interaction/research/imagine-icon-candidate/`

| File | Role |
|------|------|
| `app-icon-master-1024.png` | Primary master (1024×1024 PNG RGBA) |
| `app-icon-master-512.png` | Play Store–sized master |
| `README.md` | Same folder: link + install notes |
| `android-export/` | Prebuilt mipmap densities + install script |

Also mirrored from Imagine post: `share.jpg`, `preview.png` (portrait mockup; crop/export from square masters for shipping).

## Installed app tree locations

| Role | Path |
|------|------|
| Adaptive icon XML | `app/src/main/res/mipmap-anydpi-v26/ic_vehicleexpenses.xml` (+ `_round`) |
| Full / round / foreground bitmaps | `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_vehicleexpenses*.webp` |
| Adaptive background | `app/src/main/res/drawable/ic_vehicleexpenses_background.xml` (solid light fill, e.g. `#FAFAFA`) |
| Play Store / marketing PNG | `app/src/main/ic_vehicleexpenses-playstore.png` (and aliases `vehicleexpenses-playstore.png`, `ic_launcher-playstore.png`) |
| Manifest | `android:icon` / `android:roundIcon` → `@mipmap/ic_vehicleexpenses` / `_round` |

## Regenerating densities from the master

From a worktree that can write `app/`:

```bash
bash dev-ai-interaction/research/imagine-icon-candidate/android-export/install-into-app.sh .
```

Or re-scale from `app-icon-master-1024.png` (see `android-export/INSTALL.md`):

| Asset | Sizes (px) |
|--------|------------|
| Full / round launcher | 48, 72, 96, 144, 192 |
| Adaptive foreground | 108, 162, 216, 324, 432 |
| Play Store | 512 |

## Related

- Sandbox research README: `dev-ai-interaction/research/imagine-icon-candidate/README.md`
- Install plan (if needed): `dev-ai-interaction/plans/install-lightened-app-icon-from-master-20260710-plan.md`
