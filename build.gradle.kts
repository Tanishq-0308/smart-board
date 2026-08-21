// Top-level build file. Plugins are declared here and applied in :app.
plugins {
    // `kotlin-android` is intentionally absent: AGP 9.x ships built-in Kotlin
    // support and fails the build if that plugin is also applied.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
