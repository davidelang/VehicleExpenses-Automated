---
type: implementation-reference
status: dynamic
ai_directive: "This is a downstream reference. It MUST be updated continuously to reflect the current state of the codebase. If you change a function or architecture described here, update this document in the same commit."
---

# Odometer Reading by Camera (Technical Workflow)

This document details the precise sequence of operations required to process a single dashboard image for odometer extraction, specifically using the high-performance Native/Mono pathway.

## 1. Image Capture & Preparation
- **Capture**: The image is retrieved from the camera as an ARGB `Bitmap`.
- **Conversion to Mono**: The image is converted directly to a 1-channel (luminance) `CV_8UC1` Mat using `NativeImageUtils.syncMatFromArgb`. 
    - *Note: This extraction isolates the Red channel (luminance) directly into native memory.*

## 2. Pre-Processing (Native/Mono Path)
- **80% Contrast Stretch**: A robust min-max normalization is performed.
    - **Histogram Analysis**: The intensity distribution is calculated using `Imgproc.calcHist`.
    - **Floor/Ceiling Mapping**: The 80th percentile is mapped to 0 (black), and the 98th percentile is mapped to 255 (white).
    - **Application**: The `Mat.convertTo` function applies the linear transformation `alpha * x + beta`.
- **Grayscale Normalization**: The resulting image is ensured to be 8-bit single-channel.

## 3. OCR Extraction (Paddle Raw Step)
- **Engine**: `NativePaddleEngine` (Mono variant).
- **No ARGB Buffers**: The `CV_8UC1` Mat is fed directly into the Paddle-Lite predictor's input tensor.
- **Dynamic Resize**: The native engine handles the input scaling internally to fit the model's dynamic input shape (`512x128` or `2048x2048` for detection).
- **Raw OCR**:
    - **Detection**: DBNet extracts text bounding boxes from the contrast-stretched mono image.
    - **Recognition**: The crops (still in mono) are passed to the recognition model (`rec_v3_mono` or `rec_numeric_mono`).
    - **Greedy Decoding**: CTC decoding retrieves the raw text string.

## 4. Post-Processing
- **Sanitization**: `OdometerOcrUtils.clean7SegmentDigits` maps common 7-segment misreads (e.g., `S` -> `5`, `G` -> `6`).
- **Validation**: The result is verified against the 4–7 digit requirement for odometers.
