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
        // NewPipe Extractor, which reads YouTube playlists, is only published here.
        maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\.TeamNewPipe.*") } }
    }
}

rootProject.name = "Recitation"
include(":core")
include(":yt")
include(":app")
