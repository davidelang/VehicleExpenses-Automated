# paddle-models — tailor / opt input models (non-git pin)

| | |
|--|--|
| **Kind** | `source_kind = "files"` — no git clone |
| **Materialize** | `./third_party/fetch-deps ro paddle-models` |
| **Layout** | `src/armv8/`, `src/armv7/`, `src/x86_64/` |
| **Build** | none (`build = []`) |
| **Consumer** | `third_party/paddle/build` (auto tailor when subdir exists for arm ABIs) |

## Packs

| Subdir | Precision | Contents |
|--------|-----------|----------|
| `armv8` | HW fp16 OCR | prod-shaped `.nb` + tailor lists for arm64 library |
| `armv7` | **u8→fp32** (true v7; det uint8 out, rec float CTC) | Analytic input only — **no** `--quant_model` (that broke det heatmaps) |
| `x86_64` | float backbone | prod nbs + lists (library tailor experimental; pin default slim) |

Rebuild armv7 runtime + seed models:

```bash
./app/src/main/assets/paddle/scripts/optimize_armv7_prod_u8fp32_u8.sh
# then copy nbs into seed/armv7 and refresh libpin.toml sha256
```

App ships:

| ABI | Assets |
|-----|--------|
| arm64 / x86 | `app/src/main/assets/paddle/prod_u8fp16/` |
| armeabi-v7a | `app/src/main/assets/paddle/prod_u8fp32_u8/` |
