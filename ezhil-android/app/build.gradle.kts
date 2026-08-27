import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProps = Properties()
rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { localProps.load(it) }
val apiBaseUrl: String = localProps.getProperty("API_BASE_URL") ?: "http://10.0.2.2:8080/api/v1/"

android {
    namespace = "com.ezhil.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.ezhil.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    // Release signing. The keystore itself is never committed — its location
    // and passwords come from local.properties (gitignored) or, for CI, from
    // the environment. When none is configured the release build simply stays
    // unsigned rather than failing, so a debug-only checkout still builds.
    signingConfigs {
        create("release") {
            val storePath = localProps.getProperty("RELEASE_STORE_FILE")
                ?: System.getenv("EZHIL_RELEASE_STORE_FILE")
            if (storePath != null && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                    ?: System.getenv("EZHIL_RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                    ?: System.getenv("EZHIL_RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
                    ?: System.getenv("EZHIL_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 was off, so no release build was ever shrunk, optimised or
            // obfuscated. proguard-rules.pro keeps everything reached by
            // reflection — Moshi adapters, Retrofit interfaces, Room entities,
            // and the TFLite/ONNX models that cross into native code by name.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // ONNX Runtime, ML Kit and TFLite each ship a native library per
            // ABI, and bundling all four produced a 162 MB APK — over Play's
            // 150 MB limit, and a punishing download for a school on a slow
            // connection. x86 and x86_64 exist for emulators only, so release
            // builds carry just the two ABIs real handsets use. Debug keeps
            // them all so the emulator still works.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }

            // Only attach the signing config when a keystore was actually
            // found; otherwise Gradle fails on a missing storeFile.
            signingConfig = signingConfigs.getByName("release")
                .takeIf { it.storeFile?.exists() == true }
        }
    }

    // Schools that sideload cannot use an App Bundle, and a single APK carrying
    // both ABIs is 75 MB of which half is unusable on any given handset. Per-ABI
    // APKs mean an arm64 phone downloads only its own. The universal APK is kept
    // too, for when the target device is unknown.
    splits {
        abi {
            // AGP refuses to build an app bundle while multiple APKs are being
            // built (issuetracker 402800800), so bundleRelease is run with
            // -PezhilNoSplits=true. Nothing about a normal build changes.
            isEnable = project.findProperty("ezhilNoSplits") != "true"
            reset()
            // The two ABIs a classroom handset actually uses. CI adds x86_64
            // with -PezhilExtraAbi=x86_64 so the emulator has something it can
            // install, without that architecture ever reaching a release.
            val extra = (project.findProperty("ezhilExtraAbi") as String?)
                ?.split(",")?.map(String::trim)?.filter(String::isNotEmpty)
                ?: emptyList()
            include(*(listOf("arm64-v8a", "armeabi-v7a") + extra).toTypedArray())
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = false
        shaders = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    androidResources {
        noCompress += "tflite"
        noCompress += "onnx"   // ONNX Runtime reads the raw bytes; compression breaks mmap
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose UI
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin) // needed at compile time by moshi-kotlin-codegen KSP
    ksp(libs.moshi.codegen)

    // Security
    implementation(libs.androidx.security.crypto)

    // TensorFlow Lite
    implementation(libs.tflite)
    implementation(libs.tflite.support)
    implementation(libs.tflite.gpu)

    // ML Kit — on-device text recognition (Latin/English)
    implementation(libs.mlkit.text.recognition)

    // ONNX Runtime — on-device Tamil CRNN OCR (OcrModel.kt)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Coil
    implementation(libs.coil.compose)

    // DataStore
    implementation(libs.datastore.preferences)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
