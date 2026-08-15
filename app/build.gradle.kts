import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/** SHA-256 hex of a file, or "missing" if absent. */
fun sha256File(f: java.io.File): String {
    if (!f.isFile) return "missing"
    val md = MessageDigest.getInstance("SHA-256")
    f.inputStream().use { inp ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = inp.read(buf)
            if (n <= 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { b -> "%02x".format(b) }
}

android {
    namespace = "com.davidlang.vehicleexpensesautomated"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.davidlang.vehicleexpensesautomated"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Use orNull so a broken git state (bad refs after permission issues or worktree problems)
        // does not fail the entire configuration with "bash exit 128".
        // We also set workingDir explicitly and use a more robust command.
        val rawVersion = providers.exec {
            workingDir = project.rootDir
            commandLine(
                "bash",
                "-c",
                "BRANCH_NAME=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo 'HEAD') && (git describe --tags --match \"${'$'}{BRANCH_NAME}-start\" 2>/dev/null || git describe --always 2>/dev/null || echo 'dev')"
            )
        }.standardOutput.asText.orNull?.trim() ?: "dev"

        // Fingerprint product paddle light SOs so experiment reports self-describe the
        // library under test (git describe alone does not encode jniLibs contents).
        // Format: <git>+so<8> where <8> is sha256 of "abi=sha|..." over all shipped ABIs.
        val jniRoot = file("src/main/jniLibs")
        val soAbis = listOf("x86_64", "arm64-v8a", "armeabi-v7a")
        val soShaByAbi = soAbis.associateWith { abi ->
            sha256File(file("$jniRoot/$abi/libpaddle_light_api_shared.so"))
        }
        val soJoin = soAbis.joinToString("|") { abi -> "$abi=${soShaByAbi[abi]}" }
        val soStamp8 = MessageDigest.getInstance("SHA-256")
            .digest(soJoin.toByteArray(Charsets.UTF_8))
            .joinToString("") { b -> "%02x".format(b) }
            .take(8)
        // versionName embeds SO stamp so any SO byte change is visible even before commit.
        // Still commit jniLibs + third_party/paddle/SO_SHIPPED.sha256 so describe advances too.
        val versionWithSo = "$rawVersion+so$soStamp8"
        versionName = versionWithSo
        buildConfigField("String", "VERSION_NAME", "\"${versionWithSo}\"")
        buildConfigField("String", "PADDLE_SO_STAMP", "\"so$soStamp8\"")
        buildConfigField("String", "PADDLE_SO_X86_64", "\"${soShaByAbi["x86_64"]}\"")
        buildConfigField("String", "PADDLE_SO_ARM64_V8A", "\"${soShaByAbi["arm64-v8a"]}\"")
        buildConfigField("String", "PADDLE_SO_ARMEABI_V7A", "\"${soShaByAbi["armeabi-v7a"]}\"")

        // Tracked inventory: update when light SOs change (committed with the SO blobs).
        val stampFile = rootProject.file("third_party/paddle/SO_SHIPPED.sha256")
        val stampBody = buildString {
            append("# libpaddle_light_api_shared.so per ABI (sha256). Commit with SO swaps.\n")
            append("# Composite stamp so$soStamp8 is embedded in app versionName.\n")
            soAbis.forEach { abi -> append("$abi ${soShaByAbi[abi]}\n") }
        }
        if (!stampFile.exists() || stampFile.readText() != stampBody) {
            stampFile.parentFile?.mkdirs()
            stampFile.writeText(stampBody)
            logger.lifecycle("Updated ${stampFile.relativeTo(rootProject.projectDir)} (so$soStamp8) — commit with jniLibs if SO changed")
        }

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                // 16KB page size (Android 15+ / Play): explicit for all ABIs including armv7
                // (NDK r28 defaults 16KB on 64-bit; armv7 still needs the linker flag).
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
            }
        }
    }
    // One APK per device ABI: matching .so set + matching .nb assets (see src/<flavor>/assets).
    flavorDimensions += "abi"
    productFlavors {
        create("arm64") {
            dimension = "abi"
            ndk { abiFilters += "arm64-v8a" }
        }
        create("armv7") {
            dimension = "abi"
            ndk { abiFilters += "armeabi-v7a" }
        }
        create("x86_64") {
            dimension = "abi"
            ndk { abiFilters += "x86_64" }
        }
    }
    // Default aapt ignore plus timestamped model leftovers (*.nb.bak.*).
    // Unscheduled dets live in third_party/paddle/exp_det_ab_unscheduled/ (not assets).
    androidResources {
        ignoreAssetsPattern =
            "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~:*.bak*"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
        compilerOptions {
            freeCompilerArgs.addAll(listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"))
        }
    }
    testOptions {
        // LocationBlobOverlay / FuelLocationJson pure unit tests (no device).
        unitTests.isReturnDefaultValues = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packaging {
        jniLibs {
            // Uncompressed + zipaligned natives required for 16KB page devices (AGP 8.5.1+).
            useLegacyPackaging = false
            // ANDROID_STL=c++_shared: AGP packages NDK libc++_shared.so per ABI.
            // pickFirst if more than one dependency also embeds it.
            pickFirsts += "**/libc++_shared.so"
            excludes += "**/*.bak"
            excludes += "**/*.bak.*"
            excludes += "**/*.fat.bak"
        }
        resources {
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/INDEX.LIST")
            // android-mail + android-activation both ship these
            excludes.add("META-INF/LICENSE.md")
            excludes.add("META-INF/NOTICE.md")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/NOTICE.txt")
        }
    }
}

// Hilt's javac task does not inherit kotlin.jvmToolchain; pin all JavaCompile to JDK 17 via Foojay.
val java17Compiler = javaToolchains.compilerFor {
    languageVersion.set(JavaLanguageVersion.of(17))
}
tasks.withType<JavaCompile>().configureEach {
    javaCompiler.set(java17Compiler)
}

dependencies {
    // Local jars only (PaddlePredictor, opencv-java). AARs come from third_party pins.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    // Material Icons (BOM-aligned) — required for Icons.Default.* at runtime
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.microsoft.identity.client:msal:5.3.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    // Coil for image thumbnails
    implementation("io.coil-kt:coil-compose:2.6.0")
    // Vico charts (Reports Lab) — pin 3.2.3; do NOT use compose-m3 (pulls material3 1.4
    // via JetBrains CMP and breaks ExposedDropdownMenuBox ABI vs Compose BOM 2024.10 / 1.3.x)
    implementation("com.patrykandpatrick.vico:compose:3.2.3")


    // Room DB
    val roomVersion = "2.7.0"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    // Hilt
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")
    // Material Components
    implementation("com.google.android.material:material:1.12.0")
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    ksp("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.9.0")
    // Google Sheets API for bidirectional sync
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.apis:google-api-services-sheets:v4-rev20220927-2.0.0")
    // Google Drive API for photo upload
    implementation("com.google.apis:google-api-services-drive:v3-rev20240509-2.0.0")
    // First-party libraries (third_party pins)
    implementation(files("../third_party/remotetable/artifact/remotetable.aar"))
    implementation(files("../third_party/extractmail/artifact/extractmail.aar"))
    // rclone photo-curated librclone AAR (gomobile; pin under third_party/rclone)
    implementation(files("../third_party/rclone/artifact/librclone.aar"))
    // IMAP (generic folder fetch for email fuel receipts)
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    // Encrypted prefs for IMAP app passwords
    implementation("androidx.security:security-crypto:1.0.0")
    // CameraX for the new fillup screen.
    // 1.4+ / 1.5+ ship 16KB-aligned libimage_processing_util_jni (unlike OpenCV prebuilts).
    val camerax = "1.6.1"
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    // OpenCV Java bindings (natives: jniLibs from third_party/opencv pin artifact)
    // Jar extracted from org.opencv:opencv:4.10.0 AAR classes.jar; natives are pin-built
    // fat libopencv_java4.so (core+imgproc+imgcodecs, 16KB pages) under jniLibs.
    // Do not re-add Maven org.opencv:opencv AAR — it ships full multi-ABI natives and would
    // override / bloat the pin .so.
    // ML Kit Text Recognition (bundled). Latest published 16.0.1 — arm64 already 16KB;
    // not like OpenCV (no pin rebuild). armv7 residual is secondary to Play's 64-bit gate.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Unit tests (LocationBlobOverlay / pure helpers) — agent-run via ./build_app … -- testDebugUnitTest
    // Robolectric: real org.json.JSONObject (android.jar stubs break encode/parse under JVM).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
    testImplementation("org.robolectric:robolectric:4.14.1")

    // Instrumented OCR functional gate (paddle angle→deskew→det→crop→rec)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
