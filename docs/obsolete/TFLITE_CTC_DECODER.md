**Obsolete:** yes
**Last-in-tree commit:** `fc05ae34` (2026-05-01) — last revision that still contained TFLite engine/assets
**Removed in:** `574c4121` / `2122c35b` (2026-05-01) `Fix: Purged all TFLite functions and dependencies`
**Why removed:** Custom TFLite digit models (`numeric_ocr.tflite` / `alphanumeric_ocr.tflite`) and Paddle-on-TFLite never recovered usable text from dashboard photos (see `docs/obsolete/DISCOVERY_ENGINES.md`). Pipeline is ML Kit + native Paddle-Lite only. On-disk `*.tflite` under `app/build/` are ML Kit bundled models, not source assets (`git ls-files` has zero `*.tflite`).

# TFLite CTC Greedy Decoder Implementation

This document details how to implement a Connectionist Temporal Classification (CTC) Greedy Decoder in Kotlin for Android. This is required if the application engineer attempts to use the converted Paddle-to-TFLite recognition models instead of the Native Paddle-Lite libraries.

## 1. The Problem
When you run inference on a TFLite OCR model (like `model_fixed_640_float32.tflite`), it does not return a parsed string. Instead, it returns a 3D float array representing the probability distribution of characters at each "time step" across the image width.

**Typical Output Shape:** `[1, TimeSteps, NumClasses]`
- `1`: Batch size.
- `TimeSteps`: The number of vertical slices the model analyzed (e.g., 80).
- `NumClasses`: The size of your dictionary + 1 (the extra class is the "blank" or "spacer" token).

## 2. The Algorithm (Greedy Search)
The most efficient way to decode this matrix on a mobile device is the "Greedy" approach (Best Path). The rules are simple:
1. For every time step, find the index with the highest probability.
2. If the index is the "blank" token, ignore it.
3. If the index is the *same* as the previous time step's index, ignore it (collapse duplicates).
4. Otherwise, map the index to the character in your dictionary and append it to the result string.

## 3. Kotlin Implementation

Below is the standard, high-performance community approach for decoding the TFLite matrix in Android:

```kotlin
/**
 * Decodes the raw 3D float array output from a TFLite OCR model into a string.
 *
 * @param logits The output array from the TFLite interpreter. Shape: [1][timeSteps][numClasses]
 * @param dictionary The list of characters the model was trained on.
 * @return The decoded string.
 */
fun decodeCtcGreedy(logits: Array<Array<FloatArray>>, dictionary: List<String>): String {
    val result = java.lang.StringBuilder()
    
    // Assuming batch size is 1, so we access logits[0]
    val sequence = logits[0] 
    val timeSteps = sequence.size
    val numClasses = sequence[0].size
    
    // In PaddleOCR conversions, the 'blank' token is typically at index 0.
    // (Note: In some standard TensorFlow models, it might be the last index: numClasses - 1).
    // You must verify this based on your specific model export!
    val blankIndex = 0 
    
    var lastIndex = -1

    for (t in 0 until timeSteps) {
        var maxIndex = 0
        var maxVal = sequence[t][0]
        
        // Find the most probable character class at this time step
        for (c in 1 until numClasses) {
            if (sequence[t][c] > maxVal) {
                maxVal = sequence[t][c]
                maxIndex = c
            }
        }

        // Apply CTC rules: Ignore blanks and collapse consecutive duplicates
        if (maxIndex != blankIndex && maxIndex != lastIndex) {
            // Adjust the index to map to the dictionary correctly.
            // If blank is at 0, dictionary index is usually maxIndex - 1.
            val dictIndex = maxIndex - 1 
            
            if (dictIndex >= 0 && dictIndex < dictionary.size) {
                result.append(dictionary[dictIndex])
            }
        }
        
        lastIndex = maxIndex
    }

    return result.toString()
}
```

## 4. Integration Notes for the Application Engineer
If implementing the `NativeTfliteEngine.kt` fallback:
1. Ensure your output buffer is properly sized before calling `interpreter.run()`. E.g., `Array(1) { Array(80) { FloatArray(97) } }` for a model with 80 timesteps and 96 characters + 1 blank.
2. Pass the filled output buffer and your loaded `en_dict.txt` lines to the `decodeCtcGreedy` function above.
3. Pass the resulting string through the `clean7SegmentDigits` helper to fix standard OCR misreads before final use.
