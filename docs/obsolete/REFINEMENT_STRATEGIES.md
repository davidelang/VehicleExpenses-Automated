# Obsolete: Legacy Refinement Strategies

## Overview
This document records the legacy OCR refinement strategies that were used to improve odometer detection before the implementation of the high-performance iterative engines. These strategies relied on a single-pass refinement loop with various image pre-processing stages (Bilateral Filtering, Contrast Stretching, Adaptive Thresholding).

## Last Known Functional State
The code for these strategies was last active and verified at:
**Git HEAD:** `cdfd1ac040d7bf81feaf6b46724e1b7bbd934269`

## Superseded By
- **Iterative Engines:** `runMLKitIterative`, `runPaddleValleyMonoIterative`.
- **Architecture:** Transitioned from `Bitmap`-based single-pass refinement to `MemoryBridge`-based multi-threshold iterative recognition.

## Legacy Strategies
- **Paddle V3 Valley:** A DBNet-based text discovery followed by "Valley" expansion logic and Paddle recognition.
- **Paddle V3 Valley Mono:** Grayscale-optimized version of the Valley strategy.
- **ML Kit:** Standard ML Kit Latin recognizer running on a cropped odometer ROI.
- **ML Kit Mono:** Grayscale-optimized standard ML Kit recognition.

## Reasons for Obsolescence
1. **Performance:** The legacy single-pass approach was significantly slower and less accurate than the iterative multi-threshold pipeline.
2. **Stability:** The reliance on `ALPHA_8` Bitmaps and hardware-mapped memory frequently caused `SIGSEGV` and `SIGILL` crashes in Android's HWUI layer.
3. **Accuracy:** The iterative engines provide 48+ data points per image, enabling robust consensus voting which the single-pass strategies could not match.
