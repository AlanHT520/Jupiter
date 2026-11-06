package net.alan.jupiter.fx.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.counselor.CounselorManagementService;
import net.alan.jupiter.application.counselor.ImportCounselorService;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.domain.model.Counselor;
import net.alan.jupiter.fx.util.DialogUtils;
import net.alan.jupiter.fx.util.FxUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CounselorController {

    private final CounselorManagementService counselorManagementService;
    private final ImportCounselorService importCounselorService;

    @FXML
    private TableView<Counselor> counselorTable;

    @FXML
    private TableColumn<Counselor, String> colName;

    @FXML
    private TableColumn<Counselor, String> colStudentNo;

    @FXML
    private TableColumn<Counselor, String> colClassName;

    @FXML
    private TableColumn<Counselor, String> colGender;

    @FXML
    private TableColumn<Counselor, String> colPhone;

    @FXML
    private TableColumn<Counselor, String> colDepartment;

    @FXML
    private TableColumn<Counselor, String> colActive;

    @FXML
    private TableColumn<Counselor, Void> colActions;

    @FXML
    public void initialize() {
        counselorTable.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);

        colName.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getName()));
        colStudentNo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getStudentNo() != null ? c.getValue().getStudentNo() : ""));
        colClassName.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getClassName() != null ? c.getValue().getClassName() : ""));
        colGender.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getGender() != null ? c.getValue().getGender() : ""));
        colPhone.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getPhone() != null ? c.getValue().getPhone() : ""));
        colDepartment.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getDepartment() != null ? c.getValue().getDepartment() : ""));
        colActive.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                Boolean.TRUE.equals(c.getValue().getIsActive()) ? "启用" : "禁用"));
        colActive.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                setText(null);
                setGraphic(net.alan.jupiter.fx.util.Badges.of(
                        item, net.alan.jupiter.fx.util.Badges.activeVariant(item)));
            }
        });

        colActions.setCellFactory(col -> new TableCell<>() {
            private final HBox box = new HBox(6);

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableView() == null) {
                    setGraphic(null);
                    return;
                }
                Counselor c = getTableView().getItems().get(getIndex());
                box.getChildren().clear();

                Button resetBtn = new Button("重置密码");
                resetBtn.getStyleClass().add("btn-ghost");
                resetBtn.setOnAction(e -> onResetPassword(c));
                box.getChildren().add(resetBtn);

                Button deleteBtn = new Button("删除");
                deleteBtn.getStyleClass().add("btn-danger");
                deleteBtn.setOnAction(e -> onDeleteCounselor(c));
                box.getChildren().add(deleteBtn);

                setGraphic(box);
            }
        });

        loadData();
    }

    @FXML
    private void onImportCounselors() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择工作人员名单文件");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel 文件", "*.xlsx"));
        File file = chooser.showOpenDialog(counselorTable.getScene().getWindow());
        if (file == null) return;

        FxUtils.runAsync(
                () -> {
                    try {
                        byte[] data = Files.readAllBytes(file.toPath());
                        ImportCounselorService.ImportResult result =
                                importCounselorService.importCounselors(data);
                        return result;
                    } catch (IOException e) {
                        throw new RuntimeException("读取文件失败，请重试", e);
                    }
                },
                result -> {
                    String msg = String.format(
                            "导入完成！\n导入: %d 人\n更新: %d 人\n删除: %d 人",
                            result.getImported(), result.getUpdated(),
                            result.getDeleted());
                    if (!result.getErrors().isEmpty()) {
                        msg += "\n\n错误: " + String.join(", ", result.getErrors());
                    }
                    DialogUtils.showInfo("导入结果", msg);
                    loadData();
                }
        );
    }

    @FXML
    private void onExportAccounts() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出工作人员账户");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel 文件", "*.xlsx"));
        chooser.setInitialFileName("工作人员账户.xlsx");
        File file = chooser.showSaveDialog(counselorTable.getScene().getWindow());
        if (file == null) return;

        List<Counselor> items = counselorTable.getItems();
        if (items.isEmpty()) {
            DialogUtils.showInfo("提示", "当前没有可导出的账户数据");
            return;
        }

        if (!DialogUtils.showConfirm("导出明文密码？",
                "表格会包含每个工作人员的「明文密码」，请仅在可信环境使用。\n"
                        + "早期只存了哈希的旧账号无法还原，会在该列标注提示，需要重置后才会显示。\n\n确定继续？")) {
            return;
        }

        FxUtils.runAsync(
                () -> {
                    try {
                        byte[] data = counselorManagementService.exportAccounts(items);
                        Files.write(file.toPath(), data);
                    } catch (IOException e) {
                        throw new RuntimeException("写入文件失败，请重试", e);
                    }
                    return file.getAbsolutePath();
                },
                path -> DialogUtils.showInfo("导出成功",
                        "已导出到:\n" + path + "\n\n工作人员密码已重置，请把「初始密码」列发给本人。"),
                ex -> {
                    log.error("导出工作人员账户失败", ex);
                    DialogUtils.showError("导出失败", "导出工作人员账户失败，请稍后再试");
                }
        );
    }

    private void onResetPassword(Counselor c) {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("重置密码");
        d.setHeaderText("为 " + c.getName() + " (" + c.getUsername() + ") 重置密码");
        d.setContentText("新密码:");

        Optional<String> result = d.showAndWait();
        result.ifPresent(newPwd -> {
            try {
                counselorManagementService.resetPassword(c.getId(), newPwd);
                DialogUtils.showInfo("成功", "密码已重置");
            } catch (Exception e) {
                log.error("重置密码失败", e);
                String msg = (e instanceof BizException) ? e.getMessage() : "系统异常，请稍后再试";
                DialogUtils.showError("重置失败", msg);
            }
        });
    }

    private void onDeleteCounselor(Counselor c) {
        boolean confirm = DialogUtils.showConfirm("确认删除",
                "确定要删除查宿者 " + c.getName() + " (" + c.getUsername() + ") 吗？\n此操作不可撤销");
        if (!confirm) return;

        FxUtils.runAsync(
                () -> {
                    counselorManagementService.deleteCounselor(c.getId());
                    return null;
                },
                v -> {
                    loadData();
                    DialogUtils.showInfo("删除成功", "查宿者 " + c.getName() + " 已删除");
                },
                ex -> {
                    log.error("删除查宿者失败", ex);
                    DialogUtils.showError("删除失败", "系统异常，请稍后再试");
                }
        );
    }

    @FXML
    private void onRefresh() {
        loadData();
    }

    private void loadData() {
        FxUtils.runAsync(
                () -> counselorManagementService.listCounselors(),
                counselors -> counselorTable.setItems(
                        FXCollections.observableArrayList(counselors))
        );
    }
}