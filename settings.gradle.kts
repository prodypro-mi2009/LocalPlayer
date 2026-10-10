pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Only for the Android fork of jaudiotagger (tag reading/writing).
        maven("https://jitpack.io") {
            content { includeGroup("com.github.Adonai") }
        }
    }
}

rootProject.name = "LocalPlayer"
include(":app")
