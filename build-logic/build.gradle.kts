plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
    implementation("org.openjfx:javafx-plugin:0.1.0")
    // Spring Boot Gradle 插件由根项目通过 version catalog 的 alias 应用（3.4.4），
    // 这里不再额外引入 3.3.5，避免同一构建出现两个插件版本。
}