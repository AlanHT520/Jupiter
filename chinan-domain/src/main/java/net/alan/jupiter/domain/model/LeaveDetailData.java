package net.alan.jupiter.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.alan.jupiter.domain.enums.CheckStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 请假明细数据（班级维度）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveDetailData {

    private String date;
    private String collegeName;
    private HeaderSummary header;

    @Builder.Default
    private List<ClassLeaveRow> rows = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeaderSummary {
        private int totalStudents;
        private int male;
        private int female;
        private int actual;
        private int actualMale;
        private int actualFemale;
        private int leaveTotal;
        private int personalLeave;
        private int sickLeave;
        private int longSickLeave;
        private int intern;
        private int lateReturn;
        private int dayStudent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassLeaveRow {
        private String className;
        private String headTeacher;
        private int enrolled;
        private int actual;
        private int leave;
        private int military;
        private int transferOut;
        private int dayStudent;
        private int absent;
        private int longSickLeave;
        private int intern;
        private int lateReturn;
        private int suspend;
        private int dropOut;
        private String specialCase;

        @Builder.Default
        private List<LeaveDetailItem> details = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaveDetailItem {
        private String studentName;
        private CheckStatus status;
        private String gender;
        private String proofFile;
    }
}