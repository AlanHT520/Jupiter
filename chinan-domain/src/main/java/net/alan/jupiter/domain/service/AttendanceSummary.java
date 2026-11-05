package net.alan.jupiter.domain.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSummary {

    private String buildingName;
    private int shouldBe;
    private int actualPresent;
    private int personalLeaveCount;
    private int sickLeaveCount;
    private int lateReturnCount;
    private int militaryCount;
    private int internCount;
    private int dayStudentCount;
    private int longSickLeaveCount;
    private int absentCount;
    private int uncheckedCount;

    public int getLeaveCount() {
        return personalLeaveCount + sickLeaveCount;
    }
}