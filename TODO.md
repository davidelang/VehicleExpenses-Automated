# Phase 116: Pure Native A/B Testing
- [x] DONE: Phase 0: Repository Cleanup
- [x] DONE: Phase 1: Dual Native Architecture
- [x] DONE: Phase 2: Deskew & Early Rotation
- [x] DONE: Phase 3: Diverged Alignment & Refinement
- [x] DONE: Phase 4: Engine API Purge
- [/] IN PROGRESS: Phase 5.1: Hollowing Out the Standard Path (Transitioning dependencies and reporting) (See: dev-ai-interaction/plans/phase-116-part-5-1-hollow-out.md)
- [ ] Phase 5.2: Logic Removal (Stop Standard path execution)
- [ ] Phase 5.3: ARGB Eradication (Buffer Removal & Dead Code Elimination)

# TODO: Protocol Alignment
- [x] DONE: Fix memory and GEMINI.md discrepancies (Strike System, No-Deploy, Plan Mode transitions). (See: dev-ai-interaction/plans/protocol-alignment.md)

# TODO: OCR Performance Optimization
- [x] DONE: Implement `nativePopulateMonoTensor`.
- [x] DONE: Optimize `OdometerOcrUtils` to bypass Bitmap-to-Mat roundtrips during deskew.
- [ ] Parallelize `nativePopulateMonoTensor` using SIMD/OpenMP for 2048px tensors.
- [x] DONE: Implement microscopic instrumentation for JNI boundaries and inference stages.
- [ ] Offload Valley Expansion algorithm to C++ to eliminate JNI per-pixel overhead.
