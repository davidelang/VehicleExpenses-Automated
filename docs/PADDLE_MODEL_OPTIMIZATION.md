# Paddle-Lite Model Optimization Guide

This document outlines the definitive process for optimizing Paddle models (`.pdmodel`, `.pdiparams`) into Naive Buffer (`.nb`) files for use in the Android native engine, with a focus on **Dynamic Shape Support**.

## 1. Prerequisites

### Optimization Tool
Use the version-matched `opt` binary located in the research directory. Standard Paddle release binaries often lack the internal logic for dynamic shape configuration.

*   **Path:** `dev-ai-interaction/research/opt_linux_x86`
*   **Version:** Paddle-Lite 2.14rc

### Source Models
The source models must be in the **Combined** format:
*   `inference.pdmodel`: Model topology
*   `inference.pdiparams`: Model weights/parameters

## 2. Dynamic Shape Configuration

Dynamic shapes are **not** configured via command-line flags in this version. Instead, they are triggered by a specific environment variable that the `opt` tool reads during the optimization pass.

### The Magic Variable: `NNADAPTER_DYNAMIC_SHAPE_INFO`

The format for the variable is:
`"<tensor_name>:<min_shape>:<opt_shape>:<max_shape>"`

*   **tensor_name:** Usually `x` for detection and `x` or `image` for recognition.
*   **min_shape:** The absolute minimum resolution the model will accept (e.g., `1,3,128,128` for RGB or `1,1,128,128` for Monochrome).
*   **opt_shape:** The resolution the engine should pre-allocate memory for and tune kernels for (e.g., `1,3,1280,1280`).
*   **max_shape:** The absolute maximum resolution (e.g., `1,3,4000,4000`).

## 3. Monochrome Model Conversion

To handle 1-channel (monochrome) input files directly, the source model's first layer and weights must be surgically modified **before** optimization.

### Step 1: Weight Averaging
The first convolution layer typically expects 3 channels (RGB). We must average these weights into a single channel:
`new_weight = (R_weight + G_weight + B_weight) / 3`

### Step 2: Graph Modification
The model's input variable shape must be updated from `(-1, 3, H, W)` to `(-1, 1, H, W)`.

### Automation
Use the `dev-ai-interaction/research/convert_mono.py` script to automate this for Paddle inference models. It loads the model, averages the weights in the first `conv2d`, updates the program description, and saves the modified model.

## 4. Step-by-Step Instructions

### Step 1: Define the Range
Decide on your resolutions. Note that very large resolutions (like 4000px) will significantly increase the memory footprint on the device.

### Step 2: Run the Optimization
Execute the command while exporting the environment variable.

```bash
# Example: 128px to 4000px range, optimized for 1280px
export NNADAPTER_DYNAMIC_SHAPE_INFO="x:1,3,128,128:1,3,1280,1280:1,3,4000,4000"

./dev-ai-interaction/research/opt_linux_x86 \
  --model_file=path/to/inference.pdmodel \
  --param_file=path/to/inference.pdiparams \
  --optimize_out=path/to/output_name \
  --valid_targets=arm
```

### Step 3: Deployment
The tool will produce a file named `output_name.nb`. Move this to the app assets:
`app/src/main/assets/paddle/`

## 4. Troubleshooting

| Symptom | Cause | Solution |
| :--- | :--- | :--- |
| `ERROR: unknown command line flag 'dynamic_shape_config'` | Wrong `opt` version or syntax. | Use the `NNADAPTER_DYNAMIC_SHAPE_INFO` environment variable method instead. |
| Model crashes on device with `SIGSEGV` | Input resolution exceeded `max_shape`. | Re-optimize with a larger max shape or scale the input down before inference. |
| Output `.nb` file is 0 bytes | Initialization failure. | Check that the input tensor name (e.g., `x`) is correct using `strings` or the Python inspection script. |

## 5. Reference Examples

### High-Resolution Detection (v4)
```bash
export NNADAPTER_DYNAMIC_SHAPE_INFO="x:1,3,128,128:1,3,1280,1280:1,3,4000,4000"
./dev-ai-interaction/research/opt_linux_x86 --model_file=... --param_file=... --optimize_out=det_v4_4000 --valid_targets=arm
```

### Flexible Odometer Recognition (v3)
```bash
export NNADAPTER_DYNAMIC_SHAPE_INFO="x:1,3,48,32:1,3,48,320:1,3,48,1280"
./dev-ai-interaction/research/opt_linux_x86 --model_file=... --param_file=... --optimize_out=rec_v3_flex --valid_targets=arm
```
