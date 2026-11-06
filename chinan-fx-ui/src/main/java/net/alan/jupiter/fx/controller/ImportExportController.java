package net.alan.jupiter.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.application.counselor.ImportCounselorService;
import net.alan.jupiter.application.report.ReportExportBundleService;
import net.alan.jupiter.application.roster.ImportClassRosterService;
import net.alan.jupiter.fx.util.DialogUtils;
import net.alan.jupiter.fx.util.FxUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 导入导出 · 命令面板：左侧命令、右侧事件日志与进度。
 * 所有耗时操作都在后台线程，日志按 [HH:mm:ss] 追加。
 */
@Component
@RequiredArgsConstructor
public class ImportExportController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final FileChooser.ExtensionFilter EXCEL_FILTER =
            new FileChooser.ExtensionFilter("Excel 文件 (*.xlsx)", "*.xlsx");

    private final ImportClassRosterService importClassRosterService;
    private final ImportCounselorService importCounselorService;
    private final ReportExportBundleService reportExportBundleService;

    @FXML
    private TextArea logArea;

    @FXML
    private Label statusLabel;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Button cmdImportRoster;

    @FXML
    private Button cmdImportCounselors;

    @FXML
    private Button cmdExport;

    @FXML
    public void initialize() {
        statusLabel.setText("● IDLE");
        progressBar.setVisible(false);
        event("SYS", "命令面板就绪，选择左侧命令开始");
    }

    @FXML
    private void onImportRoster() {
        File file = chooseExcel("选择花名册文件", cmdImportRoster);
        if (file == null) return;

        run("IMPORT_ROSTER", "导入花名册 " + file.getName(), () -> {
            byte[] data = readFile(file);
            return importClassRosterService.importClassRoster(data, file.getName());
        }, result -> {
            event("INFO", "imported=" + result.getImported()
                    + " updated=" + result.getUpdated()
                    + " disabled=" + result.getDisabled()
                    + " skipped=" + result.getSkipped()
                    + " classes=" + result.getClassMapped());
            if (!result.getErrors().isEmpty()) {
                event("WARN", "errors=" + result.getErrors().size());
                result.getErrors().forEach(error -> event("WARN", error));
            }
            finish("● OK");
        });
    }

    @FXML
    private void onImportCounselors() {
        File file = chooseExcel("选择工作人员名单文件", cmdImportCounselors);
        if (file == null) return;

        run("IMPORT_STAFF", "导入工作人员 " + file.getName(), () -> {
            byte[] data = readFile(file);
            return importCounselorService.importCounselors(data);
        }, result -> {
            event("INFO", "imported=" + result.getImported()
                    + " updated=" + result.getUpdated()
                    + " deleted=" + result.getDeleted());
            if (!result.getErrors().isEmpty()) {
                result.getErrors().forEach(error -> event("WARN", error));
            }
            finish("● OK");
        });
    }

    @FXML
    private void onExport() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择导出目录");
        File dir = chooser.showDialog(cmdExport.getScene().getWindow());
        if (dir == null) return;

        run("EXPORT_BUNDLE", "全量聚合导出 → " + dir.getAbsolutePath(), () -> {
            try {
                return reportExportBundleService.exportBundle(null, dir.getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException("导出失败，请重试", e);
            }
        }, result -> {
            event("INFO", "输出目录: " + result.outputDir());
            if (result.hasFailures()) {
                event("WARN", result.failureSummary());
                DialogUtils.showWarn("导出完成（部分失败）", result.failureSummary());
            } else {
                event("INFO", "导出完成，无失败项");
                DialogUtils.showInfo("导出成功", "全量查寝报表已保存到:\n" + result.outputDir());
            }
            finish("● OK");
        });
    }

    @FXML
    private void onClearLog() {
        logArea.clear();
        event("SYS", "日志已清空");
    }

    /* ===================== 内部 ===================== */

    private File chooseExcel(String title, Button anchor) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(EXCEL_FILTER);
        return chooser.showOpenDialog(anchor.getScene().getWindow());
    }

    private byte[] readFile(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败，请重试", e);
        }
    }

    private <T> void run(String kind, String startMessage,
                         java.util.function.Supplier<T> task,
                         java.util.function.Consumer<T> onSuccess) {
        event(kind, startMessage);
        statusLabel.setText("● RUNNING");
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        FxUtils.runAsync(task, result -> {
            progressBar.setVisible(false);
            onSuccess.accept(result);
        }, error -> {
            progressBar.setVisible(false);
            statusLabel.setText("● FAILED");
            event("ERROR", error == null ? "未知错误" : String.valueOf(error.getMessage()));
            DialogUtils.showError("命令执行失败", "操作失败，请查看日志或稍后重试");
        });
    }

    private void finish(String status) {
        statusLabel.setText(status);
        event("DONE", "命令结束");
    }

    private void event(String kind, String message) {
        logArea.appendText("[" + LocalTime.now().format(TS) + "] " + kind + "  " + message + "\n");
    }
}
