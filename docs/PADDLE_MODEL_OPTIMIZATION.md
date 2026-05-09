# Paddle-Lite Model Optimization Guide

This document outlines the definitive process for optimizing Paddle models (`.pdmodel`, `.pdiparams`) into Naive Buffer (`.nb`) files for use in the Android native engine, with a focus on **Dynamic Shape Support** and **Monochrome (1-channel)** conversion.

## 1. Prerequisites

### Optimization Tool
Use the version-matched `opt` binary located in the research directory. Standard Paddle release binaries often lack the internal logic for dynamic shape configuration.

*   **Path:** `dev-ai-interaction/research/opt_linux_x86`
*   **Version:** Paddle-Lite 2.14rc

### Python Environment
For monochrome conversion, a specific Paddle environment is required.
*   **Path:** `~/miniconda3/envs/paddle_env_v3/bin/python`

## 2. Model Management Scripts
The project provides unified scripts to manage the conversion and optimization process. These are stored in:
`app/src/main/assets/paddle/scripts/`

### `convert_mono.py`
Automates the weight averaging and graph modification required to turn a 3-channel ARGB model into a 1-channel Monochrome model.
*   **Usage:** Run from `dev-ai-interaction/research/` using the miniconda python.
*   **Action:** Reads source models from `models/` and saves modified models to `models/*_mono/`.

### `optimize_models.sh`
The primary optimization engine. It handles both ARGB and Monochrome models, applying the correct dynamic shape configurations.
*   **Usage:** Run from `dev-ai-interaction/research/`.
*   **Action:** Optimizes models for ARMv7, ARMv8, and x86_64 targets and deploys the resulting `.nb` files directly to `app/src/main/assets/paddle/`.

## 3. Dynamic Shape Configuration

Dynamic shapes are configured via the `NNADAPTER_DYNAMIC_SHAPE_INFO` environment variable, which the `opt` tool reads during the optimization pass.

### The Magic Variable Format
`"<tensor_name>:<min_shape>:<opt_shape>:<max_shape>"`

*   **tensor_name:** Usually `x` for both detection and recognition models.
*   **min_shape:** The absolute minimum resolution the model will accept.
*   **opt_shape:** The resolution the engine should pre-allocate memory for and tune kernels for (the "sweet spot").
*   **max_shape:** The absolute maximum resolution.

### Reference Table: Configuration Strings

| Model Type | Mode | Configuration String (`NNADAPTER_DYNAMIC_SHAPE_INFO`) |
| :--- | :--- | :--- |
| **Detection** | ARGB | `x:1,3,128,128:1,3,1280,1280:1,3,4000,4000` |
| **Detection** | Mono | `x:1,1,128,128:1,1,1280,1280:1,1,4000,4000` |
| **Rec V3** | ARGB | `x:1,3,48,32:1,3,48,320:1,3,48,1280` |
| **Rec V3** | Mono | `x:1,1,48,32:1,1,48,320:1,1,48,1280` |
| **Numeric V2** | ARGB | `x:1,3,32,32:1,3,32,320:1,3,32,1024` |
| **Numeric V2** | Mono | `x:1,1,32,32:1,1,32,320:1,1,32,1024` |

## 4. Monochrome Model Conversion

To handle 1-channel (monochrome) input files directly, the source model's first layer and weights must be surgically modified **before** optimization using `convert_mono.py`.

### Step 1: Weight Averaging
The first convolution layer's weights are averaged across the RGB channels into a single channel:
`new_weight = (R_weight + G_weight + B_weight) / 3`

### Step 2: Graph Modification
The model's input variable shape is updated from `(-1, 3, H, W)` to `(-1, 1, H, W)`.

## 5. Deployment & Backups
After running `optimize_models.sh`, the models are automatically deployed to the app assets. It is recommended to keep a backup in the sandbox:
`dev-ai-interaction/research/models/mono_backups/`

## 6. Troubleshooting

| Symptom | Cause | Solution |
| :--- | :--- | :--- |
| `ERROR: unknown command line flag 'dynamic_shape_config'` | Wrong `opt` version. | Use the `NNADAPTER_DYNAMIC_SHAPE_INFO` env var method. |
| Model crashes with `SIGSEGV` | Input resolution exceeded `max_shape`. | Re-optimize with a larger max shape or scale input down. |
| Output `.nb` file is 0 bytes | Initialization failure. | Verify input tensor name (usually `x`). |
