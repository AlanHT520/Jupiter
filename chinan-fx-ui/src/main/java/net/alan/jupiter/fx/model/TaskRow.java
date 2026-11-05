package net.alan.jupiter.fx.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.Data;
import net.alan.jupiter.domain.model.Task;

@Data
public class TaskRow {
    private final StringProperty taskId;
    private final StringProperty taskNo;
    private final StringProperty title;
    private final StringProperty buildingName;
    private final StringProperty floorLabel;
    private final StringProperty roomNumbers;
    private final StringProperty assignedCounselorName;
    private final StringProperty status;
    private final StringProperty statusName;
    private final StringProperty totalRooms;
    private final StringProperty checkedRooms;

    public TaskRow(Task t) {
        this.taskId = new SimpleStringProperty(t.getId());
        this.taskNo = new SimpleStringProperty(t.getTaskNo());
        this.title = new SimpleStringProperty(t.getTitle());
        this.buildingName = new SimpleStringProperty(t.getBuildingName());
        this.floorLabel = new SimpleStringProperty(
                t.getFloorNumber() != null ? "F" + t.getFloorNumber() : "");
        this.roomNumbers = new SimpleStringProperty(
                t.getRoomNumbers() != null ? t.getRoomNumbers() : "全部");
        this.assignedCounselorName = new SimpleStringProperty(
                t.getAssignedCounselorName() != null ? t.getAssignedCounselorName() : "未指定");
        this.status = new SimpleStringProperty(
                t.getStatus() != null ? t.getStatus().getLabel() : "");
        this.statusName = new SimpleStringProperty(
                t.getStatus() != null ? t.getStatus().name() : "");
        this.totalRooms = new SimpleStringProperty(t.getTotalRooms() != null ? String.valueOf(t.getTotalRooms()) : "");
        this.checkedRooms = new SimpleStringProperty(t.getCheckedRooms() != null ? String.valueOf(t.getCheckedRooms()) : "");
    }
}