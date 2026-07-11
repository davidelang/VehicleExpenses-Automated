# Pull Request (Paddle-Lite / paddle-build): int8 + uint8 + fp16 production support

**Scope:** `dev-ai-interaction/paddle-build` patches and build scripts (upstream Paddle-Lite v2.14 line + local INT8 fork).  
**Not:** Android app harness.

## Summary
Patches and tooling that make production graphs work: **uint8 (and int8) analytic input**, **fp16 compute on arm**, **fp32→uint8 output calib**, and **model-tailored arm64 builds**.

## Patch sets
### `patches/` (existing upstream-facing stack)
Build/system mobile x86, packaging, prior stability fixes (see `docs/specs/PADDLE_PR_DESCRIPTIONS.md` PR1/PR2).

### `patches-int8/` (this effort)
- `analytic_input_quant_pass` — feed kInt8 xor-128 or **kUInt8 raw**; prefers *to_fp16 when fp16 places present
- Output calib pass + `fp32_to_uint8` arm/x86 kernels
- arm math `uint8_to_fp16` / type_trans
- opt flags: `--analytic_input_dtype`, `--output_calib_precision`
- JNI / MobileConfig hooks as needed for keep_quantized_weights

### Tailor tooling (local)
- `build_tailored_arm64.sh` — `LITE_BUILD_TAILOR` + strip for prod u8fp16 models
- `patch_tailor_depthwise_common.py` — FP16 depthwise link fix
- x86 true tailor **deferred** (empty `USE_LITE_KERNEL` / missing for_strip objects)

## Suggested upstream split
1. Input quant pass (int8 + uint8 dtype) + opt flags  
2. Output calib uint8 + kernels  
3. (Optional) tailor/cmake depthwise_common copy for arm FP16 strip builds  

## Validation
- Host opt record lists for armv8 prod models  
- Android phone: tailored SO loads prod nbs; multi-path non-prod fails as expected  
- Emulator: full-kernel slim SO + same prod nbs  

## References
- App product path: branch `int8-paddle-processing`  
- Research notes under `dev-ai-interaction/research/prec-campaign/`
