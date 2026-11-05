package net.alan.jupiter.fx.controller;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.counselor.ImportCounselorService;
import net.alan.jupiter.application.report.ReportExportBundleService;
import net.alan.jupiter.application.roster.ImportClassRosterService;
import net.alan.jupiter.fx.config.FxContext;
import net.alan.jupiter.fx.util.DialogUtils;
import net.alan.jupiter.fx.util.FxUtils;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class MainController {

    private record ViewMeta(String fxml, String title, String subtitle) {}

    private static final List<ViewMeta> VIEWS = List.of(
            new ViewMeta("/fxml/dashboard.fxml", "首页概览", "当前数据概况与近期查寝任务"),
            new ViewMeta("/fxml/roster.fxml", "学生名册", "按学号、姓名、楼栋、班级检索学生"),
            new ViewMeta("/fxml/task.fxml", "查寝任务", "创建、分配、开始与完成任务"),
            new ViewMeta("/fxml/check-record.fxml", "查寝记录", "查看任务内的逐人查寝结果"),
            new ViewMeta("/fxml/report.fxml", "数据报表", "楼栋、房间与查寝记录规模"),
            new ViewMeta("/fxml/building.fxml", "楼栋总览", "各宿舍楼房间与学生规模"),
            new ViewMeta("/fxml/counselor.fxml", "查宿者管理", "查宿员账号、状态与密码重置"),
            new ViewMeta("/fxml/registration-management.fxml", "注册管理", "注册开关与申请审核"),
            new ViewMeta("/fxml/log-viewer.fxml", "系统日志", "运行日志与错误排查")
    );

    private static final String DEFAULT_VIEW = "/fxml/dashboard.fxml";

    private static final Map<String, FontAwesomeSolid> NAV_ICONS = Map.of(
            "/fxml/dashboard.fxml", FontAwesomeSolid.HOME,
            "/fxml/roster.fxml", FontAwesomeSolid.USERS,
            "/fxml/task.fxml", FontAwesomeSolid.TASKS,
            "/fxml/check-record.fxml", FontAwesomeSolid.CLIPBOARD_LIST,
            "/fxml/report.fxml", FontAwesomeSolid.CHART_BAR,
            "/fxml/building.fxml", FontAwesomeSolid.BUILDING,
            "/fxml/counselor.fxml", FontAwesomeSolid.USER,
            "/fxml/registration-management.fxml", FontAwesomeSolid.EDIT,
            "/fxml/log-viewer.fxml", FontAwesomeSolid.LIST
    );

    private final ApplicationContext context;
    private final FxContext fxContext;
    private final ImportCounselorService importCounselorService;
    private final ImportClassRosterService importClassRosterService;
    private final ReportExportBundleService reportExportBundleService;

    private final Map<String, Node> viewCache = new HashMap<>();
    private String currentFxml;

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
    private ToggleGroup navGroup;

    /** 启动页（便于演示/截图/回归验证，默认首页概览）。 */
    @Value("${Chinan.start-view:/fxml/dashboard.fxml}")
    private String startView;

    @FXML
    public void initialize() {
        String name = fxContext.getUsername() != null && !fxContext.getUsername().isBlank()
                ? fxContext.getUsername() : "桌面端";
        String role = fxContext.getRole() != null ? fxContext.getRole() : "ADMIN";
        userLabel.setText(name);
        sideUserName.setText(name + " · " + role);
        avatarLabel.setText(name.substring(0, 1).toUpperCase());
        applyNavIcons();
        loadView(startView != null && !startView.isBlank() ? startView : DEFAULT_VIEW);
    }

    private void applyNavIcons() {
        if (navGroup == null) {
            return;
        }
        for (Toggle toggle : navGroup.getToggles()) {
            if (!(toggle instanceof ToggleButton button)) {
                continue;
            }
            FontAwesomeSolid icon = NAV_ICONS.get(button.getUserData());
            if (icon == null) {
                continue;
            }
            FontIcon graphic = new FontIcon(icon);
            graphic.setIconSize(14);
            graphic.getStyleClass().add("nav-icon");
            button.setGraphic(graphic);
        }
    }

    @FXML
    private void onNav(ActionEvent event) {
        if (event.getSource() instanceof ToggleButton button
                && button.getUserData() instanceof String fxml) {
            loadView(fxml);
        }
    }

    private void loadView(String fxml) {
        currentFxml = fxml;
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
                            "导入完成！\n导入: %d 人\n更新: %d 人\n删除: %d 人",
                            result.getImported(), result.getUpdated(), result.getDeleted());
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
        if (DialogUtils.showConfirm("退出系统", "确定要退出 Chinan 吗？")) {
            Platform.exit();
        }
    }

    /* ===== 帮助 → 关于 ===== */
    @FXML
    private void onAbout() {
        DialogUtils.showInfo("关于 Chinan",
                "Chinan 寝室管理系统\n版本：v-0.8.9\n\n"
                        + "JavaFX 桌面管理端 + Android 查宿端\n"
                        + "当前用户：" + fxContext.getUsername() + "（" + fxContext.getRole() + "）\n\n"
                        + "©2026  本软件著作权归张浩天所有。");
    }
}
