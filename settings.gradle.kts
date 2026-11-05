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

rootProject.name = "Chinan Java Edition"

include(
    "chinan-common",
    "chinan-domain",
    "chinan-application",
    "chinan-infrastructure",
    "chinan-web",
    "chinan-fx-ui",
    "chinan-boot"
)