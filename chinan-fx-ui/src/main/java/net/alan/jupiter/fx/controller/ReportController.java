package net.alan.jupiter.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.application.dashboard.DashboardService;
import net.alan.jupiter.domain.repository.CheckRecordRepository;
import net.alan.jupiter.domain.repository.DormBuildingRepository;
import net.alan.jupiter.domain.repository.RoomRepository;
import net.alan.jupiter.domain.repository.StudentRepository;
import net.alan.jupiter.fx.util.FxUtils;
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
    public void initialize() {
        loadReport();
    }

    @FXML
    private void onRefresh() {
        loadReport();
    }

    private void loadReport() {
        buildingStatBox.getChildren().clear();
        Label loading = new Label("统计中...");
        loading.getStyleClass().add("muted");
        buildingStatBox.getChildren().add(loading);

        FxUtils.runAsync(() -> {
            DashboardService.DashboardStats stats = dashboardService.getStats();
            long totalRecords = checkRecordRepository.count();
            var buildings = buildingRepository.findAll();

            Map<String, long[]> perBuilding = new LinkedHashMap<>();
            for (var building : buildings) {
                long rooms = roomRepository.countByBuildingIdAndIsActiveTrue(building.getId());
                long students = studentRepository.countByBuildingId(building.getId());
                perBuilding.put(building.getName(), new long[]{rooms, students});
            }

            return new Object[]{stats, totalRecords, perBuilding};
        }, result -> {
            DashboardService.DashboardStats stats = (DashboardService.DashboardStats) result[0];
            long totalRecords = (Long) result[1];
            @SuppressWarnings("unchecked")
            Map<String, long[]> perBuilding = (Map<String, long[]>) result[2];

            totalBuildingLabel.setText(String.valueOf(stats.getBuildingCount()));
            totalRoomLabel.setText(String.valueOf(stats.getRoomCount()));
            totalStudentLabel.setText(String.valueOf(stats.getStudentCount()));
            checkRecordCountLabel.setText(String.valueOf(totalRecords));

            buildingStatBox.getChildren().clear();
            if (perBuilding.isEmpty()) {
                buildingStatBox.getChildren().add(net.alan.jupiter.fx.util.EmptyStates.block(
                        "暂无楼栋数据",
                        "通过「文件 → 导入宿舍数据」导入学生名册后，这里会显示各楼栋规模",
                        org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.BUILDING));
                return;
            }
            perBuilding.forEach((name, counts) -> buildingStatBox.getChildren()
                    .add(buildBuildingRow(name, counts[0], counts[1])));
        }, ex -> {
            buildingStatBox.getChildren().clear();
            Label error = new Label("统计加载失败，请稍后重试");
            error.getStyleClass().add("error-state");
            buildingStatBox.getChildren().add(error);
        });
    }

    private HBox buildBuildingRow(String name, long rooms, long students) {
        HBox row = new HBox();
        row.getStyleClass().add("list-row");

        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("list-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label roomsLabel = new Label(rooms + " 房间");
        roomsLabel.getStyleClass().add("list-meta");
        Label studentsLabel = new Label(students + " 学生");
        studentsLabel.getStyleClass().add("list-meta");

        HBox metrics = new HBox(18, roomsLabel, studentsLabel);
        metrics.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        row.getChildren().addAll(nameLabel, spacer, metrics);
        return row;
    }
}
