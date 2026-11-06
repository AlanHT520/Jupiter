package net.alan.jupiter.fx.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.config.SystemConfigService;
import net.alan.jupiter.domain.model.CounselorRegistration;
import net.alan.jupiter.domain.model.CounselorRegistration.RegistrationStatus;
import net.alan.jupiter.domain.repository.CounselorRegistrationRepository;
import net.alan.jupiter.fx.util.DialogUtils;
import net.alan.jupiter.fx.util.FxUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RegistrationManagementController {

    private final SystemConfigService configService;
    private final CounselorRegistrationRepository registrationRepo;
    private final net.alan.jupiter.application.registration.RegistrationService registrationService;

    @FXML
    private ToggleButton registrationToggle;

    @FXML
    private Label toggleStatusLabel;

    @FXML
    private Label pendingCountLabel;

    @FXML
    private TableView<CounselorRegistration> pendingTable;

    @FXML
    private TableView<CounselorRegistration> historyTable;

    @FXML
    private TableColumn<CounselorRegistration, String> colName;

    @FXML
    private TableColumn<CounselorRegistration, String> colStudentNo;

    @FXML
    private TableColumn<CounselorRegistration, String> colClassName;

    @FXML
    private TableColumn<CounselorRegistration, String> colPhone;

    @FXML
    private TableColumn<CounselorRegistration, String> colSubmittedAt;

    @FXML
    private TableColumn<CounselorRegistration, String> colHName;

    @FXML
    private TableColumn<CounselorRegistration, String> colHStudentNo;

    @FXML
    private TableColumn<CounselorRegistration, String> colHClassName;

    @FXML
    private TableColumn<CounselorRegistration, String> colHPhone;

    @FXML
    private TableColumn<CounselorRegistration, String> colHStatus;

    @FXML
    private TableColumn<CounselorRegistration, String> colHSubmittedAt;

    @FXML
    private TableColumn<CounselorRegistration, Void> colActions;

    @FXML
    public void initialize() {
        pendingTable.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);
        historyTable.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);
        setupPendingColumns();
        setupHistoryColumns();
        setupActions();
        loadToggleState();
        loadPendingList();
        loadHistoryList();
    }

    private void setupActions() {
        colActions.setCellFactory(column -> new TableCell<>() {
            private final Button approveBtn = new Button("通过");
            private final Button rejectBtn = new Button("拒绝");
            private final HBox box = new HBox(6, approveBtn, rejectBtn);

            {
                approveBtn.getStyleClass().add("btn-primary");
                rejectBtn.getStyleClass().add("btn-danger");
                approveBtn.setOnAction(e -> handle(getTableView().getItems().get(getIndex()), true));
                rejectBtn.setOnAction(e -> handle(getTableView().getItems().get(getIndex()), false));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private void handle(CounselorRegistration reg, boolean approve) {
        if (reg == null) return;
        String action = approve ? "通过" : "拒绝";
        if (!DialogUtils.showConfirm("确认" + action + "？",
                "申请：" + reg.getName() + "（" + reg.getStudentNo() + "）\n"
                        + (approve ? "通过后将创建工作人员账号。" : "拒绝后该申请不再处理。"))) {
            return;
        }
        FxUtils.runAsync(
                () -> {
                    if (approve) {
                        registrationService.approve(reg.getId(), "桌面端管理员");
                    } else {
                        registrationService.reject(reg.getId(), "桌面端管理员");
                    }
                    return null;
                },
                v -> {
                    DialogUtils.showInfo("已处理", "申请已" + action);
                    loadPendingList();
                    loadHistoryList();
                },
                error -> DialogUtils.showError("处理失败", "请稍后再试")
        );
    }

    private void setupPendingColumns() {
        colName.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getName() != null ? c.getValue().getName() : ""));
        colStudentNo.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getGender() != null ? c.getValue().getGender() : ""));
        colClassName.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDepartment() != null ? c.getValue().getDepartment() : ""));
        colPhone.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getPhone() != null ? c.getValue().getPhone() : ""));
        colSubmittedAt.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getSubmittedAt() != null ? c.getValue().getSubmittedAt().toString() : ""));
    }

    private void setupHistoryColumns() {
        colHName.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getName() != null ? c.getValue().getName() : ""));
        colHStudentNo.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getGender() != null ? c.getValue().getGender() : ""));
        colHClassName.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDepartment() != null ? c.getValue().getDepartment() : ""));
        colHPhone.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getPhone() != null ? c.getValue().getPhone() : ""));
        colHStatus.setCellValueFactory(c -> new SimpleStringProperty(
                statusLabel(c.getValue().getStatus())));
        colHSubmittedAt.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getSubmittedAt() != null ? c.getValue().getSubmittedAt().toString() : ""));
    }

    private static String statusLabel(RegistrationStatus s) {
        if (s == null) return "";
        return switch (s) {
            case PENDING -> "待审核";
            case APPROVED -> "已通过";
            case REJECTED -> "已拒绝";
        };
    }

    private void loadToggleState() {
        boolean enabled = configService.isRegistrationEnabled();
        registrationToggle.setSelected(enabled);
        updateToggleLabel(enabled);
    }

    private void updateToggleLabel(boolean enabled) {
        registrationToggle.setText(enabled ? "已开启" : "已关闭");
        registrationToggle.getStyleClass().removeAll("btn-primary", "btn-ghost");
        registrationToggle.getStyleClass().add(enabled ? "btn-primary" : "btn-ghost");

        toggleStatusLabel.setText(enabled ? "注册已开放" : "注册已关闭");
        toggleStatusLabel.getStyleClass().removeAll("muted", "badge", "badge-success", "badge-muted");
        toggleStatusLabel.getStyleClass().addAll("badge",
                enabled ? "badge-success" : "badge-muted");
    }

    @FXML
    private void onToggleRegistration() {
        boolean want = registrationToggle.isSelected();
        if (want) {
            boolean ok = DialogUtils.showConfirm("确认开放注册？",
                    "开放后，任何持有服务器地址的用户都可以提交注册申请。\n"
                    + "请确保你会在近期查看并审核这些申请。");
            if (!ok) {
                registrationToggle.setSelected(false);
                return;
            }
        }

        FxUtils.runAsync(
                () -> {
                    configService.setRegistrationEnabled(want);
                    return null;
                },
                v -> {
                    updateToggleLabel(want);
                    DialogUtils.showInfo("已更新",
                            want ? "注册已开放" : "注册已关闭");
                }
        );
    }

    @FXML
    private void onRefreshPending() {
        loadPendingList();
    }

    private void loadPendingList() {
        FxUtils.runAsync(
                () -> registrationRepo.findByStatusOrderBySubmittedAtDesc(RegistrationStatus.PENDING),
                list -> {
                    pendingTable.setItems(FXCollections.observableArrayList(list));
                    pendingCountLabel.setText("共 " + list.size() + " 条");
                }
        );
    }

    private void loadHistoryList() {
        FxUtils.runAsync(
                () -> registrationRepo.findByStatusNotOrderBySubmittedAtDesc(RegistrationStatus.PENDING),
                list -> historyTable.setItems(FXCollections.observableArrayList(list))
        );
    }
}