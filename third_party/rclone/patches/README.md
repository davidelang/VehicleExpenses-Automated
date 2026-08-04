# rclone patches

None required for the photo AAR path. Backend curation is applied **at build time**
inside the container copy of `librclone/gomobile/gomobile.go` (see `scripts/build-photo-aar.sh`).
The pin `src/` tree stays clean / RO.
