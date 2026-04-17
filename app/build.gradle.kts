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
        versionName = providers.exec { commandLine("git", "describe", "--always") }.standardOutput.asText.get().trim()
        buildConfigField("String", "VERSION_NAME", "\"${versionName}\"")
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
    packaging {
        resources {
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/INDEX.LIST")
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    // Coil for image thumbnails
    implementation("io.coil-kt:coil-compose:2.6.0")
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
    // CameraX for the new fillup screen
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    // OpenCV for dashboard image alignment and preprocessing
    implementation("org.opencv:opencv:4.10.0")
    // Tesseract (main OCR engine)
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    // TFLite core
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    // ML Kit Text Recognition (High-performance Tensor-optimized OCR)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Native Paddle-Lite Java Wrapper
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
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
                    if (file.extension == "so") {
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
