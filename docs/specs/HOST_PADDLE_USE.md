---
type: intent-spec
status: locked
ai_directive: "This is an upstream specification for using Paddle on the host. DO NOT modify this document to match the codebase. If the code deviates from this spec, the code is wrong. Modifications to this file require a dedicated 'Strategy' turn and explicit user approval."
---

# Host-Side Paddle Usage & Testing

This document outlines the process for using PaddleOCR on the host for diagnostics, image evaluation, and model optimization.

## 1. Paddle Host-Side Tooling

### `paddle_benchmark` (Legacy/Reference)
The project utilizes the Paddle-Lite `opt` and benchmark tools on the host to evaluate model performance and stability before deployment to Android.

*   **Benchmark Binary:** Located in the research sandbox (e.g., `dev-ai-interaction/research/benchmark_bin`).
*   **Purpose:** Measures inference latency and memory consumption across different thread counts and power modes.

## 2. Model Optimization (The `opt` tool)

The primary host-side task is optimizing Paddle models into Naive Buffer (`.nb`) files.

*   **Tool:** `dev-ai-interaction/research/opt_linux_x86`
*   **Version:** Paddle-Lite 2.14rc
*   **Python:** `~/miniconda3/envs/paddle_env_v3/bin/python` (Required for monochrome conversion).

### Dynamic Shape Support
Dynamic shapes MUST be configured via the `NNADAPTER_DYNAMIC_SHAPE_INFO` environment variable during the optimization pass.

**Format:** `"<tensor_name>:<min_shape>:<opt_shape>:<max_shape>"`

| Model Type | Mode | Configuration String (`NNADAPTER_DYNAMIC_SHAPE_INFO`) |
| :--- | :--- | :--- |
| **Detection** | ARGB | `x:1,3,128,128:1,3,1280,1280:1,3,4000,4000` |
| **Detection** | Mono | `x:1,1,128,128:1,1,1280,1280:1,1,4000,4000` |
| **Rec V3** | ARGB | `x:1,3,48,32:1,3,48,320:1,3,48,1280` |
| **Rec V3** | Mono | `x:1,1,48,32:1,1,48,320:1,1,48,1280` |

## 3. Automation Scripts
Standard host-side scripts in `app/src/main/assets/paddle/scripts/`:
*   `convert_mono.py`: Weight averaging and graph modification for 1-channel models.
*   `optimize_models.sh`: Orchestrates the `opt` tool for all targets.
*   `optimize_mono_int8_models.sh`: INT8 dynamic quant (`--quant_model=true --quant_type=QUANT_INT8`) + analytic input pass; outputs `*_int8_*.nb` (set `OPT_TOOL` to host `opt_linux_x86_int8` from paddle-build output).

## 4. UINT8 → INT8 Runtime Contract (App)

**No float mean/std at runtime.** Single-op remap per pixel:

```
int8_t q = static_cast<int8_t>(b ^ 128);  // 0→-128, 128→0, 255→+127
```

| Path | Pattern |
|------|---------|
| Detection tiers | `sharedTierInt8Buffers[scale]` + `NativeImageUtils.bindInputInt8` |
| BufferSet det/rec | `quantizeMonoInputToScratch()` (p→s, no flip) → bind `.s.raw` |
| Full-res 12 MP | Tier `4000` or `bufferSetA` p→s |

Model conversion must bake the same signed-byte contract via `analytic_input_quant_pass` during `opt`.
