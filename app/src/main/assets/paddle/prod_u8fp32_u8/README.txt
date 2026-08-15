uint8 → fp32 mid-graph product models
=====================================
Path id: uint8_fp32_u8
  feed: uint8 greyscale (raw)
  mid:  fp32 (no --enable_fp16)
  det out:  uint8 heatmap via output_calib_precision=uint8
  rec out:  float CTC logits (no output calib)

Files:
  *_armv7.nb   — true ARMv7 head units (ship)
  *_armv8.nb   — arm64-v8a diagnostic (needs dual-kernel arm64 SO; FP16-only tailor aborts)
  *_x86_64.nb  — emulator diagnostic (x86 slim SO already has fp32 kernels)

Built with:
  scripts/optimize_armv7_prod_u8fp32_u8.sh
  scripts/optimize_armv8_prod_u8fp32_u8.sh

Recipe:
  --analytic_input_quant=true --analytic_input_dtype=uint8
  --enable_fp16=false
  det only: --output_calib_precision=uint8
  NEVER --quant_model / QUANT_INT8 (all-zero heatmaps with light API)

Default phone ship path remains prod_u8fp16/*_armv8.nb (HW fp16).
arm64 temporarily points at this dir for precision A/B (NativePaddleEngine DIAG).
