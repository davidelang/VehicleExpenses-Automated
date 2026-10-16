**Obsolete:** yes
**Last-in-tree commit:** `3ac422d6` (2026-05-01) `Fix: Restored Paddle initialization and corrected strategy labels`
**Removed in:** `9224a3f4` / `eab101e1` (2026-05-01) `Fix: Purged Tesseract and enabled native library extraction` (later leftover UI stubs `d2959aa8` 2026-07-14)
**Why removed:** Tesseract was replaced by native Paddle-Lite + ML Kit. Purge enabled native library extraction; remaining Tesseract UI stubs were deleted in dead-code phase 2. Current OCR engines: ML Kit and Paddle native only.

# Tesseract Crop Sweep: The "Perfect Binarization" Experiment

This document details the final, exhaustive local benchmark of Tesseract OCR on pre-extracted, high-resolution 7-segment gas pump crops to determine if it can serve as a viable secondary "tie-breaker" engine on Android.

## 1. Experimental Setup
We ran Tesseract against our `ocr_test_library` (which contains 18 high-resolution crops from 9 images). We swept across a matrix of configurations:
- **Heights:** 64px, 128px, 640px
- **Page Segmentation Modes (PSM):** 7 (Single line), 8 (Single word), 13 (Raw line)
- **Binarization Algorithms:** Raw (None), Grayscale, Otsu Threshold, Otsu Inverse, CLAHE + Otsu, Adaptive Mean, Adaptive Gaussian.
- **Whitelist:** `0123456789.`
- **Post-Processing:** Applied our proven `clean7SegmentDigits` logic.

## 2. Benchmark Results
Out of 18 test crops, the maximum number of correct matches achieved by **ANY** Tesseract configuration was **1**. 

*   **Best Tesseract Score:** 1/18 (5.6% accuracy)
*   **PaddleOCR Baseline Score:** 12/18 (66.7% accuracy) on the exact same dataset.

### Why Tesseract Fails on 7-Segment Crops:
Even with "perfect" tight crops and aggressive OpenCV binarization, Tesseract's traditional computer vision segmentation completely breaks down on 7-segment LCDs:
1.  **Segment Gaps:** Tesseract expects continuous strokes for characters. The physical gaps between the lit elements of a 7-segment digit cause Tesseract to read a single `8` as two small `o`s or multiple fragmented shapes, rejecting it entirely.
2.  **Glare Sensitivity:** Adaptive thresholding often highlighted the LCD background lattice or glare artifacts, which Tesseract interpreted as letters/punctuation, destroying the numeric sequence.

## 3. Final Verdict and Recommendation
**Tesseract is fundamentally incompatible with unconstrained 7-segment industrial displays.** No amount of preprocessing, scaling, or parameter tuning will bridge the architectural gap between its legacy blob-analysis and modern deep-learning models (like Paddle and ML Kit).

**Recommendation for the Application Engineer:** 
Tesseract should be fully **deprecated** and removed from the active OCR consensus loop for both Odometers and Gas Pumps. Relying on it even as a 5% weighted tie-breaker introduces more noise than signal. Focus all processing power on ML Kit and Native Paddle-Lite.
