package net.alan.jupiter.domain.service;

import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.domain.model.Floor;
import net.alan.jupiter.domain.model.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttendanceCalculatorTest {

    private AttendanceCalculator attendanceCalculator;

    @BeforeEach
    void setUp() {
        CheckRuleService checkRuleService = new CheckRuleService();
        attendanceCalculator = new AttendanceCalculator(checkRuleService);
    }

    @Test
    @DisplayName("计算楼层考勤统计")
    void shouldCalculateFloorAttendance() {
        Floor floor = Floor.builder()
                .floorNumber(1)
                .floorName("1层")
                .build();

        Student s1 = Student.builder().id("s1").name("张三").isActive(true).build();
        Student s2 = Student.builder().id("s2").name("李四").isActive(true).build();
        Student s3 = Student.builder().id("s3").name("王五").isActive(true).build();
        List<Student> students = Arrays.asList(s1, s2, s3);

        CheckRecord r1 = CheckRecord.builder()
                .student(s1).checkStatus(CheckStatus.PRESENT).build();
        CheckRecord r2 = CheckRecord.builder()
                .student(s2).checkStatus(CheckStatus.ABSENT).build();
        List<CheckRecord> records = Arrays.asList(r1, r2);

        AttendanceCalculator.FloorAttendance result =
                attendanceCalculator.calcFloorAttendance(floor, students, records);

        assertEquals(3, result.getShould());
        assertEquals(1, result.getActual());
        assertEquals(1, result.getAbsent());
    }

    @Test
    @DisplayName("应排除非活跃学生")
    void shouldExcludeInactiveStudents() {
        Floor floor = Floor.builder().floorNumber(1).floorName("1层").build();

        Student s1 = Student.builder().id("s1").name("张三").isActive(true).build();
        Student s2 = Student.builder().id("s2").name("李四").isActive(false).build();
        List<Student> students = Arrays.asList(s1, s2);
        List<CheckRecord> records = Collections.emptyList();

        AttendanceCalculator.FloorAttendance result =
                attendanceCalculator.calcFloorAttendance(floor, students, records);

        assertEquals(1, result.getShould());
    }

    @Test
    @DisplayName("汇总楼层统计到楼栋")
    void shouldSummarizeFloorToBuilding() {
        AttendanceCalculator.FloorAttendance fa1 = AttendanceCalculator.FloorAttendance.builder()
                .should(10).actual(8).personalLeave(1).sickLeave(1)
                .absent(0).build();
        AttendanceCalculator.FloorAttendance fa2 = AttendanceCalculator.FloorAttendance.builder()
                .should(20).actual(18).personalLeave(1).sickLeave(0)
                .absent(1).build();

        AttendanceSummary summary = attendanceCalculator
                .calcBuildingSummary(Arrays.asList(fa1, fa2), "B1");

        assertEquals("B1", summary.getBuildingName());
        assertEquals(30, summary.getShouldBe());
        assertEquals(26, summary.getActualPresent());
        assertEquals(2, summary.getPersonalLeaveCount());
        assertEquals(1, summary.getSickLeaveCount());
        assertEquals(3, summary.getLeaveCount());
        assertEquals(1, summary.getAbsentCount());
    }

    @Test
    @DisplayName("空楼层列表不应出错")
    void shouldHandleEmptyFloorList() {
        List<AttendanceCalculator.FloorAttendance> empty = Collections.emptyList();
        AttendanceSummary summary = attendanceCalculator
                .calcBuildingSummary(empty, "B1");

        assertEquals(0, summary.getShouldBe());
        assertEquals(0, summary.getActualPresent());
    }

    @Test
    @DisplayName("无记录时应使用默认状态")
    void shouldUseDefaultStatusWhenNoRecord() {
        Floor floor = Floor.builder().floorNumber(1).floorName("1层").build();

        Student s1 = Student.builder().id("s1").name("张三").isActive(true).build();
        List<Student> students = List.of(s1);
        List<CheckRecord> records = Collections.emptyList();

        AttendanceCalculator.FloorAttendance result =
                attendanceCalculator.calcFloorAttendance(floor, students, records);

        assertEquals(1, result.getShould());
        assertEquals(0, result.getActual());
    }

    @Test
    @DisplayName("走读与长期病假不计入应到")
    void shouldExcludeDayStudentAndLongSickLeaveFromShould() {
        Floor floor = Floor.builder().floorNumber(1).floorName("1层").build();

        java.time.LocalDate today = java.time.LocalDate.now();
        Student dayStudent = Student.builder()
                .id("s1").name("走读生").isActive(true)
                .dayStudentStartDate(today.minusDays(1))
                .dayStudentEndDate(today.plusDays(30))
                .build();
        Student longSick = Student.builder()
                .id("s2").name("长期病假生").isActive(true)
                .longSickLeaveStartDate(today.minusDays(1))
                .longSickLeaveEndDate(today.plusDays(30))
                .build();
        Student normal = Student.builder().id("s3").name("在寝生").isActive(true).build();

        AttendanceCalculator.FloorAttendance result = attendanceCalculator.calcFloorAttendance(
                floor, Arrays.asList(dayStudent, longSick, normal), Collections.emptyList());

        assertEquals(1, result.getShould());
        // 该学生尚无查寝记录、也无预设状态，应计入"未查"而不是"在寝"
        assertEquals(0, result.getActual());
        assertEquals(1, result.getUnchecked());
        assertEquals(1, result.getDayStudent());
        assertEquals(1, result.getLongSickLeave());
    }
}