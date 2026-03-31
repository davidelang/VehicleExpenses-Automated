# Vehicle Expenses Automated — API Reference

This document lists every public function, class, and object in the repository.

## ExperimentAlignmentScreen.kt
- `ExperimentAlignmentScreen()` — Composable that runs the alignment experiment
- `runExperiment(vehicles, folder, context, onComplete)` — Core experiment logic that scans photos and generates the HTML report

## ImageAlignmentUtils.kt
- `ImageAlignmentUtils.alignImages(reference: Bitmap, query: Bitmap, minInliers: Int = 15)` — Aligns a query image to a reference image using ORB + homography

## OdometerOcrUtils.kt
- `OdometerOcrUtils.extractFromPhoto(photoPath: String, cropRect: RectF? = null)` — Main OCR entry point that runs ML Kit, Tesseract, and multi-stage OpenCV preprocessing

## PhotoAlignmentUtils.kt
- `PhotoAlignmentUtils.alignToReference(fillupBitmap: Bitmap, referenceCrop: Rect?)` — Stage-1 alignment for odometer crop

## ImageHashUtils.kt
- `ImageHashUtils.computeAverageHash(bitmap: Bitmap)` — 64-bit average hash
- `ImageHashUtils.hammingDistance(hash1: Long, hash2: Long)` — Hamming distance
- `ImageHashUtils.similarity(hash1: Long, hash2: Long)` — Similarity score 0.0–1.0
- `ImageHashUtils.computeHashFromFilePath(photoPath: String)` — Convenience method for file paths

## MainActivity.kt
- `MainActivity()` — Root activity with navigation drawer and NavHost (includes "experiment" route)

## QuickFillupScreen.kt
- `QuickFillupScreen(navController: NavHostController)` — Main fill-up screen
- `ControlsContent(...)` — Reusable controls (includes experiment button)

## Vehicle.kt
- `Vehicle(...)` — Data class with `referenceDashPhotoUrl`, crop rects, etc.

(Full list will be expanded after the experiment page is tested.)
