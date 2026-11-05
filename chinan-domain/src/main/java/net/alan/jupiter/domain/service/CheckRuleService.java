package net.alan.jupiter.domain.service;

import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.domain.model.Student;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class CheckRuleService {

    /**
     * 根据学生的预置状态（日期范围判断）返回预设查寝状态。
     * 如果不在任何预置范围内，返回 null 表示无预设状态，
     * 调用方应区分「无预设状态」与「已查寝-在寝」。
     */
    public CheckStatus determinePresetStatus(Student student) {
        LocalDate today = LocalDate.now();

        if (isInDateRange(student.getLongSickLeaveStartDate(), student.getLongSickLeaveEndDate(), today)) {
            return CheckStatus.LONG_SICK_LEAVE;
        }
        if (isInDateRange(student.getMilitaryStartDate(), student.getMilitaryEndDate(), today)) {
            return CheckStatus.MILITARY;
        }
        if (isInDateRange(student.getInternStartDate(), student.getInternEndDate(), today)) {
            return CheckStatus.INTERN;
        }
        if (isInDateRange(student.getDayStudentStartDate(), student.getDayStudentEndDate(), today)) {
            return CheckStatus.DAY_STUDENT;
        }
        return null;
    }

    /**
     * @deprecated 使用 {@link #determinePresetStatus(Student)} 替代，
     *             该方法返回 null 表示无预设，避免将未查寝学生误判为在寝。
     */
    @Deprecated
    public CheckStatus determineDefaultStatus(Student student) {
        CheckStatus preset = determinePresetStatus(student);
        return preset != null ? preset : CheckStatus.PRESENT;
    }

    private static boolean isInDateRange(LocalDate start, LocalDate end, LocalDate today) {
        if (start == null) {
            return false;
        }
        LocalDate effectiveEnd = end != null ? end : today.plusDays(1);
        return !today.isBefore(start) && today.isBefore(effectiveEnd);
    }

    public boolean isAbnormal(CheckStatus status) {
        return status == CheckStatus.ABSENT
                || status == CheckStatus.LATE_RETURN
                || status == CheckStatus.UNKNOWN;
    }

    public int countByStatus(List<CheckRecord> records, CheckStatus status) {
        return (int) records.stream()
                .filter(r -> r.getCheckStatus() == status)
                .count();
    }

    /**
     * 计算一个房间的状态分布
     */
    public Map<CheckStatus, Long> calculateRoomStatistics(List<CheckRecord> records) {
        Map<CheckStatus, Long> stats = new EnumMap<>(CheckStatus.class);
        for (CheckStatus status : CheckStatus.values()) {
            stats.put(status, records.stream().filter(r -> r.getCheckStatus() == status).count());
        }
        return stats;
    }

    /**
     * 获取学生的当前状态标签（用于UI展示），与 determineDefaultStatus 逻辑一致但返回中文标签。
     */
    public static String getStatusLabel(Student student) {
        LocalDate today = LocalDate.now();

        if (isInDateRange(student.getLongSickLeaveStartDate(), student.getLongSickLeaveEndDate(), today)) {
            return "长期病假";
        }
        if (isInDateRange(student.getMilitaryStartDate(), student.getMilitaryEndDate(), today)) {
            return "征兵";
        }
        if (isInDateRange(student.getInternStartDate(), student.getInternEndDate(), today)) {
            return "实习";
        }
        if (isInDateRange(student.getDayStudentStartDate(), student.getDayStudentEndDate(), today)) {
            return "走读";
        }
        if (!Boolean.TRUE.equals(student.getIsActive())) {
            return "已离校";
        }
        return "在校";
    }
}