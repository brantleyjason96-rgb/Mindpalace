import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

android {
    namespace = "com.mindpalace.app"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.mindpalace.app"
        minSdk = 33
        targetSdk = 37
        versionCode = 6
        versionName = "0.6.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Physical device is arm64; x86_64 kept for emulator testing.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DBUILD_SHARED_LIBS=ON"
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DLLAMA_OPENSSL=OFF"
                arguments += "-DGGML_NATIVE=OFF"
                arguments += "-DGGML_BACKEND_DL=ON"
                arguments += "-DGGML_CPU_ALL_VARIANTS=ON"
                arguments += "-DGGML_LLAMAFILE=OFF"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // ggml_backend_load_all_from_path() dlopen()s the CPU-variant backend .so files by
            // path from nativeLibraryDir at runtime — they must be real extracted files, not just
            // mapped straight out of the APK (the AGP default since native libs can be page-aligned).
            useLegacyPackaging = true
        }
    }

    sourceSets {
        // Exported schema JSON (app/schemas) — read by MigrationTestHelper. Robolectric local
        // unit tests reuse the main *debug* merged assets dir, not a test-only one, so this has
        // to live on "main" rather than "test" for MigrationTestHelper to find it at all.
        getByName("main") {
            assets.srcDirs("$projectDir/schemas")
        }
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged manifest/resources on the JVM.
            isIncludeAndroidResources = true
        }
    }
}

// AGP 9 built-in Kotlin: jvmTarget defaults to compileOptions.targetCompatibility,
// restated here for clarity.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Room 3 schema export (checked in under app/schemas for future migration tests).
room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room3.runtime)
    ksp(libs.room3.compiler)
    // BundledSQLiteDriver = real SQLite with FTS5 compiled in, both on device and in JVM tests.
    implementation(libs.sqlite.bundled)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Bundled (on-device, no network) text recognition for Camera/OCR capture (D17).
    implementation(libs.mlkit.text.recognition)
    // Nightly AI loop scheduling (charging + idle, battery not low) — D13/D22/D23's "next piece".
    implementation(libs.work.runtime)
    // Handwriting capture (D26) — dynamic-download model only, the one deliberate exception to
    // "no INTERNET permission, ever"; the recognizer itself still runs fully on-device.
    implementation(libs.mlkit.digital.ink.recognition)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room3.testing)
    // Desktop natives for BundledSQLiteDriver in JVM (Robolectric) tests.
    testImplementation(libs.sqlite.bundled.jvm)
}
