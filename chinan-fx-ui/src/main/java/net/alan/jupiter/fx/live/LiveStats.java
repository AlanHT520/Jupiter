package net.alan.jupiter.fx.live;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.check.CheckRecordSubmittedEvent;
import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.domain.repository.CheckRecordRepository;
import net.alan.jupiter.domain.repository.TaskRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 桌面实时状态：手机/其他端提交查寝 → 应用事件 → 后台重算 → JavaFX Property 自动刷新。
 * 事件流同时供仪表盘「实时事件」与日志页使用，不做轮询。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LiveStats {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_EVENTS = 60;

    private final TaskRepository taskRepository;
    private final CheckRecordRepository checkRecordRepository;

    private final IntegerProperty shouldCount = new SimpleIntegerProperty(0);
    private final IntegerProperty actualCount = new SimpleIntegerProperty(0);
    private final IntegerProperty leaveCount = new SimpleIntegerProperty(0);
    private final IntegerProperty absentCount = new SimpleIntegerProperty(0);
    private final DoubleProperty rate = new SimpleDoubleProperty(0);
    private final ObjectProperty<LocalDateTime> updatedAt = new SimpleObjectProperty<>();

    /** 最新事件在最前面，供仪表盘 / 日志页直接绑定。 */
    private final ObservableList<String> events = FXCollections.observableArrayList();

    public IntegerProperty shouldCountProperty() {
        return shouldCount;
    }

    public IntegerProperty actualCountProperty() {
        return actualCount;
    }

    public IntegerProperty leaveCountProperty() {
        return leaveCount;
    }

    public IntegerProperty absentCountProperty() {
        return absentCount;
    }

    public DoubleProperty rateProperty() {
        return rate;
    }

    public ObjectProperty<LocalDateTime> updatedAtProperty() {
        return updatedAt;
    }

    public ObservableList<String> getEvents() {
        return events;
    }

    /** 收到任意查寝提交事件：刷新数字 + 追加事件行。 */
    @EventListener
    public void onCheckSubmitted(CheckRecordSubmittedEvent event) {
        String statuses = event.getRecords() == null ? "-" : event.getRecords().stream()
                .map(r -> r.getCheckStatus() == null ? "-" : r.getCheckStatus().getLabel())
                .distinct()
                .reduce((a, b) -> a + "/" + b)
                .orElse("-");
        String line = LocalDateTime.now().format(TS)
                + "  EVENT  CheckRecordSubmitted"
                + "  task=" + shortId(event.getTaskId())
                + "  room=" + shortId(event.getRoomId())
                + "  by=" + (event.getSubmittedBy() == null ? "-" : event.getSubmittedBy())
                + "  " + statuses;
        pushEvent(line);
        refreshAsync();
    }

    /** 后台线程重算，回 JavaFX 线程只改 property。 */
    public void refreshAsync() {
        CompletableFuture.supplyAsync(this::compute)
                .thenAccept(stats -> Platform.runLater(() -> apply(stats)))
                .exceptionally(error -> {
                    log.warn("实时统计刷新失败: {}", error.getMessage());
                    return null;
                });
    }

    private void pushEvent(String line) {
        Platform.runLater(() -> {
            events.add(0, line);
            while (events.size() > MAX_EVENTS) {
                events.remove(events.size() - 1);
            }
            updatedAt.set(LocalDateTime.now());
        });
    }

    private void apply(Stats stats) {
        shouldCount.set(stats.should);
        actualCount.set(stats.actual);
        leaveCount.set(stats.leave);
        absentCount.set(stats.absent);
        rate.set(stats.rate);
        if (updatedAt.get() == null) {
            updatedAt.set(LocalDateTime.now());
        }
    }

    private Stats compute() {
        List<Task> tasks = taskRepository.findByTaskDate(LocalDate.now());
        List<String> taskIds = tasks.stream().map(Task::getId).toList();
        List<CheckRecord> records = taskIds.isEmpty()
                ? List.of()
                : checkRecordRepository.findByTaskIdIn(taskIds);

        int should = 0, actual = 0, leave = 0, absent = 0;
        for (CheckRecord record : records) {
            CheckStatus status = record.getCheckStatus() != null
                    ? record.getCheckStatus() : record.getPresetStatus();
            if (status == null) {
                continue;
            }
            if (status != CheckStatus.DAY_STUDENT && status != CheckStatus.LONG_SICK_LEAVE) {
                should++;
            }
            switch (status) {
                case PRESENT -> actual++;
                case PERSONAL_LEAVE, SICK_LEAVE -> leave++;
                case ABSENT -> absent++;
                default -> { }
            }
        }
        double rateValue = should == 0 ? 0 : Math.round(actual * 10000.0 / should) / 100.0;
        return new Stats(should, actual, leave, absent, rateValue);
    }

    private String shortId(String id) {
        if (id == null) return "-";
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private record Stats(int should, int actual, int leave, int absent, double rate) {}
}
