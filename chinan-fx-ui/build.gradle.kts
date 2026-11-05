plugins {
    id("chinan.javafx-conventions")
    id("chinan.spring-conventions")
}

dependencies {
    implementation(project(":chinan-common"))
    implementation(project(":chinan-domain"))
    implementation(project(":chinan-application"))
    implementation(project(":chinan-infrastructure"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation(libs.spring.boot.starter.data.jpa)
}