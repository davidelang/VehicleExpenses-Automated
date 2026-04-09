# AI Developer Guide - Vehicle Expenses Automated

## Overview
This codebase is focused on automating the entry of vehicle expenses by analyzing photos of dashboards. The core challenge is identifying the vehicle and extracting the odometer reading accurately under variable lighting and framing.

## Core Components

### 1. The Alignment Experiment (`ExperimentAlignmentScreen.kt`)
This is the research harness. it processes a directory of images from an emulated device and runs them through multiple identification and alignment strategies.
- **Reports:** Generates HTML reports (split by file size) and a comprehensive `alignment_results.json`.
- **JSON Format:** Includes `algorithm_metrics` (ms/score) and `veto_details`.

### 2. Vehicle Matching (`ImageAlignmentUtils.kt`)
Uses 5-6 algorithms plus a weighted Consensus:
- **Histogram/Embedding:** Text density and vector similarity. (Strongest performers)
- **ARG:** Weighted word matching.
- **Anchor Match:** Searches for "Golden Anchors" (discovered unique words).
- **Veto Logic:** Hard vetoes (-1.0) applied if distinctive landmarks for other vehicles are found in the query.

### 3. Image Alignment
- **ORB Affine:** Feature-based 4-DOF transform.
- **Hub Mechanical:** Uses `HoughCircles` to find the speedometer hub. Provides stable translation/scale without distortion.

### 4. OCR Engines (`OdometerOcrUtils.kt`)
- **Tesseract:** Default engine with specialized pre-processing (Bilateral, Otsu). Uses numeric-only whitelists for odometer crops.
- **ML Kit:** Advanced engine used for full-image landmark discovery. Robust against tilt/blur.
- **TFLite:** Custom lightweight digit recognizer (`numeric_ocr.tflite`) for mechanical digits.

## Research in Progress
- **PaddleOCR:** Benchmarking locally in `dev-ai-interaction/research` using a Python 3.12 Conda environment.
- **Speedometer Needle:** Using `HoughLinesP` to detect needles for more precise rotational alignment.

## Troubleshooting
- **Missing Images in Tagger:** Ensure all report parts are downloaded and parsed. Base64 images are embedded in the HTML.
- **Alignment Abandoned:** Check the Affine sanity check in `ImageAlignmentUtils`. If the scale is too extreme, the transform is rejected to prevent garbage crops.
