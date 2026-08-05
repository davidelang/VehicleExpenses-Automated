# remotetable pin

| | |
|--|--|
| **Upstream** | `davidelang/remotetable` @ `3a83e579ad8a11f83f2976b2a802f8a13439a3f0` (`master` co-dev; filter v1.1) |
| **Profile** | Co-dev library + consumer pin (RO builds supported) |
| **build_time** | `minutes` |
| **reproducible** | `true` (same pin + AGP/Kotlin/JDK; verified bit-identical host rebuild) |
| **Product** | `artifact/remotetable.aar` (pure Kotlin library) |

## Reproduce (RO)

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$HOME/Android/Sdk
./third_party/fetch-deps ro remotetable
./third_party/fetch-deps build remotetable
```

Optional host: `~/git/remotetable` (orchestration + `master/` worktree).

## RO build hygiene

- Pin sources stay RO after `fetch-deps ro`.
- `./build` only unlocks `src/android/.gradle` and `src/android/remotetable/` (Gradle outputs).
- Does **not** require `requires_writable_src`.
- Product path: `src/android/remotetable/build/outputs/aar/remotetable-release.aar` → `get-artifacts`.

## Tests in build

- `python3 conformance/harness.py` (offline) before AAR assemble.
