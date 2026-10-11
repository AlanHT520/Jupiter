package net.alan.jupiter.fx.controller;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
        loading.setStyle("-fx-text-fill: #6B7080; -fx-font-size: 13px;");
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
                Label empty = new Label("暂无任务");
                empty.setStyle("-fx-text-fill: #6B7080; -fx-font-size: 13px; -fx-padding: 8 0 0 0;");
                recentTaskBox.getChildren().add(empty);
            } else {
                for (Task t : recentTasks) {
                    HBox row = new HBox(12);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(10, 16, 10, 16));
                    row.setStyle(
                            "-fx-background-color: #1E222C; -fx-background-radius: 6; -fx-border-color: #2A2E3A; -fx-border-radius: 6;");

                    Label title = new Label(t.getTitle());
                    title.setStyle("-fx-text-fill: #CDD1D9; -fx-font-size: 13px;");
                    row.getChildren().add(title);

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    row.getChildren().add(spacer);

                    String status = t.getStatus() != null ? t.getStatus().name() : "";
                    Label statusLabel = new Label(switch (status) {
                        case "COMPLETED" -> "已完成";
                        case "CANCELLED" -> "已取消";
                        default -> "进行中";
                    });
                    statusLabel.setStyle(
                            switch (status) {
                                case "COMPLETED" ->
                                        "-fx-text-fill: #89B87C; -fx-font-size: 12px; -fx-padding: 2 10; -fx-background-color: rgba(137, 184, 124, 0.12); -fx-background-radius: 10;";
                                case "CANCELLED" ->
                                        "-fx-text-fill: #E0556A; -fx-font-size: 12px; -fx-padding: 2 10; -fx-background-color: rgba(224, 85, 106, 0.12); -fx-background-radius: 10;";
                                default ->
                                        "-fx-text-fill: #E5B857; -fx-font-size: 12px; -fx-padding: 2 10; -fx-background-color: rgba(229, 184, 87, 0.12); -fx-background-radius: 10;";
                            });
                    row.getChildren().add(statusLabel);

                    recentTaskBox.getChildren().add(row);
                }
            }
        }, ex -> {
            log.error("加载概览统计失败", ex);
            recentTaskBox.getChildren().clear();
            Label error = new Label("加载失败，请稍后再试");
            error.setStyle("-fx-text-fill: #E0556A; -fx-font-size: 13px;");
            recentTaskBox.getChildren().add(error);
        });
    }
}