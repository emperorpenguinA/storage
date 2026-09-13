@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        // Google's Maven repo, referenced by explicit URL (equivalent to the google()
        // shorthand) since some network setups only allow this exact host.
        maven("https://maven.google.com") {
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
    repositories {
        maven("https://maven.google.com")
        mavenCentral()
    }
}

rootProject.name = "MementoStorage"

include(":shared")
include(":composeApp")
