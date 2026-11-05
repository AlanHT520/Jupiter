package net.alan.jupiter.fx.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.Data;
import net.alan.jupiter.domain.model.CheckRecord;

@Data
public class CheckRecordRow {
    private final StringProperty roomNumber;
    private final StringProperty studentNo;
    private final StringProperty studentName;
    private final StringProperty status;
    private final StringProperty statusSource;
    private final StringProperty submittedBy;

    public CheckRecordRow(CheckRecord r) {
        this.roomNumber = new SimpleStringProperty(
                r.getRoomNumber() != null ? r.getRoomNumber() : "");
        this.studentNo = new SimpleStringProperty(
                r.getStudentNo() != null ? r.getStudentNo() : "");
        this.studentName = new SimpleStringProperty(
                r.getStudentName() != null ? r.getStudentName() : "");
        this.status = new SimpleStringProperty(
                r.getCheckStatus() != null ? r.getCheckStatus().getLabel() : "");
        this.statusSource = new SimpleStringProperty(
                r.getStatusSource() != null ? toStatusSourceLabel(r.getStatusSource()) : "");
        this.submittedBy = new SimpleStringProperty(
                r.getSubmittedBy() != null ? r.getSubmittedBy() : "");
    }

    private static String toStatusSourceLabel(String source) {
        return switch (source) {
            case "PE" -> "辅导员";
            case "SYS" -> "系统预设";
            case "IMPORT" -> "导入";
            case "MANUAL" -> "手动";
            default -> source;
        };
    }
}