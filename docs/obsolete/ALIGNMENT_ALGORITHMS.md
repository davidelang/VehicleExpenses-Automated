# Obsolete Alignment Algorithms

This document archives the history and rationale for removing various image alignment strategies used to geometrically warp a dashboard query photo to match a reference photo.

## 1. Feature (ORB / Homography)
**Last Used Commit:** c51809dc
**Description:** `ImageAlignmentUtils.alignImages()`. Used OpenCV's ORB feature detector to find hundreds of keypoints (corners, edges) in both images. It then calculated an affine transformation matrix (`estimateAffinePartial2D` or previously `findHomography`) to warp the query image.
**Reason for Removal:** Deprecated in favor of **Anchor-Triangulation**. The ORB algorithm frequently locked onto glare, dust, or the steering wheel instead of the dashboard text. When using full Homography (8-DOF), this resulted in extreme perspective distortion ("wedging"). Even when constrained to Affine (4-DOF), the text alignment was often misaligned by several pixels, causing the subsequent odometer crops to miss the digits entirely.

## 2. Hub (HoughCircles)
**Last Used Commit:** c51809dc
**Description:** `ImageAlignmentUtils.hubAlign()`. A mechanical dial-finding algorithm that used `Imgproc.HoughCircles` to locate the physical center of the analog speedometer needle hub in both images.
**Reason for Removal:** While mathematically elegant and immune to perspective wedging, it was too brittle. It failed completely on digital dashboards (which lack physical hubs) and often failed on analog dashboards if the lighting cast a harsh shadow over the needle base. Text-based geometric alignment (Anchor-Triangulation) proved significantly more flexible, reliable, and universally applicable across all dashboard types.
