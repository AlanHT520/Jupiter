package net.alan.jupiter.fx.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.fx.model.StudentRow;
import net.alan.jupiter.fx.util.DialogUtils;
import net.alan.jupiter.fx.util.FxUtils;
import net.alan.jupiter.domain.repository.ClassInfoRepository;
import net.alan.jupiter.domain.repository.DormBuildingRepository;
import net.alan.jupiter.domain.repository.StudentRepository;
import net.alan.jupiter.infrastructure.persistence.specification.StudentSpecification;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RosterController {

    private final StudentRepository studentRepository;
    private final DormBuildingRepository buildingRepository;
    private final ClassInfoRepository classInfoRepository;
    private final net.alan.jupiter.application.roster.ImportClassRosterService importClassRosterService;

    private Map<String, String> buildingNameToId;

    @FXML
    private TextField keywordField;

    @FXML
    private ComboBox<String> buildingCombo;

    @FXML
    private ComboBox<String> classCombo;

    @FXML
    private TableView<StudentRow> studentTable;

    @FXML
    private TableColumn<StudentRow, String> colStudentNo;

    @FXML
    private TableColumn<StudentRow, String> colName;

    @FXML
    private TableColumn<StudentRow, String> colGender;

    @FXML
    private TableColumn<StudentRow, String> colClassName;

    @FXML
    private TableColumn<StudentRow, String> colBuilding;

    @FXML
    private TableColumn<StudentRow, String> colRoom;

    @FXML
    private TableColumn<StudentRow, String> colBed;

    @FXML
    private TableColumn<StudentRow, String> colPhone;

    @FXML
    private TableColumn<StudentRow, String> colStatus;

    @FXML
    private Label pageInfoLabel;

    @FXML
    private Label totalLabel;

    @FXML
    private Button prevBtn;

    @FXML
    private Button nextBtn;

    private int page = 0;
    private static final int SIZE = 50;
    private long totalCount = 0;

    @FXML
    public void initialize() {
        studentTable.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);

        colStudentNo.setCellValueFactory(c -> c.getValue().getStudentNo());
        colName.setCellValueFactory(c -> c.getValue().getName());
        colGender.setCellValueFactory(c -> c.getValue().getGender());
        colClassName.setCellValueFactory(c -> c.getValue().getClassName());
        colBuilding.setCellValueFactory(c -> c.getValue().getBuildingName());
        colRoom.setCellValueFactory(c -> c.getValue().getRoomNumber());
        colBed.setCellValueFactory(c -> c.getValue().getBedNumber());
        colPhone.setCellValueFactory(c -> c.getValue().getPhone());
        colStatus.setCellValueFactory(c -> c.getValue().getStatusLabel());

        loadBuildingCombo();
        loadClassCombo();
        loadData();
    }

    private void loadBuildingCombo() {
        FxUtils.runAsync(
                () -> {
                    var buildings = buildingRepository.findAll();
                    buildingNameToId = buildings.stream()
                            .collect(Collectors.toMap(
                                    b -> b.getName(),
                                    b -> b.getId(),
                                    (a, b) -> a));
                    return buildings.stream()
                            .map(b -> b.getName())
                            .sorted()
                            .toList();
                },
                names -> {
                    buildingCombo.getItems().setAll(names);
                    buildingCombo.getItems().add(0, "全部楼栋");
                    buildingCombo.getSelectionModel().selectFirst();
                }
        );
    }

    @FXML
    private void onSearch() {
        page = 0;
        loadData();
    }

    @FXML
    private void onRefresh() {
        page = 0;
        keywordField.clear();
        buildingCombo.getSelectionModel().selectFirst();
        classCombo.getSelectionModel().selectFirst();
        loadData();
    }

    @FXML
    private void onPrevPage() {
        if (page > 0) {
            page--;
            loadData();
        }
    }

    @FXML
    private void onNextPage() {
        page++;
        loadData();
    }

    @FXML
    private void onImport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择宿舍数据导入模板");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel 文件 (*.xlsx)", "*.xlsx"));
        File file = chooser.showOpenDialog(studentTable.getScene().getWindow());
        if (file == null) return;

        FxUtils.runAsync(() -> {
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                return importClassRosterService.importClassRoster(data, file.getName());
            } catch (IOException e) {
                throw new RuntimeException("读取文件失败，请重试", e);
            }
        }, result -> {
            StringBuilder sb = new StringBuilder();
            sb.append("导入: ").append(result.getImported()).append(" 条\n");
            sb.append("更新: ").append(result.getUpdated()).append(" 条\n");
            sb.append("跳过: ").append(result.getSkipped()).append(" 条\n");
            sb.append("删除: ").append(result.getDeleted()).append(" 条\n");
            if (!result.getErrors().isEmpty()) {
                sb.append("错误: ").append(result.getErrors().size()).append(" 条\n");
                result.getErrors().forEach(e -> sb.append("  - ").append(e).append("\n"));
            }
            DialogUtils.showInfo("导入结果", sb.toString());
            loadData();
        });
    }

    private void loadClassCombo() {
        FxUtils.runAsync(
                () -> classInfoRepository.findAll().stream()
                        .map(c -> c.getName())
                        .filter(Objects::nonNull)
                        .sorted()
                        .toList(),
                names -> {
                    classCombo.getItems().setAll(names);
                    classCombo.getItems().add(0, "全部班级");
                    classCombo.getSelectionModel().selectFirst();
                }
        );
    }

    private void loadData() {
        String keyword = keywordField.getText();
        String buildingName = buildingCombo.getValue();
        String buildingId = null;
        if (buildingName != null && !buildingName.equals("全部楼栋") && buildingNameToId != null) {
            buildingId = buildingNameToId.get(buildingName);
        }

        Specification<net.alan.jupiter.domain.model.Student> spec =
                StudentSpecification.searchBy(keyword, buildingId, getSelectedClassName());

        FxUtils.runAsync(
                () -> {
                    org.springframework.data.domain.Page<net.alan.jupiter.domain.model.Student> result =
                            studentRepository.findAll(spec, PageRequest.of(page, SIZE, Sort.by("studentNo")));
                    return new Object[]{
                            result.getContent().stream().map(StudentRow::new).toList(),
                            result.getTotalElements()
                    };
                },
                result -> {
                    @SuppressWarnings("unchecked")
                    java.util.List<StudentRow> rows = (java.util.List<StudentRow>) result[0];
                    long total = (long) result[1];
                    studentTable.setItems(FXCollections.observableArrayList(rows));
                    totalCount = total;
                    pageInfoLabel.setText("第 " + (page + 1) + " 页");
                    totalLabel.setText("共 " + total + " 条记录");
                    updatePaginationButtons();
                }
        );
    }

    private void updatePaginationButtons() {
        prevBtn.setDisable(page == 0);
        nextBtn.setDisable((page + 1L) * SIZE >= totalCount);
    }

    private String getSelectedClassName() {
        String className = classCombo.getValue();
        if (className == null || className.equals("全部班级")) {
            return null;
        }
        return className;
    }
}