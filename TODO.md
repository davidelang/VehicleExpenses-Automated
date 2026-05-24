# Phase 116: Pure Native A/B Testing
- [x] DONE: Phase 0: Repository Cleanup
- [x] DONE: Phase 1: Dual Native Buffer Architecture
- [x] DONE: Phase 2: Deskew & Early Rotation
- [ ] Phase 3.1: Data Model Infrastructure
- [ ] Phase 3.2: Parallel Native Alignment
- [ ] Phase 3.3: Diverged Native Refinement
- [ ] Phase 4: Engine API Purge
- [ ] Phase 5: Deep Dead Code Elimination

# TODO: Protocol Alignment
- [x] DONE: Fix memory and GEMINI.md discrepancies (Strike System, No-Deploy, Plan Mode transitions). (See: dev-ai-interaction/plans/protocol-alignment.md)

# TODO: OCR Performance Optimization
- [x] DONE: Implement `nativePopulateMonoTensor`.
- [x] DONE: Optimize `OdometerOcrUtils` to bypass Bitmap-to-Mat roundtrips during deskew.
- [ ] Parallelize `nativePopulateMonoTensor` using SIMD/OpenMP for 2048px tensors.
- [x] DONE: Implement microscopic instrumentation for JNI boundaries and inference stages.
- [ ] Offload Valley Expansion algorithm to C++ to eliminate JNI per-pixel overhead.

