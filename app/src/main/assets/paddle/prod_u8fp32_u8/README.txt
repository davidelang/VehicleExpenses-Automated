ARMv7 (armeabi-v7a) production models — true v7 head units
==========================================================
Path id: uint8_fp32_u8
  feed: uint8 greyscale (raw)
  mid:  fp32 (no ARM82_FP16 / no -march=armv8.2-a+fp16)
  det out:  uint8 heatmap via fp32_to_uint8
  rec out:  float CTC logits (no output calib)

Built with:
  app/src/main/assets/paddle/scripts/optimize_armv7_prod_u8fp32_u8.sh

Recipe (2026-08-04 fix):
  --analytic_input_quant=true --analytic_input_dtype=uint8
  det only: --output_calib_precision=uint8
  NEVER --quant_model / QUANT_INT8 on armv7 (all-zero heatmaps with light API)

Do not use armv8 prod_u8fp16 graphs on armv7 library (wrong precision kernels).
