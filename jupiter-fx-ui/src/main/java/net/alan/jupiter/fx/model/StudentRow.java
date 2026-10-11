package net.alan.jupiter.fx.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import net.alan.jupiter.domain.model.Student;
import net.alan.jupiter.domain.service.CheckRuleService;

public class StudentRow {
    private final StringProperty studentNo;
    private final StringProperty name;
    private final StringProperty gender;
    private final StringProperty className;
    private final StringProperty buildingName;
    private final StringProperty roomNumber;
    private final StringProperty bedNumber;
    private final StringProperty phone;
    private final StringProperty statusLabel;

    public StudentRow(Student s) {
        this.studentNo = new SimpleStringProperty(s.getStudentNo());
        this.name = new SimpleStringProperty(s.getName());
        this.gender = new SimpleStringProperty(s.getGender());
        this.className = new SimpleStringProperty(s.getClassName());
        this.buildingName = new SimpleStringProperty(s.getBuildingName());
        this.roomNumber = new SimpleStringProperty(s.getRoomNumber());
        this.bedNumber = new SimpleStringProperty(s.getBedNumber());
        this.phone = new SimpleStringProperty(s.getPhone());
        this.statusLabel = new SimpleStringProperty(CheckRuleService.getStatusLabel(s));
    }

    public StringProperty getStudentNo() {
        return studentNo;
    }

    public StringProperty getName() {
        return name;
    }

    public StringProperty getGender() {
        return gender;
    }

    public StringProperty getClassName() {
        return className;
    }

    public StringProperty getBuildingName() {
        return buildingName;
    }

    public StringProperty getRoomNumber() {
        return roomNumber;
    }

    public StringProperty getBedNumber() {
        return bedNumber;
    }

    public StringProperty getPhone() {
        return phone;
    }

    public StringProperty getStatusLabel() {
        return statusLabel;
    }
}