// Mind Palace — root build file.
// NOTE: AGP 9 uses built-in Kotlin support: org.jetbrains.kotlin.android is NOT declared here.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room3) apply false
}
