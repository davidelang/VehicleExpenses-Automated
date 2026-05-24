# TODO: Protocol Alignment
- [ ] Fix memory and GEMINI.md discrepancies (Strike System, No-Deploy, Plan Mode transitions). (See: dev-ai-interaction/plans/protocol-alignment.md)

# TODO: ICRS Alignment Core Migration
- [x] Phase 1: Define ICRS Architecture (See: docs/specs/ISOTROPIC_COORDINATE_SPEC.md)
- [x] Phase 2: Database Source of Truth Migration
- [x] Phase 3: Push Bridge Down to Alignment Core (See: dev-ai-interaction/plans/phase-3-unified-icrs-matrix.md)
- [x] Phase 4: Purge Bridges and Standardize BufferSet (See: dev-ai-interaction/plans/phase-4-bridge-decommissioning.md)
- [x] Phase 5: Final Verification & Cleanup (See: dev-ai-interaction/plans/phase-5-final-restoration.md)
  - [x] Restore metadata reporting keys.
  - [x] Serialize forensic winning_anchors to JSON.

# TODO: OCR Performance Optimization
- [x] DONE: Implement `nativePopulateMonoTensor`.
- [x] DONE: Optimize `OdometerOcrUtils` to bypass Bitmap-to-Mat roundtrips during deskew.
- [ ] Parallelize `nativePopulateMonoTensor` using SIMD/OpenMP for 2048px tensors.
- [x] DONE: Implement microscopic instrumentation for JNI boundaries and inference stages.
- [ ] Offload Valley Expansion algorithm to C++ to eliminate JNI per-pixel overhead.

# TODO: Gas Pump Extraction
- [x] DONE: Decouple `takeSnapshot` utility (See: plans/decouple-take-snapshot.md)
- [x] DONE: Implement Gas Pump Field Extraction Experiment on Android (See: dev-ai-interaction/plans/integrate-pump-extraction.md)
