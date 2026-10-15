// Root build file. Plugins are declared here (without applying) so a single
// version source in gradle/libs.versions.toml drives the whole build.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}