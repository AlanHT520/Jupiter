package net.alan.jupiter.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.domain.model.DormBuilding;
import net.alan.jupiter.domain.repository.DormBuildingRepository;
import net.alan.jupiter.domain.repository.RoomRepository;
import net.alan.jupiter.domain.repository.StudentRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BuildingController {

    private final DormBuildingRepository buildingRepository;
    private final RoomRepository roomRepository;
    private final StudentRepository studentRepository;

    @FXML
    private FlowPane buildingCards;

    @FXML
    public void initialize() {
        loadBuildingCards();
    }

    private void loadBuildingCards() {
        buildingCards.getChildren().clear();
        try {
            List<DormBuilding> buildings = buildingRepository.findByIsActiveTrue();
            if (buildings.isEmpty()) {
                buildingCards.getChildren().add(net.alan.jupiter.fx.util.EmptyStates.block(
                        "暂无楼栋数据",
                        "通过「文件 → 导入宿舍数据」导入学生名册后，这里会显示各楼栋的房间与学生规模",
                        org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.BUILDING));
                return;
            }
            for (DormBuilding building : buildings) {
                long roomCount = roomRepository.countByBuildingIdAndIsActiveTrue(building.getId());
                long studentCount = studentRepository.countByBuildingId(building.getId());
                buildingCards.getChildren().add(buildCard(building, roomCount, studentCount));
            }
        } catch (Exception e) {
            log.error("加载楼栋数据失败", e);
            Label errorLabel = new Label("楼栋数据加载失败，请稍后重试");
            errorLabel.getStyleClass().add("error-state");
            buildingCards.getChildren().add(errorLabel);
        }
    }

    private VBox buildCard(DormBuilding building, long roomCount, long studentCount) {
        VBox card = new VBox();
        card.getStyleClass().add("building-card");

        HBox header = new HBox(10);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label icon = new Label("🏢");
        icon.getStyleClass().add("stat-icon");
        Label name = new Label(building.getName() != null ? building.getName() : "(未命名楼栋)");
        name.getStyleClass().add("building-name");
        header.getChildren().addAll(icon, name);

        Label code = new Label(building.getBuildingCode() != null
                ? "编号 " + building.getBuildingCode() : "未设置编号");
        code.getStyleClass().add("caption");

        Label metrics = new Label(roomCount + " 个房间 · " + studentCount + " 名学生");
        metrics.getStyleClass().add("muted");

        card.getChildren().addAll(header, code, metrics);
        return card;
    }
}
