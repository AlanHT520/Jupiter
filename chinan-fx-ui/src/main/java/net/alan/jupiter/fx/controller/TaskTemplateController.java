package net.alan.jupiter.fx.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.task.TaskTemplateService;
import net.alan.jupiter.domain.model.TaskTemplate;
import net.alan.jupiter.fx.util.DialogUtils;
import net.alan.jupiter.fx.util.FxUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 任务预分配独立页：查看/删除预分配，选择日期一键批量分配。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskTemplateController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TaskTemplateService taskTemplateService;
    private final ApplicationEventPublisher eventPublisher;

    @FXML
    private TableView<TaskTemplate> templateTable;

    @FXML
    private TableColumn<TaskTemplate, String> colName;

    @FXML
    private TableColumn<TaskTemplate, String> colCount;

    @FXML
    private TableColumn<TaskTemplate, String> colCreatedAt;

    @FXML
    private Label summaryLabel;

    @FXML
    public void initialize() {
        templateTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colName.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getName() != null ? c.getValue().getName() : ""));
        colCount.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getItemCount() != null ? c.getValue().getItemCount() + " 条" : "-"));
        colCreatedAt.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCreatedAt() != null ? c.getValue().getCreatedAt().format(FMT) : ""));
        loadData();
    }

    @FXML
    private void onRefresh() {
        loadData();
    }

    private void loadData() {
        FxUtils.runAsync(taskTemplateService::list, list -> {
            templateTable.setItems(FXCollections.observableArrayList(list));
            summaryLabel.setText("共 " + list.size() + " 个预分配");
        }, error -> {
            log.error("加载任务预分配失败", error);
            summaryLabel.setText("加载失败");
        });
    }

    @FXML
    private void onDelete() {
        TaskTemplate template = templateTable.getSelectionModel().getSelectedItem();
        if (template == null) {
            DialogUtils.showWarn("未选择预分配", "请先选中一个任务预分配");
            return;
        }
        if (!DialogUtils.showConfirm("删除预分配",
                "确定删除预分配「" + template.getName() + "」？\n已生成的任务不受影响。")) {
            return;
        }
        FxUtils.runAsync(
                () -> {
                    taskTemplateService.delete(template.getId());
                    return null;
                },
                ok -> loadData(),
                error -> DialogUtils.showError("删除失败", "预分配删除失败，请重试"));
    }

    @FXML
    private void onGoTasks() {
        eventPublisher.publishEvent(new net.alan.jupiter.fx.event.NavigateViewEvent("/fxml/task.fxml"));
    }
}
