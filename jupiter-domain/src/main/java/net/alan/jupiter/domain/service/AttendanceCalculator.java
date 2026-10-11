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
        int newShould = 0, newActual = 0;
        int newPersonalLeave = 0, newSickLeave = 0, newLateReturn = 0;
        int newMilitary = 0, newIntern = 0;
        int newDayStudent = 0, newLongSickLeave = 0, newAbsent = 0;

        for (Student s : floorStudents) {
            if (s.getIsActive() != null && !s.getIsActive()) continue;

            boolean isNew = s.getIsNewStudent() != null && s.getIsNewStudent();
            CheckRecord record = recordMap.get(s.getId());
            CheckStatus status = resolveCheckStatus(record, s);

            if (status == null) {
                status = CheckStatus.UNKNOWN;
            }

            should++;
            if (isNew) newShould++;

            switch (status) {
                case PRESENT:
                    actual++;
                    if (isNew) newActual++;
                    break;
                case PERSONAL_LEAVE:
                    personalLeave++;
                    if (isNew) newPersonalLeave++;
                    break;
                case SICK_LEAVE:
                    sickLeave++;
                    if (isNew) newSickLeave++;
                    break;
                case MILITARY:
                    military++;
                    if (isNew) newMilitary++;
                    break;
                case INTERN:
                    intern++;
                    if (isNew) newIntern++;
                    break;
                case DAY_STUDENT:
                    dayStudent++;
                    if (isNew) newDayStudent++;
                    break;
                case LONG_SICK_LEAVE:
                    longSickLeave++;
                    if (isNew) newLongSickLeave++;
                    break;
                case LATE_RETURN:
                    lateReturn++;
                    if (isNew) newLateReturn++;
                    break;
                case ABSENT:
                    absent++;
                    if (isNew) newAbsent++;
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
                .newShould(newShould).newActual(newActual)
                .newPersonalLeave(newPersonalLeave).newSickLeave(newSickLeave)
                .newMilitary(newMilitary).newIntern(newIntern)
                .newLateReturn(newLateReturn)
                .newDayStudent(newDayStudent).newLongSickLeave(newLongSickLeave)
                .newAbsent(newAbsent)
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
            summary.setNewStudentShouldBe(summary.getNewStudentShouldBe() + fa.getNewShould());
            summary.setNewStudentActual(summary.getNewStudentActual() + fa.getNewActual());
            summary.setNewStudentPersonalLeave(summary.getNewStudentPersonalLeave() + fa.getNewPersonalLeave());
            summary.setNewStudentSickLeave(summary.getNewStudentSickLeave() + fa.getNewSickLeave());
            summary.setNewStudentLateReturn(summary.getNewStudentLateReturn() + fa.getNewLateReturn());
            summary.setNewStudentMilitary(summary.getNewStudentMilitary() + fa.getNewMilitary());
            summary.setNewStudentIntern(summary.getNewStudentIntern() + fa.getNewIntern());
            summary.setNewStudentDayStudent(summary.getNewStudentDayStudent() + fa.getNewDayStudent());
            summary.setNewStudentLongSickLeave(summary.getNewStudentLongSickLeave() + fa.getNewLongSickLeave());
            summary.setNewStudentAbsent(summary.getNewStudentAbsent() + fa.getNewAbsent());
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
        private int newShould;
        private int newActual;
        private int newPersonalLeave;
        private int newSickLeave;
        private int newLateReturn;
        private int newMilitary;
        private int newIntern;
        private int newDayStudent;
        private int newLongSickLeave;
        private int newAbsent;
    }
}