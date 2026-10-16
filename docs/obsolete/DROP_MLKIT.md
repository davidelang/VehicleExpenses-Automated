# Obsolete: ML Kit text recognition dependency

**Status:** Removed from the Gradle graph and all Kotlin call sites. Production Manage Vehicles, Quick Fill, batch dash, and alignment/pump experiments use Paddle for detection and recognition. Vehicle landmark JSON is still stored under the key **`"ML Kit"`** (label only). Historical iterative OCR steps: [MLKIT_ITERATIVE_ALGORITHM.md](MLKIT_ITERATIVE_ALGORITHM.md).

**Annotated tag (peel the last tree that still depended on `text-recognition:16.0.1`):** `obsolete-mlkit-text-recognition`  
**Commit (tag peel):** `9c8d53139c732a5e982c47652420ef544cadf5a0`  
**Subject:** `# Quick Fill and batch dash use paddle seed 0.40`

## Why

ML Kit was used for text-angle deskew and landmark discovery in retired experiment paths. Paddle now covers deskew angles (`calculateAverageTextAngle` paddle-optimized), seed discovery (`productDetLandmarks2048`), and pump/odo recognition. Dropping the dependency shrinks the arm64 debug APK (see `dev-ai-interaction/research/mlkit-apk-size-20261014.md`).

## Recovery

```bash
git rev-parse obsolete-mlkit-text-recognition^{}
git show obsolete-mlkit-text-recognition:app/build.gradle.kts | rg text-recognition
```

Do not move `works`, `deployed`, or `builds` when creating or updating this tag.
