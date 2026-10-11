plugins {
    alias(libs.plugins.springConventions)
    alias(libs.plugins.springBoot)
    id("org.openjfx.javafxplugin")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

dependencies {
    implementation(project(":jupiter-common"))
    implementation(project(":jupiter-domain"))
    implementation(project(":jupiter-application"))
    implementation(project(":jupiter-infrastructure"))
    implementation(project(":jupiter-web"))
    implementation(project(":jupiter-fx-ui"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.freemarker)
    developmentOnly(libs.spring.boot.devtools)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.spring.boot.starter.test)
}

// ===================================================================
// 禁用 Spring Boot Fat JAR，改用普通 JAR + libs/ 目录 + jpackage
// ===================================================================
tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
    archiveClassifier.set("")
    manifest {
        attributes(
            "Main-Class" to "net.alan.jupiter.JupiterApplication"
        )
    }
}

// ===================================================================
// 打包相关变量
// ===================================================================
val mainClass = "net.alan.jupiter.JupiterApplication"
val appName = "Jupiter"
val packageDir = layout.buildDirectory.dir("package")
val libDir = packageDir.map { it.dir("lib") }
val libsDir = libDir.map { it.dir("libs") }
val rtImageDir = packageDir.map { it.dir("runtime") }
val jpackageInputDir = packageDir.map { it.dir("jpackage-input") }


// ===================================================================
// Step 1: jlink 生成精简 JRE
// ===================================================================
abstract class JlinkRuntimeTask : DefaultTask() {

    @get:Input
    abstract val modules: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun build() {
        val rtDir = outputDir.get().asFile
        rtDir.parentFile.mkdirs()
        rtDir.deleteRecursively()

        val javaHome = System.getProperty("java.home")
        val jlinkBin = javaHome.trimEnd(File.separatorChar) + "${File.separator}bin${File.separator}jlink.exe"
        val proc = ProcessBuilder(
            jlinkBin,
            "--output", rtDir.absolutePath,
            "--add-modules", modules.get().joinToString(","),
            "--strip-debug", "--no-man-pages", "--no-header-files"
        ).redirectErrorStream(true).start()
        proc.inputStream.bufferedReader().use { it.lines().forEach { println(it) } }
        val exitCode = proc.waitFor()
        if (exitCode != 0) {
            throw GradleException("jlink failed with exit code $exitCode")
        }
    }
}

val jlinkRuntime = tasks.register<JlinkRuntimeTask>("jlinkRuntime") {
    group = "distribution"
    description = "用 jlink 生成精简 JRE"
    modules.set(listOf(
        "java.base", "java.logging", "java.xml", "java.sql", "java.naming",
        "java.desktop", "java.management", "java.security.jgss",
        "java.instrument", "jdk.unsupported", "java.net.http", "java.scripting"
    ))
    outputDir.set(rtImageDir)
}

// ===================================================================
// Step 2: 复制主 JAR 到 lib/（非 Fat JAR，仅项目代码）
// ===================================================================
val copyMainJar = tasks.register<Copy>("copyMainJar") {
    group = "distribution"
    description = "复制主 JAR 到 lib 目录"
    dependsOn(tasks.jar)
    from(tasks.jar.flatMap { it.archiveFile })
    into(libDir)
}

// ===================================================================
// Step 3: 复制所有依赖到 lib/libs/
// ===================================================================
val copyDependencies = tasks.register<Copy>("copyDependencies") {
    group = "distribution"
    description = "复制所有依赖 JAR 到 lib/libs 目录"
    dependsOn(tasks.jar)
    from(configurations.runtimeClasspath)
    into(libsDir)
}

// ===================================================================
// Step 4: 生成 start.bat（使用 -cp 方式加载 libs/* 所有依赖）
// ===================================================================
abstract class CreateBatTask : DefaultTask() {

    @get:Input
    abstract val jarFileName: Property<String>

    @get:Input
    abstract val mainClass: Property<String>

    @get:OutputFile
    abstract val batFile: RegularFileProperty

