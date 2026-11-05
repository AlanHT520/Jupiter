package net.alan.jupiter.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 晚查寝数据汇总（学院级 + 楼栋级 + 楼层级 三段式）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NightCheckSummaryData {

    private String collegeName;
    private String date;

    private int totalShould;
    private int totalActual;
    private int totalPersonalLeave;
    private int totalSickLeave;
    private int totalDayStudent;
    private int totalLongSickLeave;
    private int totalMilitary;
    private int totalAbsent;
    private int totalUnchecked;
    private int totalLateReturn;
    private int totalIntern;

    @Builder.Default
    private List<BuildingRow> buildingRows = new ArrayList<>();

    @Builder.Default
    private List<FloorRow> floorRows = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BuildingRow {
        private String buildingName;
        private int shouldAmount;
        private int actualAmount;
        private int personalLeave;
        private int sickLeave;
        private int military;
        private int dayStudent;
        private int absent;
        private int longSickLeave;
        private int lateReturn;
        private int intern;
        private int unchecked;
        private String absentNames;
        private String remark;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FloorRow {
        private String buildingName;
        private String floorLabel;
        private int shouldAmount;
        private int actualAmount;
        private int personalLeave;
        private int sickLeave;
        private int military;
        private int absent;
    }
}