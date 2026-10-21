pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// The Android app needs an Android SDK. Builds without one (for example a sandbox that can't
// reach Google's SDK host) still build and test :core.
val localProps = java.util.Properties().apply {
    val f = file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasAndroidSdk = listOf(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"), localProps.getProperty("sdk.dir"))
    .any { !it.isNullOrBlank() && file(it).exists() }

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (hasAndroidSdk) google()
        mavenCentral()
    }
}

rootProject.name = "Tyst"

include(":core")

if (hasAndroidSdk) {
    include(":app")
} else {
    logger.warn("Tyst: no Android SDK found, building :core only.")
}
