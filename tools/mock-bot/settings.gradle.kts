pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "mock-bot"

// Gedeeld schema met de launcher: hetzelfde :protocol-project.
include(":protocol")
project(":protocol").projectDir = file("../../protocol")
