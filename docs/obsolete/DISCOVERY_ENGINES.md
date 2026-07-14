# Obsolete Discovery Engines

This document archives the history and rationale for removing several discovery engines from the `VehicleExpenses-Automated` OCR pipeline.

## 1. TFLite & Paddle-on-TFLite
**Last Used Commit:** ~v45
**Description:** Custom, lightweight digit recognizers (`numeric_ocr.tflite` and `alphanumeric_ocr.tflite`) run via the Android TFLite interpreter. We also attempted to run PaddleOCR models converted to TFLite format.
**Reason for Removal:** Despite multiple iterations, host-side simulation, tensor reshaping, and dictionary calibration (e.g., Row-Major layout, 1-based dictionary, CTC Blank mapping), we were never able to get them to recover usable, reliable text from real-world dashboard images. The technical overhead of maintaining the C++ buffers and memory mapping was not justified given the zero-accuracy output.

## 2. Paddle-Lite (Native)
**Last Used Commit:** `c51809dc` (`obsolete-DISCOVERY_ENGINES`)
**Description:** A native C++ deployment of PaddleOCR using the `libpaddle_lite_jni.so` library. It was invoked via the `NativePaddleEngine` wrapper.
**Reason for Removal:** While the engine executed successfully, it struggled to reliably match text compared to ML Kit. It frequently missed critical punctuation (like the decimal in `342.5` or the slash in `km/h`) or entirely ignored faint text that ML Kit captured effortlessly. It was unregistered from the active discovery pipelines, though the `NativePaddleEngine` class and native libraries were left in the codebase for potential future research.

## 3. Paddle-ML-Hybrid
**Last Used Commit:** `c51809dc` (`obsolete-DISCOVERY_ENGINES`)
**Description:** An experimental engine (`HybridOcrEngine`) that attempted to use ML Kit for fast text localization (bounding boxes) and then fed those specific crops into a heavily constrained Paddle-Lite instance for text recognition.
**Reason for Removal:** While it performed better than pure Paddle-Lite, the results were still inconsistent. The ultimate nail in the coffin was the realization that pure ML Kit, when combined with the ability for users to add **Manual Uniqueness Landmarks** (invisible 0,0,0,0 text fields), proved far superior, vastly more reliable, and significantly faster in practice. The hybrid complexity was therefore stripped from the pipeline.
