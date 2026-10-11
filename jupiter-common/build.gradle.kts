plugins {
    alias(libs.plugins.javaConventions)
}

dependencies {
    implementation(libs.jackson.databind)
    testImplementation(libs.junit.jupiter)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}