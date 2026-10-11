plugins {
    id("jupiter.javafx-conventions")
    id("jupiter.spring-conventions")
}

dependencies {
    implementation(project(":jupiter-common"))
    implementation(project(":jupiter-domain"))
    implementation(project(":jupiter-application"))
    implementation(project(":jupiter-infrastructure"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation(libs.spring.boot.starter.data.jpa)
}