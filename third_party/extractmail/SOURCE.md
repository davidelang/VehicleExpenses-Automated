# extractmail pin

| | |
|--|--|
| **Upstream** | `davidelang/extractmail` @ `0dc3f80ad8c9d3bf0fe6a127649b44e60cd64f81` (`master`; includes third_party/remotetable pin) |
| **Profile** | Co-dev library + consumer pin (RO builds supported) |
| **build_time** | `minutes` |
| **reproducible** | `true` (same pin + AGP/Kotlin/JDK; verified bit-identical host rebuild) |
| **Product** | `artifact/extractmail.aar` (~3.5KB, thin Kotlin API surface) |

## Reproduce (RO)

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$HOME/Android/Sdk
./third_party/fetch-deps ro extractmail
./third_party/fetch-deps build extractmail
```

Optional host: `~/git/extractmail`.

## RO build hygiene

- Pin sources stay RO after `fetch-deps ro`.
- `./build` only unlocks `src/android/.gradle` and `src/android/extractmail/` (Gradle outputs).
- Does **not** require `requires_writable_src`.
- Product path: `src/android/extractmail/build/outputs/aar/extractmail-release.aar` → `get-artifacts`.

## Tests in build

1. Optional: `python3 python/run_goldens.py` (non-fatal if deps missing)
2. `src/scripts/build-aar.sh` → `assembleRelease`

## Co-develop (rw)

```bash
./third_party/fetch-deps rw extractmail
# edit under src/
./third_party/fetch-deps build extractmail
# promote: library PR → bump libpin.toml git_sha + artifact
```
