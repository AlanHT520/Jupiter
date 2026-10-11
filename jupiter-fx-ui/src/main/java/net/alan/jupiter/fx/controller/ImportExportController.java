package net.alan.jupiter.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.application.report.ExportReportService;
import net.alan.jupiter.application.report.ReportExportBundleService;
import net.alan.jupiter.application.roster.ImportClassRosterService;
import net.alan.jupiter.fx.util.DialogUtils;
import net.alan.jupiter.fx.util.FxUtils;
import net.alan.jupiter.domain.repository.TaskRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class ImportExportController {

    private final ImportClassRosterService importClassRosterService;
    private final ExportReportService exportReportService;
    private final ReportExportBundleService reportExportBundleService;
    private final TaskRepository taskRepository;

    private static final FileChooser.ExtensionFilter EXCEL_FILTER =
            new FileChooser.ExtensionFilter("Excel 文件 (*.xlsx)", "*.xlsx");

    @FXML
    private TextArea logArea;

    @FXML
    private Label statusLabel;

    @FXML
    private Button importBtn;

    @FXML
    private Button exportBtn;

    @FXML
    public void initialize() {
        statusLabel.setText("就绪");
    }

    @FXML
    private void onImport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择花名册文件");
        chooser.getExtensionFilters().add(EXCEL_FILTER);
        File file = chooser.showOpenDialog(importBtn.getScene().getWindow());
        if (file == null) return;

        statusLabel.setText("正在导入花名册");
        logArea.clear();

        FxUtils.runAsync(() -> {
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                return importClassRosterService.importClassRoster(data, file.getName());
            } catch (IOException e) {
                throw new RuntimeException("读取文件失败，请重试", e);
            }
        }, result -> {
            appendLog("===== 花名册导入结果 =====");
                appendLog("文件名: " + file.getName());
                appendLog("清除旧数据后导入: " + result.getImported() + " 条");
            if (!result.getErrors().isEmpty()) {
                appendLog("错误: " + result.getErrors().size() + " 条");
                result.getErrors().forEach(e -> appendLog("  - " + e));
            }
            statusLabel.setText("导入完成: 共 " + result.getImported() + " 条");
        });
    }

    @FXML
    private void onExport() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择导出目录");
        File dir = chooser.showDialog(exportBtn.getScene().getWindow());
        if (dir == null) return;

        statusLabel.setText("正在生成全量报表（所有已完成任务）...");
        logArea.clear();
        appendLog("===== 报表导出开始 =====");
        appendLog("导出所有已完成查寝任务的数据报表");

        FxUtils.runAsync(() -> {
            try {
                Path bundleDir = reportExportBundleService.exportBundle(null, dir.getAbsolutePath());
                return bundleDir.toAbsolutePath().toString();
            } catch (IOException e) {
                throw new RuntimeException("导出失败，请重试", e);
            }
        }, savedPath -> {
            appendLog("===== 全量报表导出完成 =====");
            appendLog("导出目录: " + savedPath);
            statusLabel.setText("全量报表已导出");
            DialogUtils.showInfo("导出成功", "全量查寝报表已保存到:\n" + savedPath);
        });
    }

    private void appendLog(String text) {
        logArea.appendText(text + "\n");
    }
}