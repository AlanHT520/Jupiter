plugins {
    alias(libs.plugins.javaConventions) apply false
    alias(libs.plugins.springConventions) apply false
    alias(libs.plugins.springBoot) apply false
    alias(libs.plugins.springDependencyManagement) apply false
}

group = "net.alan.jupiter"
version = "v-0.8.9"

allprojects {
    repositories {
        mavenCentral()
    }
}