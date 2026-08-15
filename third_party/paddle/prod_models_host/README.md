# Host-only product nbs (not in any ABI APK)

Leftover ABI/pack combinations after flavor split. Used by qemu/host compare scripts.

| File | Was |
|------|-----|
| `prod_u8fp16_*_x86_64.nb` | `assets/paddle/prod_u8fp16/` (emu now ships `prod_u8fp32_u8`) |
| `prod_u8fp32_*_armv8.nb` | `assets/paddle/prod_u8fp32_u8/` (phone ships `prod_u8fp16`) |
