// Plugins are put on one shared classpath here. AGP is only added when :app is part of the build.
buildscript {
    val hasApp = rootProject.findProject(":app") != null
    repositories {
        if (hasApp) google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.compose.compiler.gradle.plugin)
        if (hasApp) classpath(libs.android.gradle.plugin)
    }
}
