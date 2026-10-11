pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "Jupiter Java Edition"

include(
    "jupiter-common",
    "jupiter-domain",
    "jupiter-application",
    "jupiter-infrastructure",
    "jupiter-web",
    "jupiter-fx-ui",
    "jupiter-boot"
)