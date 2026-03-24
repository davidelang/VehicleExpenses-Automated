plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.davidlang.vehicleexpensesautomated"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.davidlang.vehicleexpensesautomated"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.7"
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
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Room DB
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    // Hilt
    implementation("com.google.dagger:hilt-android:2.51")
    ksp("com.google.dagger:hilt-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0") // for SyncWorker
    // Material Components
    implementation("com.google.android.material:material:1.12.0")
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
