import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.davidlang.vehicleexpensesautomated"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.davidlang.vehicleexpensesautomated"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
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
        versionName = rawVersion
        buildConfigField("String", "VERSION_NAME", "\"${rawVersion}\"")

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
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
            useLegacyPackaging = true
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
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
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
    // CameraX for the new fillup screen
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    // OpenCV Java bindings (natives: jniLibs from third_party/opencv pin artifact)
    // Jar extracted from org.opencv:opencv:4.10.0 AAR classes.jar; natives are pin-built
    // fat libopencv_java4.so (core+imgproc+imgcodecs, 16KB pages) under jniLibs.
    // Do not re-add Maven org.opencv:opencv AAR — it ships full multi-ABI natives and would
    // override / bloat the pin .so.
    // ML Kit Text Recognition (High-performance Tensor-optimized OCR)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Native Paddle-Lite Java wrapper + other local jars (not AARs — rclone is pin path above)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}

// No more PaddleOCR validation — model has been removed

/**
 * Global UPX Compression Hook
 * Automatically compresses all native libraries (.so) during the build process.
 */
tasks.whenTaskAdded {
    if (name.startsWith("merge") && name.endsWith("NativeLibs")) {
        val mergedLibsDir = project.layout.buildDirectory.dir("intermediates/merged_native_libs")
        doLast {
            val nativeLibsDir = mergedLibsDir.get().asFile
            if (nativeLibsDir.exists()) {
                println(">>> UPX: Starting compression pass in $nativeLibsDir")
                nativeLibsDir.walkTopDown().forEach { file ->
                    // Skip pin-built 16KB-aligned natives (UPX would break max-page-size).
                    if (file.extension == "so" &&
                        file.name != "libopencv_java4.so" &&
                        file.name != "libgojni.so"
                    ) {
                        println(">>> UPX: Compressing ${file.name}")
                        try {
                            ProcessBuilder("upx", "--best", file.absolutePath)
                                .inheritIO()
                                .start()
                                .waitFor()
                        } catch (e: Exception) {
                            println(">>> UPX: Failed to compress ${file.name}: ${e.message}")
                        }
                    }
                }
                println(">>> UPX: Compression pass complete.")
            }
        }
    }
}
