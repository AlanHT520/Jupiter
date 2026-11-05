package net.alan.jupiter.fx.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.domain.model.CheckRecordModifyLog;
import net.alan.jupiter.domain.repository.CheckRecordModifyLogRepository;
import net.alan.jupiter.fx.util.FxUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LogViewerController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long POLL_SECONDS = 3;

    private final CheckRecordModifyLogRepository modifyLogRepository;

    private Timeline pollTimer;

    @FXML
    private TextArea logArea;

    @FXML
    public void initialize() {
        loadLogs();
        startPolling();
    }

    private void startPolling() {
        if (pollTimer != null) return;
        pollTimer = new Timeline(new KeyFrame(Duration.seconds(POLL_SECONDS), e -> loadLogs()));
        pollTimer.setCycleCount(Timeline.INDEFINITE);
        pollTimer.play();
    }

    @FXML
    private void onRefresh() {
        loadLogs();
    }

    private void loadLogs() {
        FxUtils.runAsync(
                () -> {
                    List<CheckRecordModifyLog> logs = modifyLogRepository.findAll(
                            PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "modifiedAt"))
                    ).getContent();
                    return formatLogs(logs);
                },
                result -> logArea.setText(result),
                error -> {
                    log.error("加载日志失败", error);
                    logArea.setText("加载日志失败: " + error.getMessage());
                }
        );
    }

    private static String formatLogs(List<CheckRecordModifyLog> logs) {
        if (logs.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(" Chinan 系统操作日志");
        sb.append("\n");
        sb.append("═".repeat(80));
        sb.append("\n\n");

        for (CheckRecordModifyLog entry : logs) {
            String time = entry.getModifiedAt() != null
                    ? entry.getModifiedAt().format(FMT)
                    : "----";
            String who = entry.getModifiedBy() != null ? entry.getModifiedBy() : "-";
            String field = entry.getFieldName() != null ? entry.getFieldName() : "-";
            String oldV = entry.getOldValue() != null ? entry.getOldValue() : "(空)";
            String newV = entry.getNewValue() != null ? entry.getNewValue() : "(空)";
            String reason = entry.getReason() != null ? entry.getReason() : "";

            sb.append(String.format("[%s] %-8s | %-12s | %-14s -> %-14s | %s\n",
                    time, who, field, trunc(oldV, 14), trunc(newV, 14), reason));
        }

        sb.append("\n");
        sb.append("═".repeat(80));
        sb.append(String.format("\n 共 %d 条日志记录", logs.size()));

        return sb.toString();
    }

    private static String trunc(String s, int max) {
        if (s == null) return "-";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}