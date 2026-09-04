pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Fallback: sebagian rilis ffmpegkit-maintained didistribusikan lewat JitPack
        // selain Maven Central — lihat section 10 blueprint.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Music Visualizer Studio"
include(":app")
