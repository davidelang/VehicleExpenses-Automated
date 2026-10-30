# paddle-models — tailor / opt input models (non-git pin)

| | |
|--|--|
| **Kind** | `source_kind = "files"` — no git clone |
| **Materialize** | `./third_party/fetch-deps ro paddle-models` |
| **Layout** | `src/armv8/`, `src/armv7/`, `src/x86_64/` |
| **Build** | `./build` (production `.nb` + tailor lists from `seed/weights` and the `paddle-opt` pin) |
| **Consumer** | `third_party/paddle/build` (tailor lists), app CMake (`artifact/app/<arch>/*.nb` via `.incbin`) |

## Packs

| Subdir | Precision | Contents |
|--------|-----------|----------|
| `armv8` | HW fp16 OCR | prod-shaped `.nb` + tailor lists for arm64 library |
| `armv7` | **u8→fp32** (true v7; det uint8 out, rec float CTC); tailor lists rebuilt to `artifact/tailor/armv7` | Analytic input only — **no** `--quant_model` (that broke det heatmaps) |
| `x86_64` | float backbone | prod nbs + lists (library tailor experimental; pin default slim) |

Production models are rebuilt only by `./build` (see below); the old app-asset `optimize_*` scripts were retired.

App ships (embedded by `app/src/main/cpp/CMakeLists.txt` via `.incbin`; there are no `.nb` copies under `app/`):

| ABI | Pin file | Embedded asset key |
|-----|----------|--------------------|
| arm64-v8a | `artifact/app/armv8/{det,rec_v3,rec_numeric}_armv8.nb` | `paddle/prod_u8fp16/` |
| armeabi-v7a | `artifact/app/armv7/{det,rec_v3,rec_numeric}_armv7.nb` | `paddle/prod_u8fp32_u8/` |
| x86_64 | `artifact/app/x86_64/{det,rec_v3,rec_numeric}_x86_64.nb` | `paddle/prod_u8fp32_u8/` |

`artifact/host/prod_u8fp{16,32}_*.nb` are host-only comparison packs (not shipped).

## Production model rebuild

Inputs (pinned in `libpin.toml` `[[source]]`, not a separate hash list):

- `seed/weights/det`, `seed/weights/rec_v3`, `seed/weights/rec_numeric` (pdmodel + pdiparams)
- opt from the `paddle-opt` pin: `third_party/paddle-opt/artifact/linux-x86_64/opt` (`url = "file:../paddle-opt/artifact/linux-x86_64/opt"`)
- `convert_mono.py` in this directory (sum-of-weights; never `--legacy-mean`)

Order: (1) `./third_party/fetch-deps build --record paddle-opt` → (2) `./third_party/fetch-deps build paddle-models` (15 production `.nb` plus armv8 and armv7 kernel lists) → (3) `./third_party/fetch-deps build paddle` (arm64 library from `artifact/tailor/armv8`, armv7 from `artifact/tailor/armv7`).

Command:

```bash
./third_party/fetch-deps ro paddle-models
./third_party/fetch-deps build paddle-models
```

`./build` writes `src/out/app/<abi>/*.nb`, `src/out/host/prod_u8fp{32,16}_*.nb`, `src/out/tailor/armv8/.tailored_*_list`, and `src/out/tailor/armv7/.tailored_*_list`. get-artifacts copies those to `artifact/`. Expected `.nb` sha256 values are the `[[artifact]]` rows. Both ABIs are merged by one function (`merge_lists`). The ops lists are the `sort -u` union of the three app opt runs (fp16 for armv8, fp32 for armv7); the kernel lists are the pair-preserving first-seen union (det, rec_v3, rec_numeric) of `.tailored_kernels_list` / `.tailored_kernels_source_list`, failing on blank lines, an empty-precision `,kARM,,` kernel line, unequal pair counts, or a kernel paired with two source lines.

Test: `third_party/paddle-models/tests/verify-rebuild` (ro, build twice, sha256 of the 15 `.nb` artifacts and the 8 tailor-list artifacts, 4 per ABI, against `libpin.toml`, then the first-conv weight check). Seed keeps `seed/weights` and the four `seed/x86_64` tailor lists.
