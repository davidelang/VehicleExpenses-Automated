# Obsolete: Deskew Convergence Algorithms (B, C, D)

This document records the design, implementation, and performance characteristics of the Java/Kotlin-based deskew consensus algorithms (Algorithms B, C, and D) before they were decommissioned in favor of zero-copy native C++ consensus (Algorithm E) and baseline ML Kit landmarks (Algorithm A).

---

## 1. Algorithm Overview

### Algorithm B: Standard Kotlin Consensus
* **Weighting Scheme:** Uniform average.
* **Mechanism:** Computes the simple arithmetic mean of the raw rotation angles of all bounding boxes detected by the Paddle OCR layout analysis.
* **Formula:**
  $$\theta = \frac{1}{N} \sum_{i=1}^N \theta_i$$
* **Failure Mode:** Vulnerable to "zero-degree swamping" where large numbers of horizontal or noisy bounding boxes skew the average towards $0.0^\circ$, failing to correct actual odometer tilt.

### Algorithm C: Area-Weighted Kotlin Consensus
* **Weighting Scheme:** Area-squared weighting.
* **Mechanism:** To filter out small noise fragments and prioritize text regions representing digit boxes, each detected box angle is weighted by the square of its pixel area.
* **Formula:**
  $$w_i = (\text{width}_i \times \text{height}_i)^2$$
  $$\theta = \frac{\sum w_i \theta_i}{\sum w_i}$$
* **Impact:** Reduced zero-degree swamping significantly, resolving several trailing character recognition errors on tilted dashboards.

### Algorithm D: Confidence-Area-Weighted Kotlin Consensus
* **Weighting Scheme:** Confidence multiplied by Area-squared weighting.
* **Mechanism:** Extends Algorithm C by incorporating the model's prediction confidence score for each box to further discount low-confidence background artifacts.
* **Formula:**
  $$w_i = (\text{width}_i \times \text{height}_i)^2 \times \text{confidence}_i$$
  $$\theta = \frac{\sum w_i \theta_i}{\sum w_i}$$
* **Impact:** Delivered peak Kotlin accuracy (matching Algorithm C at 93.8%), but suffered from JNI-to-JVM memory boundary bottlenecks.

---

## 2. Historical Performance & Stage Progression
Below is the final stage progression analysis computed across the **145 ground-truth images** before decommissioning:

| Engine |  Raw | S-80% (G/L) | S-78% (G/L) |
| :--- | :--- | :---: | :---: |
| Set A ML [set_a]     | 75/+2 | 78/+3 (+17 -14) | 77/+3 (+14 -15) |
| Set A Paddle [set_a] | 98/+1 | 130/+5 (+38 -6) | 130/+5 (+0 -0) |
| Set B ML [set_b]     | 78/+2 | 77/+3 (+16 -17) | 79/+2 (+17 -15) |
| Set B Paddle [set_b] | 97/+0 | 132/+3 (+40 -5) | 132/+3 (+0 -0) |
| Set C ML [set_c]     | 82/+2 | 75/+4 (+14 -21) | 77/+1 (+18 -16) |
| Set C Paddle [set_c] | 94/+0 | 136/+3 (+44 -2) | 135/+3 (+0 -1) |
| Set D ML [set_d]     | 82/+2 | 75/+4 (+14 -21) | 77/+1 (+18 -16) |
| **Set D Paddle [set_d]** | **94/+0** | **136/+3 (+44 -2)** | **135/+3 (+0 -1)** |
| Set E ML [set_e]     | 73/+3 | 78/+4 (+18 -13) | 82/+1 (+16 -12) |
| **Set E Paddle [set_e]** | **96/+0** | **134/+3 (+42 -4)** | **133/+3 (+0 -1)** |

### Note on Suffix Accuracy (`/+N`)
The `/+N` suffix represents "extra-trailing character" errors. These are cases where:
1. The baseline odometer reading is **100% correct** (e.g. read `164512` correctly).
2. A trailing digit, border artifact, or space was erroneously appended to the end of the text string (e.g. reading it as `1645121` or `164512 0`).

---

## 3. Reasons for Decommissioning
1. **JVM Heap & GC Pressures:** Processing box arrays requires allocating multiple arrays of float arrays and instantiating JVM `TextBlock` and `DetectionBox` objects on every frame, generating significant garbage collection churn.
2. **JNI Copy overhead:** To run layout analysis in Kotlin, the massive 512x512 heatmap tensor (`262,144` floats) must be marshalled back to Java space on every frame via `outputTensor.floatData`, consuming **~5–10ms** of JNI array copy time.
3. **Native Convergence (Algorithm E):** By porting the connected components and consensus logic directly to C++ (`NativeImageUtils.cpp`) and feeding it the raw native `Tensor` pointer directly, we achieve the exact same mathematical alignment results in **zero-copy JNI time**, bypassing the JVM entirely.
