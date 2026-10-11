package net.alan.jupiter.fx.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
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
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class MainController {

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
    public void initialize() {
        userLabel.setText(fxContext.getUsername());
        loadView("/fxml/dashboard.fxml");
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
                errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 14px; -fx-padding: 20;");
                contentPane.getChildren().setAll(errorLabel);
                return;
            }
        }
        contentPane.getChildren().setAll(node);
    }

    /* ===== 视图菜单 → 页面导航 ===== */
    @FXML private void onDashboard() { loadView("/fxml/dashboard.fxml"); }
    @FXML private void onRoster() { loadView("/fxml/roster.fxml"); }
    @FXML private void onTask() { loadView("/fxml/task.fxml"); }
    @FXML private void onCheckRecord() { loadView("/fxml/check-record.fxml"); }
    @FXML private void onLogViewer() { loadView("/fxml/log-viewer.fxml"); }
    @FXML private void onBuilding() { loadView("/fxml/building.fxml"); }
    @FXML private void onCounselor() { loadView("/fxml/counselor.fxml"); }

    private void refreshDashboardIfActive() {
        viewCache.remove("/fxml/dashboard.fxml");
        if ("/fxml/dashboard.fxml".equals(currentFxml)) {
            loadView("/fxml/dashboard.fxml");
        }
    }

    /* ===== 文件菜单 → 导入宿舍数据 ===== */
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
                    String msg = String.format("导入完成！\n导入: %d 条记录", result.getImported());
                    if (!result.getErrors().isEmpty()) {
                        msg += "\n\n错误: " + String.join(", ", result.getErrors());
                    }
                    DialogUtils.showInfo("导入结果", msg);
                    refreshDashboardIfActive();
                }
        );
    }

    /* ===== 文件菜单 → 导入工作人员名单 ===== */
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
                    String msg = String.format("导入完成！\n导入: %d 人", result.getImported());
                    if (!result.getErrors().isEmpty()) {
                        msg += "\n\n错误: " + String.join(", ", result.getErrors());
                    }
                    DialogUtils.showInfo("导入结果", msg);
                    loadView("/fxml/counselor.fxml");
                }
        );
    }

    /* ===== 文件菜单 → 导出查寝报表 ===== */
    @FXML
    private void onExportReport() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择导出目录");
        File dir = chooser.showDialog(contentPane.getScene().getWindow());
        if (dir == null) return;

        FxUtils.runAsync(
                () -> {
                    try {
                        return reportExportBundleService.exportBundle(null, dir.getAbsolutePath())
                                .toAbsolutePath().toString();
                    } catch (IOException e) {
                        throw new RuntimeException("导出失败，请重试", e);
                    }
                },
                savedPath -> {
                    DialogUtils.showInfo("导出成功", "查寝报表已导出到:\n" + savedPath);
                }
        );
    }

    /* ===== 文件菜单 → 退出 ===== */
    @FXML
    private void onExit() {
        boolean confirm = DialogUtils.showConfirm("退出系统", "确定要退出 Jupiter 吗？");
        if (confirm) {
            Platform.exit();
        }
    }

    /* ===== 帮助菜单 → 关于 ===== */
    @FXML
    private void onAbout() {
        DialogUtils.showInfo("关于 Jupiter",
                "Jupiter 寝室管理系统\n版本：v0.8.5\n\n"
                        + "本项目包含 PC 桌面端与 Android 移动端\n"
                        + "当前用户：" + fxContext.getUsername() + "（" + fxContext.getRole() + "）\n\n"
                        + "©2026  本软件著作权归张浩天所有。\n"
                        + "未经作者事先书面授权，任何个人或组织不得使用、\n"
                        + "复制、修改或分发本软件，不得将其用于商业用途，\n"
                        + "亦不得将其作为任何公开展示的组成部分。");
    }
}