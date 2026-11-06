# 寝安 寝室晚查寝管理系统

高校宿舍晚查寝（查宿）管理系统：学生名册与楼栋/楼层/房间管理、查寝任务分配、移动端提交、证明照片、学院/楼栋/班级报表导出、注册审批、修改留痕。

> 仓库目录名为 `Jupiter`，产品名为 **寝安**。

首次登录需在登录页填写服务器地址与 TCP 端口（默认 9090）。

## 构建环境

- JDK 21（Gradle toolchain 自动使用），产出 Java 17 字节码
- Gradle 9.x（根工程 wrapper 9.6.0；Android 工程请用 Android Studio 或本机 Gradle 9.7+）
- Spring Boot 3.4.4 / JavaFX 21 / POI 5.4 / EasyExcel 4.0.3

