#!/bin/bash
# Curated rclone backends for VehicleExpenses photo backup.
# Expects: writable WORKDIR (rclone tree), outputs AAR to OUT_AAR.
#
# Compile-out (must NOT appear in patched gomobile.go — see RcloneProviderCatalog.COMPILED_OUT_TYPES):
#   local, memory, http, doi, imagekit, cloudinary, sharefile, linkbox, googlephotos,
#   hdfs, netstorage, qingstor, swift, filefabric, quatrix, internetarchive
set -euo pipefail

WORKDIR="${WORKDIR:-/workspace/rclone}"
GOMOBILE_GO="librclone/gomobile/gomobile.go"
OUT_AAR="${OUT_AAR:-/output/librclone_photo.aar}"

# Compile-out backend import paths (aligned with RcloneProviderCatalog.COMPILED_OUT_TYPES).
COMPILE_OUT_PATHS=(
    "github.com/rclone/rclone/backend/local"
    "github.com/rclone/rclone/backend/memory"
    "github.com/rclone/rclone/backend/http"
    "github.com/rclone/rclone/backend/doi"
    "github.com/rclone/rclone/backend/imagekit"
    "github.com/rclone/rclone/backend/cloudinary"
    "github.com/rclone/rclone/backend/sharefile"
    "github.com/rclone/rclone/backend/linkbox"
    "github.com/rclone/rclone/backend/googlephotos"
    "github.com/rclone/rclone/backend/hdfs"
    "github.com/rclone/rclone/backend/netstorage"
    "github.com/rclone/rclone/backend/qingstor"
    "github.com/rclone/rclone/backend/swift"
    "github.com/rclone/rclone/backend/filefabric"
    "github.com/rclone/rclone/backend/quatrix"
    "github.com/rclone/rclone/backend/internetarchive"
)

export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/21.4.7075529}"
# 16KB ELF max-page-size for Android 15+ devices (cgo linker)
export CGO_LDFLAGS="${CGO_LDFLAGS:--Wl,-z,max-page-size=16384}"

cd "$WORKDIR"

if [[ ! -f "$GOMOBILE_GO" ]]; then
    echo "ERROR: $GOMOBILE_GO not found under $WORKDIR" >&2
    exit 1
fi

cp "$GOMOBILE_GO" "${GOMOBILE_GO}.bak"

IMPORTS_FILE="$(mktemp)"
cat > "$IMPORTS_FILE" <<'IMPORTS'
// Photo-curated backends (build-photo-aar.sh; see RcloneProviderCatalog.COMPILED_OUT_TYPES)
_ "github.com/rclone/rclone/backend/alias"
_ "github.com/rclone/rclone/backend/archive"
_ "github.com/rclone/rclone/backend/azureblob"
_ "github.com/rclone/rclone/backend/azurefiles"
_ "github.com/rclone/rclone/backend/b2"
_ "github.com/rclone/rclone/backend/box"
_ "github.com/rclone/rclone/backend/cache"
_ "github.com/rclone/rclone/backend/chunker"
_ "github.com/rclone/rclone/backend/combine"
_ "github.com/rclone/rclone/backend/compress"
_ "github.com/rclone/rclone/backend/crypt"
_ "github.com/rclone/rclone/backend/drive"
_ "github.com/rclone/rclone/backend/dropbox"
_ "github.com/rclone/rclone/backend/fichier"
_ "github.com/rclone/rclone/backend/ftp"
_ "github.com/rclone/rclone/backend/googlecloudstorage"
_ "github.com/rclone/rclone/backend/hasher"
_ "github.com/rclone/rclone/backend/hidrive"
_ "github.com/rclone/rclone/backend/iclouddrive"
_ "github.com/rclone/rclone/backend/jottacloud"
_ "github.com/rclone/rclone/backend/koofr"
_ "github.com/rclone/rclone/backend/mailru"
_ "github.com/rclone/rclone/backend/mega"
_ "github.com/rclone/rclone/backend/onedrive"
_ "github.com/rclone/rclone/backend/opendrive"
_ "github.com/rclone/rclone/backend/oracleobjectstorage"
_ "github.com/rclone/rclone/backend/pcloud"
_ "github.com/rclone/rclone/backend/pikpak"
_ "github.com/rclone/rclone/backend/premiumizeme"
_ "github.com/rclone/rclone/backend/protondrive"
_ "github.com/rclone/rclone/backend/putio"
_ "github.com/rclone/rclone/backend/s3"
_ "github.com/rclone/rclone/backend/seafile"
_ "github.com/rclone/rclone/backend/sftp"
_ "github.com/rclone/rclone/backend/sia"
_ "github.com/rclone/rclone/backend/smb"
_ "github.com/rclone/rclone/backend/storj"
_ "github.com/rclone/rclone/backend/union"
_ "github.com/rclone/rclone/backend/webdav"
_ "github.com/rclone/rclone/backend/yandex"
_ "github.com/rclone/rclone/backend/zoho"
IMPORTS

PATCHED_FILE="$(mktemp)"
awk -v imports="$IMPORTS_FILE" '
    /backend\/all/ {
        while ((getline line < imports) > 0) print line
        close(imports)
        next
    }
    { print }
' "$GOMOBILE_GO" > "$PATCHED_FILE"
mv "$PATCHED_FILE" "$GOMOBILE_GO"
rm -f "$IMPORTS_FILE"

if grep -q 'backend/all' "$GOMOBILE_GO"; then
    echo "ERROR: backend/all still present after patch — aborting" >&2
    mv "${GOMOBILE_GO}.bak" "$GOMOBILE_GO"
    exit 1
fi

for path in "${COMPILE_OUT_PATHS[@]}"; do
    if grep -q "$path" "$GOMOBILE_GO"; then
        echo "ERROR: compile-out import still present: $path" >&2
        mv "${GOMOBILE_GO}.bak" "$GOMOBILE_GO"
        exit 1
    fi
done

echo "Post-patch validation OK: backend/all removed; compile-out imports absent"
echo "CGO_LDFLAGS=$CGO_LDFLAGS"

mkdir -p "$(dirname "$OUT_AAR")"
go mod download
gomobile bind -v \
    -target=android/arm,android/arm64,android/amd64 \
    -androidapi 21 \
    -tags "noselfupdate nmount" \
    -ldflags="-s -w" \
    -trimpath \
    -o "$OUT_AAR" \
    ./librclone/gomobile

# Restore tree if bak still present (container copy; host pin stays clean)
if [[ -f "${GOMOBILE_GO}.bak" ]]; then
    mv "${GOMOBILE_GO}.bak" "$GOMOBILE_GO"
fi

echo "Built $OUT_AAR ($(du -h "$OUT_AAR" | cut -f1))"
