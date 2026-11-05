package net.alan.jupiter.fx.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.task.AssignTaskService;
import net.alan.jupiter.domain.model.Counselor;
import net.alan.jupiter.domain.model.DormBuilding;
import net.alan.jupiter.domain.model.Floor;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.domain.repository.CounselorRepository;
import net.alan.jupiter.domain.repository.DormBuildingRepository;
import net.alan.jupiter.domain.repository.FloorRepository;
import net.alan.jupiter.domain.repository.TaskRepository;
import net.alan.jupiter.fx.model.TaskRow;
import net.alan.jupiter.fx.util.DialogUtils;
import net.alan.jupiter.fx.util.FxUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskController {

    private final TaskRepository taskRepository;
    private final AssignTaskService assignTaskService;
    private final DormBuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final CounselorRepository counselorRepository;

    @FXML
    private TableView<TaskRow> taskTable;

    @FXML
    private TableColumn<TaskRow, String> colTaskNo;

    @FXML
    private TableColumn<TaskRow, String> colTitle;

    @FXML
    private TableColumn<TaskRow, String> colBuilding;

    @FXML
    private TableColumn<TaskRow, String> colFloor;

    @FXML
    private TableColumn<TaskRow, String> colRooms;

    @FXML
    private TableColumn<TaskRow, String> colChecker;

    @FXML
    private TableColumn<TaskRow, String> colStatus;

    @FXML
    private TableColumn<TaskRow, String> colProgress;

    @FXML
    private TableColumn<TaskRow, Void> colActions;

    @FXML
    private Label pageInfoLabel;

    @FXML
    private Button prevBtn;

    @FXML
    private Button nextBtn;

    private int page = 0;
    private static final int SIZE = 50;
    private int totalPages = 0;

    /** 便于演示/截图/自动化回归：启动后自动打开指定对话框（如 create-task）。 */
    @org.springframework.beans.factory.annotation.Value("${Chinan.open-dialog:}")
    private String openDialogOnLoad;

    @FXML
    public void initialize() {
        taskTable.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);

        colTaskNo.setCellValueFactory(c -> c.getValue().getTaskNo());
        colTaskNo.setCellFactory(col -> {
            TableCell<TaskRow, String> cell = new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setTooltip(null);
                    } else {
                        setText(item);
                        Tooltip tip = new Tooltip(item);
                        tip.setStyle("-fx-font-size: 12px;");
                        setTooltip(tip);
                    }
                }
            };
            return cell;
        });

        colTitle.setCellValueFactory(c -> c.getValue().getTitle());
        colTitle.setCellFactory(createTooltipCellFactory());

        colBuilding.setCellValueFactory(c -> c.getValue().getBuildingName());
        colBuilding.setCellFactory(createTooltipCellFactory());

        colFloor.setCellValueFactory(c -> c.getValue().getFloorLabel());
        colFloor.setCellFactory(createTooltipCellFactory());

        colRooms.setCellValueFactory(c -> c.getValue().getRoomNumbers());
        colRooms.setCellFactory(createTooltipCellFactory());
        colRooms.setPrefWidth(120);

        colChecker.setCellValueFactory(c -> c.getValue().getAssignedCounselorName());
        colChecker.setCellFactory(createTooltipCellFactory());
        colChecker.setPrefWidth(100);

        colStatus.setCellValueFactory(c -> c.getValue().getStatus());
        colStatus.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                TaskRow row = getTableView().getItems().get(getIndex());
                setText(null);
                setGraphic(net.alan.jupiter.fx.util.Badges.of(
                        item, net.alan.jupiter.fx.util.Badges.taskStatusVariant(row.getStatusName().get())));
            }
        });

        colProgress.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                (c.getValue().getCheckedRooms().get() == null ? "0" : c.getValue().getCheckedRooms().get())
                        + " / " + (c.getValue().getTotalRooms().get() == null ? "0" : c.getValue().getTotalRooms().get())));

        colActions.setCellFactory(col -> new TableCell<>() {
            private final HBox box = new HBox(6);

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableView() == null) {
                    setGraphic(null);
                    return;
                }
                TaskRow row = getTableView().getItems().get(getIndex());
                box.getChildren().clear();
                String s = row.getStatusName().get();

                if (!"COMPLETED".equals(s) && !"CANCELLED".equals(s)) {
                    Button cancelBtn = new Button("取消");
                    cancelBtn.getStyleClass().add("btn-danger");
                    cancelBtn.setOnAction(e -> onCancelTask(row.getTaskId().get()));
                    box.getChildren().add(cancelBtn);
                }
                if ("COMPLETED".equals(s) || "CANCELLED".equals(s)) {
                    Button deleteBtn = new Button("删除");
                    deleteBtn.getStyleClass().add("btn-ghost");
                    deleteBtn.setOnAction(e -> onDeleteTask(row.getTaskId().get()));
                    box.getChildren().add(deleteBtn);
                }
                setGraphic(box);
            }
        });

        loadData();

        if ("create-task".equalsIgnoreCase(openDialogOnLoad)) {
            javafx.application.Platform.runLater(this::onCreateTask);
        }
    }

    @FXML
    private void onCreateTask() {
        Dialog<Task> dialog = new Dialog<>();
        dialog.setTitle("创建查寝任务");
        dialog.setHeaderText("选择楼栋、楼层、指定房间并分配查宿者");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(560);
        java.net.URL dialogCss = getClass().getResource("/css/chinan.css");
        if (dialogCss != null) {
            dialog.getDialogPane().getStylesheets().add(dialogCss.toExternalForm());
            dialog.getDialogPane().getStyleClass().add("dialog-pane");
        }

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(14);
        grid.setPadding(new Insets(4, 0, 6, 0));

        TextField titleField = new TextField();
        titleField.setPrefWidth(260);
        titleField.setPromptText("如：2024秋 第3周晚查寝");
        DatePicker datePicker = new DatePicker(LocalDate.now());

        ComboBox<DormBuilding> buildingCombo = new ComboBox<>();
        buildingCombo.setPromptText("选择楼栋");
        ComboBox<Floor> floorCombo = new ComboBox<>();
        floorCombo.setPromptText("先选择楼栋");
        floorCombo.setDisable(true);
        TextField roomField = new TextField();
        roomField.setPromptText("如 101,102,103，留空表示全部房间");
        ComboBox<Counselor> counselorCombo = new ComboBox<>();
        counselorCombo.setPromptText("选择查宿者");

        buildingCombo.setOnAction(e -> {
            DormBuilding building = buildingCombo.getValue();
            if (building != null) {
                loadFloors(floorCombo, building.getId());
            } else {
                floorCombo.getItems().clear();
                floorCombo.setDisable(true);
                floorCombo.setPromptText("先选择楼栋");
            }
        });

        grid.add(new Label("任务标题:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("任务日期:"), 0, 1);
        grid.add(datePicker, 1, 1);
        grid.add(new Label("查寝楼栋:"), 0, 2);
        grid.add(buildingCombo, 1, 2);
        grid.add(new Label("查寝楼层:"), 0, 3);
        grid.add(floorCombo, 1, 3);
        grid.add(new Label("指定房间:"), 0, 4);
        grid.add(roomField, 1, 4);
        grid.add(new Label("分配查宿者:"), 0, 5);
        grid.add(counselorCombo, 1, 5);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-state");
        errorLabel.setWrapText(true);
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
        grid.add(errorLabel, 0, 6, 2, 1);

        dialog.getDialogPane().setContent(grid);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        okButton.setText("创建");
        okButton.getStyleClass().add("btn-primary");
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.setText("取消");

        // 行内校验：缺失项直接在对话框内提示，不再弹二级窗口
        java.util.function.Supplier<String> validate = () -> {
            if (titleField.getText() == null || titleField.getText().isBlank()) return "请填写任务标题";
            if (datePicker.getValue() == null) return "请选择任务日期";
            if (buildingCombo.getValue() == null) return "请选择查寝楼栋";
            if (floorCombo.getValue() == null) return "请选择查寝楼层";
            if (counselorCombo.getValue() == null) return "请选择查宿者";
            return null;
        };
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String error = validate.get();
            if (error != null) {
                errorLabel.setText(error);
                errorLabel.setManaged(true);
                errorLabel.setVisible(true);
                event.consume();
            }
        });
        Runnable clearError = () -> {
            errorLabel.setManaged(false);
            errorLabel.setVisible(false);
        };
        titleField.textProperty().addListener((obs, oldV, newV) -> clearError.run());
        datePicker.valueProperty().addListener((obs, oldV, newV) -> clearError.run());
        buildingCombo.valueProperty().addListener((obs, oldV, newV) -> clearError.run());
        floorCombo.valueProperty().addListener((obs, oldV, newV) -> clearError.run());
        counselorCombo.valueProperty().addListener((obs, oldV, newV) -> clearError.run());

        buildingCombo.setPromptText("加载中...");
        counselorCombo.setPromptText("加载中...");

        AtomicInteger pendingLoads = new AtomicInteger(2);
        Runnable tryEnableOk = () -> {
            if (pendingLoads.decrementAndGet() == 0) {
                Platform.runLater(() -> okButton.setDisable(false));
            }
        };

        FxUtils.runAsync(
                () -> buildingRepository.findAll().stream()
                        .sorted(Comparator.comparing(DormBuilding::getName))
                        .toList(),
                buildings -> {
                    buildingCombo.getItems().setAll(buildings);
                    buildingCombo.setPromptText("选择楼栋");
                    if (!buildings.isEmpty()) buildingCombo.getSelectionModel().selectFirst();
                    tryEnableOk.run();
                },
                ex -> {
                    buildingCombo.setPromptText("加载失败");
                    tryEnableOk.run();
                }
        );

        FxUtils.runAsync(
                () -> counselorRepository.findAll().stream()
                        .filter(c -> c.getIsActive() && "COUNSELOR".equalsIgnoreCase(c.getRole()))
                        .sorted(Comparator.comparing(Counselor::getName))
                        .toList(),
                counselors -> {
                    counselorCombo.getItems().setAll(counselors);
                    counselorCombo.setPromptText("选择查宿者");
                    if (!counselors.isEmpty()) counselorCombo.getSelectionModel().selectFirst();
                    tryEnableOk.run();
                },
                ex -> {
                    counselorCombo.setPromptText("加载失败");
                    tryEnableOk.run();
                }
        );

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String title = titleField.getText();
                LocalDate taskDate = datePicker.getValue();
                DormBuilding building = buildingCombo.getValue();
                Floor floor = floorCombo.getValue();
                String rooms = roomField.getText();
                Counselor counselor = counselorCombo.getValue();

                if (title == null || title.isBlank() || taskDate == null
                        || building == null || floor == null || counselor == null) {
                    // 正常情况下已被行内校验拦截，这里只做兜底
                    return null;
                }

                try {
                    return assignTaskService.assignTask(
                            title, taskDate, building.getId(), floor.getId(),
                            rooms.isBlank() ? null : rooms,
                            counselor.getId(), null);
                } catch (Exception e) {
                    log.error("创建任务失败", e);
                    DialogUtils.showError("创建失败", "系统异常，请稍后再试");
                    return null;
                }
            }
            return null;
        });

        Optional<Task> result = dialog.showAndWait();
        result.ifPresent(task -> loadData());
    }

    private void loadFloors(ComboBox<Floor> floorCombo, String buildingId) {
        FxUtils.runAsync(
                () -> floorRepository.findByBuildingIdOrderByFloorNumber(buildingId),
                floors -> {
                    floorCombo.getItems().setAll(floors);
                    floorCombo.setDisable(floors.isEmpty());
                    floorCombo.setPromptText(floors.isEmpty() ? "该楼栋无楼层数据" : "选择楼层");
                    if (!floors.isEmpty()) floorCombo.getSelectionModel().selectFirst();
                }
        );
    }

    private javafx.util.Callback<javafx.scene.control.TableColumn<TaskRow, String>,
            javafx.scene.control.TableCell<TaskRow, String>> createTooltipCellFactory() {
        return col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    javafx.scene.control.Tooltip tip = new javafx.scene.control.Tooltip(item);
                    tip.setStyle("-fx-font-size: 12px;");
                    setTooltip(tip);
                }
            }
        };
    }

    private void onCompleteTask(String taskId) {
        try {
            assignTaskService.completeTask(taskId);
            loadData();
        } catch (Exception e) {
            log.error("完成任务失败", e);
            DialogUtils.showError("操作失败", "系统异常，请稍后再试");
        }
    }

    private void onCancelTask(String taskId) {
        try {
            assignTaskService.cancelTask(taskId);
            loadData();
        } catch (Exception e) {
            log.error("取消任务失败", e);
            DialogUtils.showError("操作失败", "系统异常，请稍后再试");
        }
    }

    private void onDeleteTask(String taskId) {
        boolean confirm = DialogUtils.showConfirm("确认删除",
                "删除后任务将不再显示，确定要删除该任务吗？");
        if (confirm) {
            try {
                assignTaskService.softDeleteTask(taskId);
                loadData();
                DialogUtils.showInfo("删除成功", "任务已删除");
            } catch (Exception e) {
                log.error("删除任务失败", e);
                DialogUtils.showError("删除失败", "系统异常，请稍后再试");
            }
        }
    }

    @FXML
    private void onClearCompleted() {
        boolean confirm = DialogUtils.showConfirm("确认清除",
                "此操作将清除所有\"已完成\"的任务，任务将不再显示。\n确定要继续吗？");
        if (confirm) {
            try {
                int count = assignTaskService.clearCompletedTasks();
                loadData();
                DialogUtils.showInfo("清除完成", "已清除 " + count + " 个已完成任务");
            } catch (Exception e) {
                log.error("清除已完成任务失败", e);
                DialogUtils.showError("清除失败", "系统异常，请稍后再试");
            }
        }
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
    private void onRefresh() {
        page = 0;
        loadData();
    }

    private void loadData() {
        FxUtils.runAsync(
                () -> {
                    org.springframework.data.domain.Page<Task> taskPage =
                            taskRepository.findAll(
                                    org.springframework.data.domain.PageRequest.of(
                                            page, SIZE,
                                            org.springframework.data.domain.Sort.by(
                                                    org.springframework.data.domain.Sort.Direction.DESC,
                                                    "createdAt")));
                    return new Object[]{
                            taskPage.getContent().stream().map(TaskRow::new).toList(),
                            taskPage.getTotalPages()
                    };
                },
                result -> {
                    @SuppressWarnings("unchecked")
                    java.util.List<TaskRow> rows = (java.util.List<TaskRow>) result[0];
                    totalPages = (int) result[1];
                    taskTable.setItems(FXCollections.observableArrayList(rows));
                    pageInfoLabel.setText("第 " + (page + 1) + " 页 / 共 " + totalPages + " 页");
                    if (totalPages == 0) {
                        page = 0;
                        updatePaginationButtons();
                        return;
                    }
                    if (page >= totalPages) {
                        page = totalPages - 1;
                        loadData();
                        return;
                    }
                    updatePaginationButtons();
                }
        );
    }

    private void updatePaginationButtons() {
        prevBtn.setDisable(page == 0);
        nextBtn.setDisable(page + 1 >= totalPages);
    }
}