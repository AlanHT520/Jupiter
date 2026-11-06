# Chinan 寝室晚查寝管理系统

高校宿舍晚查寝（查宿）管理系统：学生名册与楼栋/楼层/房间管理、查寝任务分配、移动端提交、证明照片、学院/楼栋/班级报表导出、注册审批、修改留痕。

> 仓库目录名仍为 `Jupiter`，产品名为 **Chinan**（`rootProject.name = "Chinan Java Edition"`，包名 `net.alan.jupiter`）。

## 一、模块结构

| 模块 | 说明 |
|---|---|
| `chinan-common` | 统一返回 `ApiResult` / `PageResult`、`BizException` / `ErrorCode`、工具类 |
| `chinan-domain` | JPA 实体、`JpaRepository` 接口、预设状态与考勤计算等领域服务 |
| `chinan-infrastructure` | Excel 导入导出、JWT、本地文件存储、线程池、Specification |
| `chinan-application` | 应用服务：查寝提交与审计、报表统计、导入、任务、注册、访问控制 |
| `chinan-web` | REST 接口（`/api/**`、`/admin-api/**`）+ PE 移动网页 |
| `chinan-fx-ui` | JavaFX 桌面管理端（FXML + 控制器 + 主题） |
| `chinan-boot` | 启动入口、安全配置、TCP 服务、数据初始化、打包（jlink + jpackage） |
| `chinan-android` | 查宿员 Android App（Compose + Hilt + Room），**独立 Gradle 工程** |

## 二、运行方式

### 1. 服务端（Web + REST + TCP）

```powershell
# 默认 dev：H2 文件库 ./data/Chinan，端口 8080，TCP 9090
.\gradlew.bat :chinan-boot:bootRun --args="--mode=server"
```

- 管理后台：`http://localhost:8080/admin/`
- PE 移动网页：`http://localhost:8080/pe/index.html`
- OpenAPI（仅 dev）：`http://localhost:8080/api-docs`
- 首次启动会创建管理员 `AlanHT520`；未配置密码时生成随机密码并打印到日志（`Chinan.admin.default-password` 可指定）。

### 2. 桌面端（JavaFX）

```powershell
.\gradlew.bat :chinan-boot:bootRun
```

桌面模式按当前约定**免登录**，以本地 ADMIN 身份进入；同时会启动 TCP 服务供手机端连接（可用 `--Chinan.tcp.enabled=false` 关闭）。

### 3. 生产环境

```powershell
java -Dspring.profiles.active=prod -DDB_USERNAME=... -DDB_PASSWORD=... ^
     -DChinan_JWT_SECRET=至少32位密钥 -jar chinan-boot.jar --mode=server
```

- 数据库：PostgreSQL，`ddl-auto=validate`（需先建表；迁移脚本见 `chinan-boot/src/main/resources/db/migration/`，尚未接入 Flyway）。
- 必须显式提供 `Chinan_JWT_SECRET`，生产默认关闭 Swagger 与 H2 控制台。

### 4. Android

```powershell
# 工程位于 chinan-android/，用本机 Gradle 或 Android Studio 构建
gradle -p chinan-android :app:assembleDebug
```

首次登录需在登录页填写服务器地址与 TCP 端口（默认 9090）。

## 三、构建环境

- JDK 21（Gradle toolchain 自动使用），产出 Java 17 字节码
- Gradle 9.x（根工程 wrapper 9.6.0；Android 工程请用 Android Studio 或本机 Gradle 9.7+）
- Spring Boot 3.4.4 / JavaFX 21 / POI 5.4 / EasyExcel 4.0.3

常用命令：

```powershell
.\gradlew.bat --offline compileJava compileTestJava   # 编译
.\gradlew.bat --offline test                          # 单元测试
.\gradlew.bat --offline :chinan-boot:packageApp       # jlink + jpackage 打包（Windows）
```

## 四、关键约定

- **应到口径**：应到 = 在册学生 − 走读 − 长期病假（征兵/实习计入应到并单列）。
- **导入语义**：名册同步，不是清空重建；Excel 中未出现的在校生标记为停用，历史记录保留。
- **已完成任务**：查宿员不能再修改；管理员可修改，变更写入 `check_record_modify_log`。
- **鉴权**：JWT；角色以数据库为准；改密/停用账号会递增 `token_version` 使旧 token 失效。
- **访问控制**：任务级 `AccessGuard`（管理员或任务归属人），读写一致。

## 五、界面

桌面端为**深色管理台**：左侧分组导航 + 顶部页面标题/用户区 + 卡片式统计与表格，统一品牌蓝 `#4C8DFF` 与状态色；Android 端为 Material 3，与桌面端共用同一套品牌色与语义状态色。

| 桌面端 | 截图 |
|---|---|
| 首页概览 | ![首页概览](docs/images/desktop-dashboard.png) |
| 学生名册 | ![学生名册](docs/images/desktop-roster.png) |
| 查寝任务 | ![查寝任务](docs/images/desktop-task.png) |
| 数据报表 | ![数据报表](docs/images/desktop-report.png) |
| 楼栋总览 | ![楼栋总览](docs/images/desktop-building.png) |
| 注册管理 | ![注册管理](docs/images/desktop-registration.png) |
| 系统日志 | ![系统日志](docs/images/desktop-log.png) |
| 创建任务对话框 | ![创建任务对话框](docs/images/desktop-dialog.png) |
| 表单行内校验 | ![表单行内校验](docs/images/desktop-dialog-validation.png) |

设计与改版细节见 [docs/ui-redesign.md](docs/ui-redesign.md)。

## 六、已知限制

- Android 端仍使用明文 TCP（计划后续升级 TLS）；`usesCleartextTraffic=false` 不覆盖裸 socket。
- 生产库表结构依赖手工执行迁移脚本。
- 报表历史数据不会因口径调整自动重算，需重新导出。
