# TODO: ICRS Alignment Core Migration
- [x] Phase 1: Define ICRS Architecture (See: docs/specs/ISOTROPIC_COORDINATE_SPEC.md)
- [x] Phase 2: Database Source of Truth Migration
- [/] Phase 3: Push Bridge Down to Alignment Core (See: dev-ai-interaction/plans/phase-3-unified-affine-forensics.md)
  - [x] Refactor `anchorAlign` to use unified Affine Transform (`setValues`).
  - [ ] Implement forensic landmark serialization into JSON metadata.
  - [ ] Visual verification of alignment output.
  - [ ] Commit and build.
- [ ] Phase 4-5: Purge Bridges and BufferSet normalization
