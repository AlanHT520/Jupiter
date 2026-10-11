plugins {
    id("jupiter.java-conventions")
    id("org.openjfx.javafxplugin")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

dependencies {
    implementation("io.github.mkpaz:atlantafx-base:2.0.0")
    implementation("org.kordamp.ikonli:ikonli-javafx:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-fontawesome5-pack:12.3.1")
    implementation("org.controlsfx:controlsfx:11.2.1")
}