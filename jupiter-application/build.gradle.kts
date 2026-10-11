plugins {
    alias(libs.plugins.springConventions)
}

dependencies {
    implementation(project(":jupiter-common"))
    implementation(project(":jupiter-domain"))
    implementation(project(":jupiter-infrastructure"))

    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.freemarker)
    implementation(libs.poi.ooxml)
    implementation(libs.easyexcel)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.spring.boot.starter.test)
}