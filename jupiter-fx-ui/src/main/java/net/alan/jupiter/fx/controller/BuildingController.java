package net.alan.jupiter.fx.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
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
        try {
            List<DormBuilding> buildings = buildingRepository.findByIsActiveTrue();
            for (DormBuilding b : buildings) {
                long roomCount = roomRepository.countByBuildingIdAndIsActiveTrue(b.getId());
                long studentCount = studentRepository.countByBuildingId(b.getId());

                VBox card = new VBox(8);
                card.getStyleClass().add("stat-card");
                card.setPrefWidth(180);

                Label iconLabel = new Label("\uD83C\uDFE2");
                iconLabel.setStyle("-fx-font-size: 22px;");

                Label nameLabel = new Label(b.getName());
                nameLabel.getStyleClass().add("stat-value");
                nameLabel.setStyle(nameLabel.getStyle() + "-fx-font-size: 18px;");

                Label codeLabel = new Label(b.getBuildingCode() != null ? b.getBuildingCode() : "-");
                codeLabel.setStyle("-fx-text-fill: #808080; -fx-font-size: 11px;");

                Label roomsLabel = new Label("房间: " + roomCount);
                roomsLabel.getStyleClass().add("stat-label");

                Label studentsLabel = new Label("学生: " + studentCount);
                studentsLabel.getStyleClass().add("stat-label");

                card.getChildren().addAll(iconLabel, nameLabel, codeLabel, roomsLabel, studentsLabel);
                buildingCards.getChildren().add(card);
            }
        } catch (Exception e) {
            log.error("加载楼栋数据失败", e);
            Label errorLabel = new Label("数据加载失败");
            errorLabel.setStyle("-fx-text-fill: red;");
            buildingCards.getChildren().add(errorLabel);
        }
    }
}