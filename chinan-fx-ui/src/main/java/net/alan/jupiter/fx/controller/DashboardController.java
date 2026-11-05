package net.alan.jupiter.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.dashboard.DashboardService;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.fx.util.FxUtils;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;

    @FXML
    private Label buildingCountLabel;

    @FXML
    private Label roomCountLabel;

    @FXML
    private Label studentCountLabel;

    @FXML
    private Label activeTaskLabel;

    @FXML
    private VBox recentTaskBox;

    @FXML
    public void initialize() {
        loadStats();
    }

    private void loadStats() {
        recentTaskBox.getChildren().clear();
        Label loading = new Label("加载中...");
        loading.getStyleClass().add("muted");
        recentTaskBox.getChildren().add(loading);

        FxUtils.runAsync(() -> {
            DashboardService.DashboardStats stats = dashboardService.getStats();
            List<Task> recentTasks = dashboardService.getRecentTasks();
            return new Object[]{stats, recentTasks};
        }, result -> {
            DashboardService.DashboardStats stats = (DashboardService.DashboardStats) result[0];
            @SuppressWarnings("unchecked")
            List<Task> recentTasks = (List<Task>) result[1];

            buildingCountLabel.setText(String.valueOf(stats.getBuildingCount()));
            roomCountLabel.setText(String.valueOf(stats.getRoomCount()));
            studentCountLabel.setText(String.valueOf(stats.getStudentCount()));
            activeTaskLabel.setText(String.valueOf(stats.getActiveTaskCount()));

            recentTaskBox.getChildren().clear();
            if (recentTasks.isEmpty()) {
                recentTaskBox.getChildren().add(net.alan.jupiter.fx.util.EmptyStates.block(
                        "还没有查寝任务",
                        "在左侧「查寝任务」中创建任务后，最近 5 条会显示在这里",
                        FontAwesomeSolid.CLIPBOARD_LIST));
                return;
            }
            for (Task task : recentTasks) {
                recentTaskBox.getChildren().add(buildTaskRow(task));
            }
        }, ex -> {
            log.error("加载概览统计失败", ex);
            recentTaskBox.getChildren().clear();
            Label error = new Label("加载失败，请稍后再试");
            error.getStyleClass().add("error-state");
            recentTaskBox.getChildren().add(error);
        });
    }

    private HBox buildTaskRow(Task task) {
        HBox row = new HBox();
        row.getStyleClass().add("list-row");

        VBox textBox = new VBox(3);
        Label title = new Label(task.getTitle() != null ? task.getTitle() : "(未命名任务)");
        title.getStyleClass().add("list-title");

        StringBuilder meta = new StringBuilder();
        if (task.getTaskDate() != null) {
            meta.append(task.getTaskDate());
        }
        if (task.getBuildingName() != null && !task.getBuildingName().isBlank()) {
            if (!meta.isEmpty()) meta.append(" · ");
            meta.append(task.getBuildingName());
        }
        if (task.getFloorNumber() != null && !task.getFloorNumber().isBlank()) {
            meta.append(" · ").append(task.getFloorNumber()).append("层");
        }
        if (task.getTotalRooms() != null) {
            meta.append(" · 房间 ").append(task.getCheckedRooms() != null ? task.getCheckedRooms() : 0)
                    .append("/").append(task.getTotalRooms());
        }
        Label metaLabel = new Label(meta.toString());
        metaLabel.getStyleClass().add("list-meta");
        textBox.getChildren().addAll(title, metaLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(textBox, spacer, statusBadge(task));
        return row;
    }

    private Label statusBadge(Task task) {
        String status = task.getStatus() != null ? task.getStatus().name() : "";
        Label badge = new Label();
        badge.getStyleClass().add("badge");
        switch (status) {
            case "COMPLETED" -> {
                badge.setText("已完成");
                badge.getStyleClass().add("badge-success");
            }
            case "CANCELLED" -> {
                badge.setText("已取消");
                badge.getStyleClass().add("badge-danger");
            }
            case "PENDING" -> {
                badge.setText("待开始");
                badge.getStyleClass().add("badge-muted");
            }
            default -> {
                badge.setText("进行中");
                badge.getStyleClass().add("badge-warning");
            }
        }
        return badge;
    }
}
