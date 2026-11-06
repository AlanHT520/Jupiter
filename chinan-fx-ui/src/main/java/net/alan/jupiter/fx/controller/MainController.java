package net.alan.jupiter.fx.controller;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.counselor.ImportCounselorService;
import net.alan.jupiter.application.report.ReportExportBundleService;
import net.alan.jupiter.application.roster.ImportClassRosterService;
import net.alan.jupiter.fx.config.FxContext;
import net.alan.jupiter.fx.util.DialogUtils;
import net.alan.jupiter.fx.util.FxUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 桌面端外壳：顶部路径 + 编号导航 + 底部状态栏。
 * 状态栏信息全部是只读运行时事实（DB / TCP 连接数 / PROFILE / USER / ROLE / 版本）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MainController {

    private record ViewMeta(String fxml, String path, String title, String subtitle) {}

    private static final List<ViewMeta> VIEWS = List.of(
            new ViewMeta("/fxml/dashboard.fxml", "/dashboard", "仪表盘", "实时统计与近期任务"),
            new ViewMeta("/fxml/task.fxml", "/tasks", "任务管理", "创建、分配、开始与完成任务"),
            new ViewMeta("/fxml/task-template.fxml", "/templates", "任务预分配", "保存一批任务分配，一键按日期重新分配"),
            new ViewMeta("/fxml/check-record.fxml", "/records", "查寝记录", "任务内的逐人查寝结果"),
            new ViewMeta("/fxml/roster.fxml", "/roster", "花名册", "按学号、姓名、楼栋、班级检索"),
            new ViewMeta("/fxml/counselor.fxml", "/counselors", "工作人员", "查宿人员（含学生会）账号、状态与密码重置"),
            new ViewMeta("/fxml/building.fxml", "/buildings", "楼栋结构", "各宿舍楼房间与学生规模"),
            new ViewMeta("/fxml/import-export.fxml", "/io", "导入导出", "导入导出命令面板与进度"),
            new ViewMeta("/fxml/registration-management.fxml", "/registration", "注册审核", "注册开关与申请审核"),
            new ViewMeta("/fxml/log-viewer.fxml", "/logs", "日志查看", "实时日志与错误排查"),
            new ViewMeta("/fxml/report.fxml", "/reports", "报表", "楼栋、房间与查寝记录规模"),
            new ViewMeta("/fxml/system-config.fxml", "/config", "系统配置", "运行环境与连接状态")
    );

    private static final String DEFAULT_VIEW = "/fxml/dashboard.fxml";

    private final ApplicationContext context;
    private final FxContext fxContext;
    private final ImportCounselorService importCounselorService;
    private final ImportClassRosterService importClassRosterService;
    private final ReportExportBundleService reportExportBundleService;

    private final Map<String, Node> viewCache = new HashMap<>();
    private String currentFxml;

    @Value("${Chinan.start-view:/fxml/dashboard.fxml}")
    private String startView;

    @Value("${Chinan.version:v1.0.0}")
    private String appVersion;

    @Value("${Chinan.tcp.port:9090}")
    private int tcpPort;

    @Value("${Chinan.tcp.enabled:true}")
    private boolean tcpEnabled;

    @Value("${spring.profiles.active:dev}")
    private String profile;

    @Value("${spring.datasource.url:jdbc:h2:mem:chinan}")
    private String datasourceUrl;

    @FXML
    private StackPane contentPane;

    @FXML
    private Label userLabel;

    @FXML
    private Label avatarLabel;

    @FXML
    private Label sideUserName;

    @FXML
    private Label pageTitleLabel;

    @FXML
    private Label pageSubtitleLabel;

    @FXML
    private Label pathLabel;

    @FXML
    private Label connDot;

    @FXML
    private Label connLabel;

    @FXML
    private Label clockLabel;

    @FXML
    private Label dbLabel;

    @FXML
    private Label tcpStatusLabel;

    @FXML
    private Label profileLabel;

    @FXML
    private Label statusUserLabel;

    @FXML
    private Label roleLabel;

    @FXML
    private Label versionLabel;

    @FXML
    private ToggleGroup navGroup;

    @FXML
    public void initialize() {
        String name = fxContext.getUsername() != null && !fxContext.getUsername().isBlank()
                ? fxContext.getUsername() : "桌面端";
        String role = fxContext.getRole() != null ? fxContext.getRole() : "ADMIN";

        userLabel.setText(name);
        statusUserLabel.setText("用户 " + name);
        roleLabel.setText("角色 " + role);
        sideUserName.setText("用户 " + name);
        avatarLabel.setText("QN");
        versionLabel.setText(appVersion);
        profileLabel.setText("环境 " + profile);
        dbLabel.setText("数据库: " + dbKind());
        connLabel.setText("端口 :" + tcpPort);

        applyTcpState();
        startClockAndPolling();
        String initialView = startView != null && !startView.isBlank() ? startView : DEFAULT_VIEW;
        log.info("桌面外壳启动视图: {}（配置值 {}）", initialView, startView);
        loadView(initialView);
    }

    private void startClockAndPolling() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        clockLabel.setText(LocalTime.now().format(fmt));

        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1),
                e -> clockLabel.setText(LocalTime.now().format(fmt))));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();

        Timeline connPoll = new Timeline(new KeyFrame(Duration.seconds(2), e -> applyTcpState()));
        connPoll.setCycleCount(Animation.INDEFINITE);
        connPoll.play();
    }

    private void applyTcpState() {
        boolean on = tcpEnabled;
        connDot.setText(on ? "●" : "○");
        connDot.getStyleClass().remove("offline");
        if (!on) {
            connDot.getStyleClass().add("offline");
            connLabel.setText("端口 :off");
            tcpStatusLabel.setText("TCP 端口 :off");
        } else {
            connLabel.setText("端口 :" + tcpPort);
            tcpStatusLabel.setText("TCP 端口 :" + tcpPort + " ● " + activeConnections() + " 连接");
        }
    }

    /**
     * TCP 连接数放在 boot 模块，桌面端不直接依赖，用只读反射取计数；取不到就显示 0。
     */
    private int activeConnections() {
        try {
            if (!context.containsBean("tcpServer")) {
                return 0;
            }
            Object bean = context.getBean("tcpServer");
            Method method = bean.getClass().getMethod("getActiveConnections");
            Object value = method.invoke(bean);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String dbKind() {
        String url = datasourceUrl == null ? "" : datasourceUrl.toLowerCase();
        if (url.contains("h2")) return "h2";
        if (url.contains("mysql") || url.contains("mariadb")) return "mysql";
        if (url.contains("postgres")) return "postgres";
        if (url.contains("sqlserver")) return "sqlserver";
        return "db";
    }

    @FXML
    private void onNav(ActionEvent event) {
        if (event.getSource() instanceof ToggleButton button
                && button.getUserData() instanceof String fxml) {
            loadView(fxml);
        }
    }

    /** 页面内跳转（任务预分配页 → 任务管理页）。 */
    @org.springframework.context.event.EventListener
    public void onNavigateView(net.alan.jupiter.fx.event.NavigateViewEvent event) {
        javafx.application.Platform.runLater(() -> loadView(event.fxml()));
    }

    private void loadView(String fxml) {
        currentFxml = fxml;
        // 仪表盘每次进入都重新拉数据，避免导入花名册后数字不刷新
        if (DEFAULT_VIEW.equals(fxml)) {
            viewCache.remove(fxml);
        }
        Node node = viewCache.get(fxml);
        if (node == null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
                loader.setControllerFactory(context::getBean);
                node = loader.load();
                viewCache.put(fxml, node);
            } catch (Exception e) {
                log.error("加载页面失败: {}", fxml, e);
                Label errorLabel = new Label("页面加载失败，请稍后再试");
                errorLabel.getStyleClass().add("error-state");
                contentPane.getChildren().setAll(errorLabel);
                return;
            }
        }
        contentPane.getChildren().setAll(node);
        applyViewMeta(fxml);
    }

    private void applyViewMeta(String fxml) {
        VIEWS.stream()
                .filter(view -> view.fxml().equals(fxml))
                .findFirst()
                .ifPresent(view -> {
                    pageTitleLabel.setText(view.title());
                    pageSubtitleLabel.setText(view.subtitle());
                    pathLabel.setText("寝安 / " + view.title());
                });
        if (navGroup != null) {
            for (Toggle toggle : navGroup.getToggles()) {
                if (fxml.equals(toggle.getUserData())) {
                    toggle.setSelected(true);
                    break;
                }
            }
        }
    }

    private void refreshDashboardIfActive() {
        viewCache.remove("/fxml/dashboard.fxml");
        if (DEFAULT_VIEW.equals(currentFxml)) {
            loadView(DEFAULT_VIEW);
        }
    }

    /* ===== 文件 → 导入宿舍数据 ===== */
    @FXML
    private void onImportRoster() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择花名册文件");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel 文件 (*.xlsx)", "*.xlsx"));
        File file = chooser.showOpenDialog(contentPane.getScene().getWindow());
        if (file == null) return;

        FxUtils.runAsync(
                () -> {
                    try {
                        byte[] data = Files.readAllBytes(file.toPath());
                        return importClassRosterService.importClassRoster(data, file.getName());
                    } catch (IOException e) {
                        throw new RuntimeException("读取文件失败，请重试", e);
                    }
                },
                result -> {
                    String msg = String.format(
                            "导入完成！\n导入: %d 条\n更新: %d 条\n停用: %d 条\n跳过: %d 条",
                            result.getImported(), result.getUpdated(),
                            result.getDisabled(), result.getSkipped());
                    if (!result.getErrors().isEmpty()) {
                        msg += "\n\n错误: " + String.join(", ", result.getErrors());
                    }
                    DialogUtils.showInfo("导入结果", msg);
                    refreshDashboardIfActive();
                }
        );
    }

    /* ===== 文件 → 导入工作人员名单 ===== */
    @FXML
    private void onImportCounselors() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择工作人员名单文件");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel 文件 (*.xlsx)", "*.xlsx"));
        File file = chooser.showOpenDialog(contentPane.getScene().getWindow());
        if (file == null) return;

        FxUtils.runAsync(
                () -> {
                    try {
                        byte[] data = Files.readAllBytes(file.toPath());
                        return importCounselorService.importCounselors(data);
                    } catch (IOException e) {
                        throw new RuntimeException("读取文件失败，请重试", e);
                    }
                },
                result -> {
                    String msg = String.format(
                            "导入完成！\n导入: %d 人\n更新: %d 人\n停用（未在本次名单中）: %d 人",
                            result.getImported(), result.getUpdated(), result.getDisabled());
                    if (!result.getErrors().isEmpty()) {
                        msg += "\n\n错误: " + String.join(", ", result.getErrors());
                    }
                    DialogUtils.showInfo("导入结果", msg);
                    loadView("/fxml/counselor.fxml");
                }
        );
    }

    /* ===== 文件 → 导出查寝报表 ===== */
    @FXML
    private void onExportReport() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择导出目录");
        File dir = chooser.showDialog(contentPane.getScene().getWindow());
        if (dir == null) return;

        FxUtils.runAsync(
                () -> {
                    try {
                        return reportExportBundleService.exportBundle(null, dir.getAbsolutePath());
                    } catch (IOException e) {
                        throw new RuntimeException("导出失败，请重试", e);
                    }
                },
                result -> {
                    if (result.hasFailures()) {
                        DialogUtils.showWarn("导出完成（部分失败）",
                                "报表已导出到:\n" + result.outputDir()
                                        + "\n\n" + result.failureSummary());
                    } else {
                        DialogUtils.showInfo("导出成功",
                                "报表已导出到: " + result.outputDir());
                    }
                }
        );
    }

    /* ===== 文件 → 退出 ===== */
    @FXML
    private void onExit() {
        if (DialogUtils.showConfirm("退出系统", "确定要退出寝安吗？")) {
            Platform.exit();
        }
    }

    /* ===== 帮助 → 关于 ===== */
    @FXML
    private void onAbout() {
        DialogUtils.showInfo("关于寝安",
                "寝安 QIN'AN — 宿舍查寝管理系统\n版本：" + appVersion + "\n\n"
                        + "JavaFX 桌面管理端 + Android 查宿端\n"
                        + "当前用户：" + fxContext.getUsername() + "（" + fxContext.getRole() + "）\n\n"
                        + "©2026  本软件著作权归张浩天所有。");
    }
}
