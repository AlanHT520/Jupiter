package net.alan.jupiter.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDto {

    private String id;
    private String studentNo;
    private String name;
    private String gender;
    private String className;
    private String roomId;
    private String roomNumber;
    private String buildingId;
    private String buildingName;
    private String bedNumber;
    private String phone;
    private Boolean isNewStudent;
    private LocalDate longSickLeaveStartDate;
    private LocalDate longSickLeaveEndDate;
    private LocalDate militaryStartDate;
    private LocalDate militaryEndDate;
    private LocalDate internStartDate;
    private LocalDate internEndDate;
    private LocalDate dayStudentStartDate;
    private LocalDate dayStudentEndDate;
    private String checkStatus;
    private Integer statusCode;
    private String presetStatus;
    private String statusSource;
    private String remark;
    private String defaultStatus;
    private String lateReturnStatus;
}