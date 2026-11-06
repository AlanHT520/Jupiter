plugins {
    java
}

/**
 * 统一使用 JDK 21 工具链编译，产出 Java 17 字节码。
 * - 避免"本机默认 JDK 版本过高（如 JDK 27）导致 Lombok 初始化失败"这类环境差异；
 * - Gradle 会按需自动定位/下载 JDK 21（settings 中已启用 foojay resolver）。
 */
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}