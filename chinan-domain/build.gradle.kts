plugins {
    alias(libs.plugins.springConventions)
}

dependencies {
    implementation(project(":chinan-common"))

    implementation(libs.spring.boot.starter.data.jpa)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.spring.boot.starter.test)
}