package net.alan.jupiter.domain.service;

import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.domain.model.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CheckRuleServiceTest {

    private CheckRuleService checkRuleService;

    @BeforeEach
    void setUp() {
        checkRuleService = new CheckRuleService();
    }

    @Test
    @DisplayName("默认状态应为在寝")
    void shouldReturnPresentByDefault() {
        Student student = Student.builder()
                .studentNo("2024001")
                .name("测试学生")
                .build();

        CheckStatus status = checkRuleService.determineDefaultStatus(student);
        assertEquals(CheckStatus.PRESENT, status);
    }

    @Test
    @DisplayName("征兵期间应返回征兵状态")
    void shouldReturnMilitaryWhenInDateRange() {
        LocalDate today = LocalDate.now();
        Student student = Student.builder()
                .studentNo("2024001")
                .name("测试学生")
                .militaryStartDate(today.minusDays(10))
                .militaryEndDate(today.plusDays(10))
                .build();

        CheckStatus status = checkRuleService.determineDefaultStatus(student);
        assertEquals(CheckStatus.MILITARY, status);
    }

    @Test
    @DisplayName("实习期间应返回实习状态")
    void shouldReturnInternWhenInDateRange() {
        LocalDate today = LocalDate.now();
        Student student = Student.builder()
                .studentNo("2024001")
                .name("测试学生")
                .internStartDate(today.minusDays(5))
                .internEndDate(today.plusDays(30))
                .build();

        CheckStatus status = checkRuleService.determineDefaultStatus(student);
        assertEquals(CheckStatus.INTERN, status);
    }

    @Test
    @DisplayName("走读期间应返回走读状态")
    void shouldReturnDayStudentWhenInDateRange() {
        LocalDate today = LocalDate.now();
        Student student = Student.builder()
                .studentNo("2024001")
                .name("测试学生")
                .dayStudentStartDate(today.minusDays(1))
                .dayStudentEndDate(today.plusDays(100))
                .build();

        CheckStatus status = checkRuleService.determineDefaultStatus(student);
        assertEquals(CheckStatus.DAY_STUDENT, status);
    }

    @Test
    @DisplayName("长期病假应返回长期病假状态")
    void shouldReturnLongSickLeave() {
        LocalDate today = LocalDate.now();
        Student student = Student.builder()
                .studentNo("2024001")
                .name("测试学生")
                .longSickLeaveStartDate(today.minusDays(10))
                .longSickLeaveEndDate(today.plusDays(20))
                .build();

        CheckStatus status = checkRuleService.determineDefaultStatus(student);
        assertEquals(CheckStatus.LONG_SICK_LEAVE, status);
    }

    @Test
    @DisplayName("异常状态判断")
    void shouldIdentifyAbnormalStatus() {
        assertTrue(checkRuleService.isAbnormal(CheckStatus.ABSENT));
        assertTrue(checkRuleService.isAbnormal(CheckStatus.LATE_RETURN));
        assertTrue(checkRuleService.isAbnormal(CheckStatus.UNKNOWN));
        assertFalse(checkRuleService.isAbnormal(CheckStatus.PRESENT));
        assertFalse(checkRuleService.isAbnormal(CheckStatus.PERSONAL_LEAVE));
    }

    @Test
    @DisplayName("按状态统计记录数")
    void shouldCountByStatus() {
        CheckRecord present = CheckRecord.builder()
                .checkStatus(CheckStatus.PRESENT).build();
        CheckRecord absent = CheckRecord.builder()
                .checkStatus(CheckStatus.ABSENT).build();
        CheckRecord leave = CheckRecord.builder()
                .checkStatus(CheckStatus.PERSONAL_LEAVE).build();
        List<CheckRecord> records = Arrays.asList(present, absent, leave);

        assertEquals(1, checkRuleService.countByStatus(records, CheckStatus.PRESENT));
        assertEquals(1, checkRuleService.countByStatus(records, CheckStatus.ABSENT));
        assertEquals(0, checkRuleService.countByStatus(records, CheckStatus.MILITARY));
    }

    @Test
    @DisplayName("计算房间状态分布")
    void shouldCalculateRoomStatistics() {
        CheckRecord r1 = CheckRecord.builder().checkStatus(CheckStatus.PRESENT).build();
        CheckRecord r2 = CheckRecord.builder().checkStatus(CheckStatus.PRESENT).build();
        CheckRecord r3 = CheckRecord.builder().checkStatus(CheckStatus.ABSENT).build();
        List<CheckRecord> records = Arrays.asList(r1, r2, r3);

        Map<CheckStatus, Long> stats = checkRuleService.calculateRoomStatistics(records);
        assertEquals(2L, stats.get(CheckStatus.PRESENT));
        assertEquals(1L, stats.get(CheckStatus.ABSENT));
        assertEquals(0L, stats.get(CheckStatus.MILITARY));
    }

    @Test
    @DisplayName("征兵优先级高于实习")
    void militaryShouldTakePriorityOverIntern() {
        LocalDate today = LocalDate.now();
        Student student = Student.builder()
                .studentNo("2024001")
                .name("测试学生")
                .militaryStartDate(today.minusDays(10))
                .militaryEndDate(today.plusDays(10))
                .internStartDate(today.minusDays(5))
                .internEndDate(today.plusDays(30))
                .build();

        CheckStatus status = checkRuleService.determineDefaultStatus(student);
        assertEquals(CheckStatus.MILITARY, status);
    }

    @Test
    @DisplayName("日期范围外不应匹配")
    void shouldNotMatchOutsideDateRange() {
        LocalDate today = LocalDate.now();
        Student student = Student.builder()
                .studentNo("2024001")
                .name("测试学生")
                .militaryStartDate(today.minusDays(30))
                .militaryEndDate(today.minusDays(1))
                .build();

        CheckStatus status = checkRuleService.determineDefaultStatus(student);
        assertEquals(CheckStatus.PRESENT, status);
    }

    @Test
    @DisplayName("无结束日期表示无限期")
    void nullEndDateShouldMeanIndefinite() {
        LocalDate today = LocalDate.now();
        Student student = Student.builder()
                .studentNo("2024001")
                .name("测试学生")
                .militaryStartDate(today.minusDays(1))
                .militaryEndDate(null)
                .build();

        CheckStatus status = checkRuleService.determineDefaultStatus(student);
        assertEquals(CheckStatus.MILITARY, status);
    }
}