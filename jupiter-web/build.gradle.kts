plugins {
    alias(libs.plugins.springConventions)
}

dependencies {
    implementation(project(":jupiter-common"))
    implementation(project(":jupiter-domain"))
    implementation(project(":jupiter-application"))
    implementation(project(":jupiter-infrastructure"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.springdoc.openapi)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    annotationProcessor(libs.mapstruct.processor)
    implementation(libs.mapstruct)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
}