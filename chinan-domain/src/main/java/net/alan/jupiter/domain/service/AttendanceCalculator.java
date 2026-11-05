package net.alan.jupiter.domain.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.domain.model.Floor;
import net.alan.jupiter.domain.model.Student;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttendanceCalculator {

    private final CheckRuleService checkRuleService;

    public AttendanceCalculator(CheckRuleService checkRuleService) {
        this.checkRuleService = checkRuleService;
    }

    /**
     * 计算楼层统计
     */
    public FloorAttendance calcFloorAttendance(Floor floor, List<Student> floorStudents,
                                                List<CheckRecord> records) {
        Map<String, CheckRecord> recordMap = records.stream()
                .filter(r -> r.getStudent() != null)
                .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r, (a, b) -> a));

        int should = 0, actual = 0;
        int personalLeave = 0, sickLeave = 0, lateReturn = 0;
        int military = 0, intern = 0, dayStudent = 0, longSickLeave = 0, absent = 0;
        int unchecked = 0;

        for (Student s : floorStudents) {
            if (s.getIsActive() != null && !s.getIsActive()) continue;

            CheckRecord record = recordMap.get(s.getId());
            CheckStatus status = resolveCheckStatus(record, s);

            if (status == null) {
                status = CheckStatus.UNKNOWN;
            }

            // 统一口径：走读 / 长期病假不占"应到"，但仍单独计数
            if (status != CheckStatus.DAY_STUDENT && status != CheckStatus.LONG_SICK_LEAVE) {
                should++;
            }

            switch (status) {
                case PRESENT:
                    actual++;
                    break;
                case PERSONAL_LEAVE:
                    personalLeave++;
                    break;
                case SICK_LEAVE:
                    sickLeave++;
                    break;
                case MILITARY:
                    military++;
                    break;
                case INTERN:
                    intern++;
                    break;
                case DAY_STUDENT:
                    dayStudent++;
                    break;
                case LONG_SICK_LEAVE:
                    longSickLeave++;
                    break;
                case LATE_RETURN:
                    lateReturn++;
                    break;
                case ABSENT:
                    absent++;
                    break;
                case UNKNOWN:
                    unchecked++;
                    break;
            }
        }

        return FloorAttendance.builder()
                .floorName(floor.getFloorName() != null ? floor.getFloorName()
                        : String.valueOf(floor.getFloorNumber()))
                .should(should).actual(actual)
                .personalLeave(personalLeave).sickLeave(sickLeave)
                .lateReturn(lateReturn)
                .military(military).intern(intern)
                .dayStudent(dayStudent).longSickLeave(longSickLeave).absent(absent)
                .unchecked(unchecked)
                .build();
    }

    private CheckStatus resolveCheckStatus(CheckRecord record, Student student) {
        if (record != null && record.getCheckStatus() != null) {
            return record.getCheckStatus();
        }
        CheckStatus preset = checkRuleService.determinePresetStatus(student);
        if (preset != null) {
            return preset;
        }
        return CheckStatus.UNKNOWN;
    }

    /**
     * 汇总楼层统计到楼栋
     */
    public AttendanceSummary calcBuildingSummary(List<FloorAttendance> floorAttendances,
                                                  String buildingName) {
        AttendanceSummary summary = new AttendanceSummary();
        summary.setBuildingName(buildingName);

        for (FloorAttendance fa : floorAttendances) {
            summary.setShouldBe(summary.getShouldBe() + fa.getShould());
            summary.setActualPresent(summary.getActualPresent() + fa.getActual());
            summary.setPersonalLeaveCount(summary.getPersonalLeaveCount() + fa.getPersonalLeave());
            summary.setSickLeaveCount(summary.getSickLeaveCount() + fa.getSickLeave());
            summary.setLateReturnCount(summary.getLateReturnCount() + fa.getLateReturn());
            summary.setMilitaryCount(summary.getMilitaryCount() + fa.getMilitary());
            summary.setInternCount(summary.getInternCount() + fa.getIntern());
            summary.setDayStudentCount(summary.getDayStudentCount() + fa.getDayStudent());
            summary.setLongSickLeaveCount(summary.getLongSickLeaveCount() + fa.getLongSickLeave());
            summary.setAbsentCount(summary.getAbsentCount() + fa.getAbsent());
            summary.setUncheckedCount(summary.getUncheckedCount() + fa.getUnchecked());
        }
        return summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FloorAttendance {
        private String floorName;
        private int should;
        private int actual;
        private int personalLeave;
        private int sickLeave;
        private int lateReturn;
        private int military;
        private int intern;
        private int dayStudent;
        private int longSickLeave;
        private int absent;
        private int unchecked;
    }
}