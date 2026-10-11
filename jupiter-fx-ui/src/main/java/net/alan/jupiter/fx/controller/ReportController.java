package net.alan.jupiter.fx.controller;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.application.dashboard.DashboardService;
import net.alan.jupiter.fx.util.FxUtils;
import net.alan.jupiter.domain.repository.CheckRecordRepository;
import net.alan.jupiter.domain.repository.DormBuildingRepository;
import net.alan.jupiter.domain.repository.RoomRepository;
import net.alan.jupiter.domain.repository.StudentRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ReportController {

    private final DashboardService dashboardService;
    private final DormBuildingRepository buildingRepository;
    private final RoomRepository roomRepository;
    private final StudentRepository studentRepository;
    private final CheckRecordRepository checkRecordRepository;

    @FXML
    private Label totalBuildingLabel;

    @FXML
    private Label totalRoomLabel;

    @FXML
    private Label totalStudentLabel;

    @FXML
    private Label checkRecordCountLabel;

    @FXML
    private VBox buildingStatBox;

    @FXML
    private Button refreshBtn;

    @FXML
    public void initialize() {
        loadReport();
    }

    @FXML
    private void onRefresh() {
        loadReport();
    }

    private void loadReport() {
        FxUtils.runAsync(() -> {
            var stats = dashboardService.getStats();
            var buildings = buildingRepository.findAll();
            long totalRecords = checkRecordRepository.count();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalBuilding", stats.getBuildingCount());
            result.put("totalRoom", stats.getRoomCount());
            result.put("totalStudent", stats.getStudentCount());
            result.put("activeTaskCount", stats.getActiveTaskCount());
            result.put("totalRecords", totalRecords);

            for (var b : buildings) {
                long roomCount = roomRepository.countByBuildingIdAndIsActiveTrue(b.getId());
                long studentCount = studentRepository.countByBuildingId(b.getId());
                result.put("building_" + b.getName() + "_rooms", roomCount);
                result.put("building_" + b.getName() + "_students", studentCount);
            }

            result.put("buildings", buildings.stream().map(b -> b.getName()).toList());
            return result;
        }, result -> {
            totalBuildingLabel.setText(String.valueOf(result.get("totalBuilding")));
            totalRoomLabel.setText(String.valueOf(result.get("totalRoom")));
            totalStudentLabel.setText(String.valueOf(result.get("totalStudent")));
            checkRecordCountLabel.setText(String.valueOf(result.get("totalRecords")));

            @SuppressWarnings("unchecked")
            java.util.List<String> buildingNames = (java.util.List<String>) result.get("buildings");
            buildingStatBox.getChildren().clear();
            if (buildingNames.isEmpty()) {
                Label empty = new Label("暂无楼栋数据");
                empty.setStyle("-fx-text-fill: #6B7080; -fx-font-size: 13px; -fx-padding: 8 0 0 0;");
                buildingStatBox.getChildren().add(empty);
            } else {
                for (String name : buildingNames) {
                    long rooms = (long) result.get("building_" + name + "_rooms");
                    long students = (long) result.get("building_" + name + "_students");

                    HBox row = new HBox(16);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(12, 16, 12, 16));
                    row.setStyle(
                            "-fx-background-color: #1E222C; -fx-background-radius: 6; -fx-border-color: #2A2E3A; -fx-border-radius: 6;");

                    Label nameLabel = new Label(name);
                    nameLabel.setStyle("-fx-text-fill: #CDD1D9; -fx-font-size: 13px; -fx-font-weight: bold; -fx-min-width: 120;");
                    row.getChildren().add(nameLabel);

                    Label roomLabel = new Label(rooms + " 房间");
                    roomLabel.setStyle("-fx-text-fill: #8A8F9C; -fx-font-size: 12px; -fx-min-width: 80;");
                    row.getChildren().add(roomLabel);

                    Label studentLabel = new Label(students + " 学生");
                    studentLabel.setStyle("-fx-text-fill: #8A8F9C; -fx-font-size: 12px; -fx-min-width: 80;");
                    row.getChildren().add(studentLabel);

                    buildingStatBox.getChildren().add(row);
                }
            }
        });
    }
}