pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
    }
    plugins {
        // Resolved from https://maven.fabricmc.net/net/fabricmc/fabric-loom (latest release at pin time).
        id("fabric-loom") version "1.17.8"
    }
}

plugins {
    // Resolved from https://maven.kikugie.dev/releases (latest release at pin time).
    id("dev.kikugie.stonecutter") version "0.9.5"
}

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"
    create(rootProject) {
        versions("1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.8", "1.21.10", "1.21.11")
        vcsVersion = "1.21.11"
    }
}

rootProject.name = "Atomized"
