package net.alan.jupiter.fx.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.fx.model.CheckRecordRow;
import net.alan.jupiter.fx.util.DialogUtils;
import net.alan.jupiter.fx.util.FxUtils;
import net.alan.jupiter.domain.repository.CheckRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckRecordController {

    private final CheckRecordRepository checkRecordRepository;

    @FXML
    private TableView<CheckRecordRow> recordTable;

    @FXML
    private TableColumn<CheckRecordRow, String> colRoom;

    @FXML
    private TableColumn<CheckRecordRow, String> colStudentNo;

    @FXML
    private TableColumn<CheckRecordRow, String> colStudentName;

    @FXML
    private TableColumn<CheckRecordRow, String> colStatus;

    @FXML
    private TableColumn<CheckRecordRow, String> colStatusSource;

    @FXML
    private TableColumn<CheckRecordRow, String> colSubmittedBy;

    @FXML
    private Label pageInfoLabel;

    @FXML
    private Button prevBtn;

    @FXML
    private Button nextBtn;

    private int page = 0;
    private static final int SIZE = 50;
    private long totalCount = 0;

    @FXML
    public void initialize() {
        recordTable.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);

        colRoom.setCellValueFactory(c -> c.getValue().getRoomNumber());
        colStudentNo.setCellValueFactory(c -> c.getValue().getStudentNo());
        colStudentName.setCellValueFactory(c -> c.getValue().getStudentName());
        colStatus.setCellValueFactory(c -> c.getValue().getStatus());
        colStatusSource.setCellValueFactory(c -> c.getValue().getStatusSource());
        colSubmittedBy.setCellValueFactory(c -> c.getValue().getSubmittedBy());

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
    private void onRefresh() {
        page = 0;
        loadData();
    }

    @FXML
    private void onClearAll() {
        boolean confirm = DialogUtils.showConfirm("确认清除",
                "此操作将清除所有查寝记录，且不可恢复。\n确定要继续吗？");
        if (!confirm) return;

        FxUtils.runAsync(
                () -> {
                    long count = checkRecordRepository.count();
                    checkRecordRepository.deleteAll();
                    return count;
                },
                count -> {
                    page = 0;
                    loadData();
                    DialogUtils.showInfo("清除完成", "已清除 " + count + " 条查寝记录");
                },
                ex -> {
                    log.error("清除查寝记录失败", ex);
                    DialogUtils.showError("清除失败", "系统异常，请稍后再试");
                }
        );
    }

    private void loadData() {
        FxUtils.runAsync(
                () -> {
                    Page<net.alan.jupiter.domain.model.CheckRecord> result = checkRecordRepository.findAll(
                            PageRequest.of(page, SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
                    return new Object[]{
                            result.map(CheckRecordRow::new).getContent(),
                            result.getTotalElements()
                    };
                },
                result -> {
                    @SuppressWarnings("unchecked")
                    java.util.List<CheckRecordRow> rows = (java.util.List<CheckRecordRow>) result[0];
                    long total = (long) result[1];
                    recordTable.setItems(FXCollections.observableArrayList(rows));
                    totalCount = total;
                    pageInfoLabel.setText("第 " + (page + 1) + " 页，共 " + total + " 条");
                    updatePaginationButtons();
                }
        );
    }

    private void updatePaginationButtons() {
        prevBtn.setDisable(page == 0);
        nextBtn.setDisable((page + 1L) * SIZE >= totalCount);
    }
}