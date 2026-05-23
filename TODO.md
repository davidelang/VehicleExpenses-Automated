# TODO: ICRS Alignment Core Migration
- [x] Phase 1: Define ICRS Architecture (See: docs/specs/ISOTROPIC_COORDINATE_SPEC.md)
- [x] Phase 2: Database Source of Truth Migration
- [/] Phase 3: Push Bridge Down to Alignment Core (See: dev-ai-interaction/plans/phase-3-unified-icrs-matrix.md)
  - [ ] Implement Unified ICRS Matrix coefficients in `anchorAlign`.
  - [ ] Refactor consensus logic to use ICRS landmarks.
  - [ ] Implement forensic landmark serialization (ICRS + Pixel).
  - [ ] Visual verification of alignment output.
  - [ ] Commit and build.
- [ ] Phase 4-5: Purge Bridges and BufferSet normalization
