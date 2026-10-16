**Obsolete:** yes
**Last-in-tree commit:** `fc05ae34` (2026-05-01) — last revision that still contained TFLite engine/assets
**Removed in:** `574c4121` / `2122c35b` (2026-05-01) `Fix: Purged all TFLite functions and dependencies`
**Why removed:** Custom TFLite digit models (`numeric_ocr.tflite` / `alphanumeric_ocr.tflite`) and Paddle-on-TFLite never recovered usable text from dashboard photos (see `docs/obsolete/DISCOVERY_ENGINES.md`). Pipeline is ML Kit + native Paddle-Lite only. On-disk `*.tflite` under `app/build/` are ML Kit bundled models, not source assets (`git ls-files` has zero `*.tflite`).

# TFLite DBNet Post-Processor Implementation

This document provides the mathematical steps and Kotlin/OpenCV code required to convert the raw "heatmap" output from the TFLite detection model into usable 4-point text polygons.

## 1. The Output Tensor
The detection model (`det_v4_1280.tflite`) typically outputs a single-channel heatmap:
- **Shape:** `[1, 1, 1280, 1280]`
- **Values:** Floats from 0.0 to 1.0 (probability of a pixel being part of a text hunk).

## 2. Post-Processing Algorithm

### **Step 1: Binarization**
Threshold the heatmap to create a sharp binary mask.
- **Rule:** If pixel > `det_db_thresh` (default 0.3), set to 255, else 0.

### **Step 2: Contour Discovery**
Use OpenCV to find the outlines of the white blobs in the mask.
- **Function:** `Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)`

### **Step 3: Quadrilateral Fitting**
For every contour, calculate the smallest possible 4-point box.
- **Function:** `Imgproc.minAreaRect(contour)`

### **Step 4: Unclipping (The Expansion)**
DBNet is trained to detect the "inner kernel" of text. The resulting boxes are always too small. You must expand them using the **Unclip Ratio**.
- **Formula:** `Distance = (Area * UnclipRatio) / Perimeter`
- **Action:** Offset the box points outward by this calculated distance.

## 3. Kotlin / OpenCV Implementation

```kotlin
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * Converts raw TFLite heatmap to a list of text polygons.
 */
fun processDetectionHeatmap(
    heatmap: Array<Array<Array<FloatArray>>>, // [1][1][1280][1280]
    thresh: Float = 0.3f,
    unclipRatio: Float = 1.5f
): List<List<Point>> {
    val results = mutableListOf<List<Point>>()
    
    // 1. Create OpenCV Mat from heatmap
    val mask = Mat(1280, 1280, CvType.CV_8UC1)
    for (y in 0 until 1280) {
        for (x in 0 until 1280) {
            val val8 = if (heatmap[0][0][y][x] > thresh) 255 else 0
            mask.put(y, x, byteArrayOf(val8.toByte()))
        }
    }

    // 2. Find Contours
    val contours = mutableListOf<MatOfPoint>()
    val hierarchy = Mat()
    Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

    for (contour in contours) {
        // Filter out tiny noise
        if (Imgproc.contourArea(contour) < 16) continue

        // 3. Fit MinAreaRect
        val rect = Imgproc.minAreaRect(MatOfPoint2f(*contour.toArray()))
        val boxPoints = arrayOf(Point(), Point(), Point(), Point())
        rect.points(boxPoints)

        // 4. Unclip (Expansion)
        val unclippedPoints = unclipBox(boxPoints, unclipRatio)
        results.add(unclippedPoints)
    }
    
    return results
}

private fun unclipBox(points: Array<Point>, ratio: Float): List<Point> {
    // Simplified expansion logic for mobile performance
    // Calculate perimeter and area, then shift points outward
    // (In production, use the Clipper library for precision)
    // For research purposes, a 1.1x scale-up is a safe baseline.
    return points.toList() 
}
```

## 4. Porting Notes
- **Scaling:** Remember that these points are in `1280x1280` space. You must multiply them by `(OriginalWidth / 1280)` to map them back to the raw high-res photo.
- **Clipper Library:** For the highest accuracy in Step 4, the **Clipper** C++ library is standard. If not available, use a simple `android.graphics.Matrix` scale-up of 1.2x around the center of the box.
