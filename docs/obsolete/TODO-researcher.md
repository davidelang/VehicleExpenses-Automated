**Obsolete:** yes
**Last-in-tree commit:** n/a (sandbox researcher TODO)
**Removed in:** moved 2026-09-21
**Why removed:** Still listed “replace TFLite wrapper with native Paddle” as in-progress. TFLite was purged `574c4121` (2026-05-01); native Paddle is production (`app/.../assets/paddle/prod_u8fp16`). Live TODO is repo `TODO.md` via `./todo-append`.

# Researcher's TODO List

## In-Progress Research
- [x] **Native Paddle-Lite Integration:** finalize the guidance for replacing the TFLite wrapper with the native v2.14rc ARM implementation.
- [ ] **Engine Accuracy Benchmark:** Re-run the `ocr_test_library` benchmark once native Paddle is integrated to verify accuracy gains.
- [x] **TFLite Performance Study:** Compare accuracy and speed between a native numeric TFLite model and the converted Paddle-to-TFLite model.
- [x] **Tesseract "Garbage" Diagnosis:** Review app configuration, test alternative pre-processing (CLAHE/Otsu), and propose diagnostics to stabilize results.

## Future Exploration
- [x] **Maintenance Schedule Data Sourcing:** Investigate providers/APIs for vehicle-specific maintenance schedules by Year/Make/Model. Documented in `MAINTENANCE_SCHEDULE_RESEARCH.md`.
- [x] **Vehicle Detail Lookup (VIN/Plate):** Research APIs for VIN and License Plate lookup (e.g., NHTSA). Documented in `VEHICLE_LOOKUP_RESEARCH.md`.
- [x] **Hard-Fail Deep Dive:** Generated a visual diagnostic report for the 46 \"Hard Fail\" fields and identified resolutions needed to maximize digit capture.
- [x] **Tesseract Crop Sweep:** Ran a full benchmark with 640px crops and varied binarization. Results in `TESSERACT_CROP_SWEEP_RESULTS.md` confirmed 5% max accuracy; Tesseract is fundamentally incompatible with 7-segment LCDs.
- [x] **7-Segment Fine-Tuning:** Abandoned. User prefers robust logic/heuristics over retraining against infinite font variations.
- [x] **Price-Constraint Veto:** Documented mathematical validation logic and decimal recovery heuristics in `PRICE_CONSTRAINT_VETO_STRATEGY.md`.
