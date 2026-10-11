plugins {
    alias(libs.plugins.springConventions)
}

dependencies {
    implementation(project(":jupiter-common"))
    implementation(project(":jupiter-domain"))

    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)

    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    implementation(libs.poi.ooxml)
    implementation(libs.easyexcel)
    implementation(libs.freemarker)

    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.mysql.connector)
    runtimeOnly(libs.h2)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}