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

    defaultConfig {
        applicationId = "com.mindpalace.app"
        minSdk = 33
        targetSdk = 37
        versionCode = 5
        versionName = "0.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room3.testing)
    // Desktop natives for BundledSQLiteDriver in JVM (Robolectric) tests.
    testImplementation(libs.sqlite.bundled.jvm)
}