    @TaskAction
    fun generate() {
        batFile.get().asFile.writeText("""
            |@echo off
            |chcp 65001 >nul
            |title Jupiter Server
            |cd /d "%~dp0lib"
            |..\runtime\bin\java.exe -Xmx512m -Dfile.encoding=UTF-8 -cp "${jarFileName.get()};libs\*" ${mainClass.get()} %*
            |pause
        """.trimMargin())
    }
}

val createBat = tasks.register<CreateBatTask>("createBat") {
    group = "distribution"
    description = "生成 start.bat 启动脚本"
    dependsOn(copyMainJar, copyDependencies, jlinkRuntime)
    jarFileName.set(tasks.jar.flatMap { it.archiveFileName })
    mainClass.set("net.alan.jupiter.JupiterApplication")
    batFile.set(packageDir.map { it.file("start.bat") })
}

// ===================================================================
// Step 5: 复制图标到 jpackage 输入目录
// ===================================================================
val copyIcon = tasks.register<Copy>("copyIcon") {
    group = "distribution"
    description = "复制 EXE 图标到 jpackage 输入目录"
    from(rootProject.projectDir.resolve("jupiter-fx-ui/src/main/resources/jupiter.ico"))
    into(jpackageInputDir)
}

// ===================================================================
// Step 6: 准备 jpackage 输入目录（扁平结构，所有 JAR 放在一起）
// ===================================================================
val prepareJpackageInput = tasks.register<Copy>("prepareJpackageInput") {
    group = "distribution"
    description = "准备 jpackage 输入目录"
    dependsOn(tasks.jar, copyIcon)
    from(tasks.jar.flatMap { it.archiveFile })
    from(configurations.runtimeClasspath)
    into(jpackageInputDir)
}

// ===================================================================
// Step 6: jpackage 创建独立应用镜像
// ===================================================================
abstract class JpackageAppTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeDir: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val iconFile: RegularFileProperty

    @get:Input
    abstract val jarFileName: Property<String>

    @get:Input
    abstract val mainClass: Property<String>

    @get:Input
    abstract val appName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun build() {
        val input = inputDir.get().asFile
        val runtime = runtimeDir.get().asFile
        val jarName = jarFileName.get()
        val destParent = outputDir.get().asFile.parentFile
        val targetDir = outputDir.get().asFile

        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        destParent.mkdirs()

        val javaHome = System.getProperty("java.home")
        val jpackageBin = javaHome.trimEnd(File.separatorChar) + "${File.separator}bin${File.separator}jpackage.exe"

        val cmd = mutableListOf(
            jpackageBin,
            "--type", "app-image",
            "--input", input.absolutePath,
            "--main-jar", jarName,
            "--main-class", mainClass.get(),
            "--runtime-image", runtime.absolutePath,
            "--name", appName.get(),
            "--dest", destParent.absolutePath,
            "--java-options", "-Dfile.encoding=UTF-8",
            "--java-options", "-Xmx512m"
        )

        val icon = inputDir.get().file("jupiter.ico").asFile
        if (icon.exists()) {
            cmd.addAll(listOf("--icon", icon.absolutePath))
        }

        val proc = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText()
        if (output.isNotBlank()) println(output)
        val exitCode = proc.waitFor()
        if (exitCode != 0) {
            throw GradleException("jpackage failed with exit code $exitCode: $output")
        }
    }
}

val jpackageApp = tasks.register<JpackageAppTask>("jpackageApp") {
    group = "distribution"
    description = "使用 jpackage 创建独立应用镜像（包含原生启动器）"
    dependsOn(prepareJpackageInput, jlinkRuntime)
    inputDir.set(jpackageInputDir)
    runtimeDir.set(rtImageDir)
    iconFile.set(jpackageInputDir.map { it.file("jupiter.ico") })
    jarFileName.set(tasks.jar.flatMap { it.archiveFileName })
    mainClass.set("net.alan.jupiter.JupiterApplication")
    appName.set("Jupiter")
    outputDir.set(packageDir.map { it.dir("Jupiter") })
}

// ===================================================================
// 主打包任务
// ===================================================================
tasks.register("packageApp") {
    group = "distribution"
    description = "打包完整应用：lib/ 目录结构 + start.bat + jpackage 应用镜像"
    dependsOn(createBat, jpackageApp)
}