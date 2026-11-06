package net.alan.jupiter.application.summary;

import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.enums.TaskStatus;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.domain.model.ClassInfo;
import net.alan.jupiter.domain.model.Counselor;
import net.alan.jupiter.domain.model.Student;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.domain.repository.CheckRecordRepository;
import net.alan.jupiter.domain.repository.ClassInfoRepository;
import net.alan.jupiter.domain.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryAggregatorTest {

    private static final LocalDate DAY = LocalDate.of(2025, 5, 20);

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CheckRecordRepository checkRecordRepository;

    @Mock
    private ClassInfoRepository classInfoRepository;

    private SummaryAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new SummaryAggregator(taskRepository, checkRecordRepository, classInfoRepository);
    }

    @Test
    @DisplayName("scopeSummary：应到/实到/请假/旷宿计数与 rate 计算")
    void scopeSummaryCountsStatusesAndRate() {
        Task task = task("t1", DAY, "1号楼", null);
        when(taskRepository.findByTaskDateBetween(
                any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<Task>(List.of(task)));
        when(checkRecordRepository.findByTaskIdIn(anyList())).thenReturn(List.of(
                record("r1", task, student("s1", "张三", "计算机2301", "计算机学院", "1号楼"),
                        CheckStatus.PRESENT),
                record("r2", task, student("s2", "李四", "计算机2301", "计算机学院", "1号楼"),
                        CheckStatus.PERSONAL_LEAVE),
                record("r3", task, student("s3", "王五", "计算机2301", "计算机学院", "1号楼"),
                        CheckStatus.SICK_LEAVE),
                record("r4", task, student("s4", "赵六", "计算机2301", "计算机学院", "1号楼"),
                        CheckStatus.ABSENT),
                record("r5", task, student("s5", "孙七", "计算机2301", "计算机学院", "1号楼"),
                        CheckStatus.DAY_STUDENT),
                record("r6", task, student("s6", "周八", "计算机2301", "计算机学院", "1号楼"),
                        CheckStatus.MILITARY)));

        SummarySnapshot snapshot = aggregator.scopeSummary(DAY, DAY, null, null);

        assertEquals(SummarySnapshot.Scope.SCOPE, snapshot.scope());
        assertNotNull(snapshot.asOf());
        assertEquals(1, snapshot.taskCount());
        assertEquals(6, snapshot.studentTotal());
        // 走读不计入应到：6 人 - 1 走读 = 5
        assertEquals(5, snapshot.shouldCount());
        assertEquals(1, snapshot.actualCount());
        assertEquals(2, snapshot.leaveCount());
        assertEquals(1, snapshot.absentCount());
        // 实到率 = 1 / 5 * 100
        assertEquals(20.0, snapshot.rate(), 1e-9);

        SummarySnapshot.Item dateItem = item(snapshot, SummarySnapshot.DIM_DATE, "2025-05-20");
        assertEquals(5, dateItem.shouldCount());
        assertEquals(1, dateItem.actualCount());
        assertEquals(20.0, dateItem.rate(), 1e-9);

        SummarySnapshot.Item collegeItem = item(snapshot, SummarySnapshot.DIM_COLLEGE, "计算机学院");
        assertEquals(6, collegeItem.studentTotal());
        assertEquals(5, collegeItem.shouldCount());
        assertEquals(1, collegeItem.actualCount());

        SummarySnapshot.Item buildingItem = item(snapshot, SummarySnapshot.DIM_BUILDING, "1号楼");
        assertEquals(6, buildingItem.studentTotal());
        assertEquals(6, item(snapshot, SummarySnapshot.DIM_CLASS_NAME, "计算机2301").studentTotal());
    }

    @Test
    @DisplayName("scopeSummary：学院筛选只统计指定学院的学生")
    void scopeSummaryFiltersByCollege() {
        Task task = task("t1", DAY, "1号楼", null);
        when(taskRepository.findByTaskDateBetween(
                any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<Task>(List.of(task)));
        when(checkRecordRepository.findByTaskIdIn(anyList())).thenReturn(List.of(
                record("r1", task, student("s1", "张三", "计算机2301", "计算机学院", "1号楼"),
                        CheckStatus.PRESENT),
                record("r2", task, student("s2", "李四", "英语2301", "外国语学院", "1号楼"),
                        CheckStatus.ABSENT)));

        SummarySnapshot snapshot = aggregator.scopeSummary(DAY, DAY, "计算机学院", null);

        assertEquals(1, snapshot.taskCount());
        assertEquals(1, snapshot.studentTotal());
        assertEquals(1, snapshot.shouldCount());
        assertEquals(1, snapshot.actualCount());
        assertEquals(0, snapshot.absentCount());
        assertEquals(100.0, snapshot.rate(), 1e-9);
        assertEquals(1, item(snapshot, SummarySnapshot.DIM_COLLEGE, "计算机学院").studentTotal());
        assertTrue(snapshot.items().stream()
                .noneMatch(i -> "外国语学院".equals(i.key())), "被过滤的学院不应出现在聚合项中");
    }

    @Test
    @DisplayName("scopeSummary：楼栋筛选只统计指定楼栋的任务")
    void scopeSummaryFiltersByBuilding() {
        Task building1 = task("t1", DAY, "1号楼", null);
        Task building2 = task("t2", DAY, "2号楼", null);
        when(taskRepository.findByTaskDateBetween(
                any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<Task>(List.of(building1, building2)));
        // 楼栋筛选后应只按 t2 取记录：用精确参数桩（严格桩会校验入参）
        when(checkRecordRepository.findByTaskIdIn(List.of("t2"))).thenReturn(List.of(
                record("r2", building2, student("s2", "李四", "计算机2301", "计算机学院", "2号楼"),
                        CheckStatus.ABSENT)));

        SummarySnapshot snapshot = aggregator.scopeSummary(DAY, DAY, null, "2号楼");

        assertEquals(1, snapshot.taskCount());
        assertEquals(1, snapshot.studentTotal());
        assertEquals(1, snapshot.shouldCount());
        assertEquals(1, snapshot.absentCount());
        assertEquals(1, item(snapshot, SummarySnapshot.DIM_BUILDING, "2号楼").studentTotal());
    }

    @Test
    @DisplayName("scopeSummary：辅导员口径只统计分配给自己的任务")
    void scopeSummaryRestrictsToOwnTasks() {
        Counselor owner = Counselor.builder().id("c1").name("辅导员一").role("COUNSELOR").build();
        Counselor other = Counselor.builder().id("c2").name("辅导员二").role("COUNSELOR").build();
        Task mine = task("t1", DAY, "1号楼", owner);
        Task others = task("t2", DAY, "1号楼", other);
        when(taskRepository.findByTaskDateBetween(
                any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<Task>(List.of(mine, others)));
        when(checkRecordRepository.findByTaskIdIn(anyList())).thenReturn(List.of(
                record("r1", mine, student("s1", "张三", "计算机2301", "计算机学院", "1号楼"),
                        CheckStatus.PRESENT)));

        SummarySnapshot snapshot = aggregator.scopeSummary(DAY, DAY, null, null, "c1");

        assertEquals(1, snapshot.taskCount());
        assertEquals(1, snapshot.studentTotal());
        assertEquals(1, snapshot.actualCount());
    }

    @Test
    @DisplayName("taskSummary：单任务按学生维度统计并给出聚合项")
    void taskSummaryCountsSingleTask() {
        Task task = task("t1", DAY, "1号楼", null);
        when(taskRepository.findById("t1")).thenReturn(Optional.of(task));
        when(checkRecordRepository.findByTaskId("t1")).thenReturn(List.of(
                record("r1", task, student("s1", "张三", "计算机2301", "计算机学院", "1号楼"),
                        CheckStatus.PRESENT),
                record("r2", task, student("s2", "李四", "计算机2301", "计算机学院", "1号楼"),
                        CheckStatus.ABSENT)));

        SummarySnapshot snapshot = aggregator.taskSummary("t1");

        assertEquals(SummarySnapshot.Scope.TASK, snapshot.scope());
        assertEquals(1, snapshot.taskCount());
        assertEquals(2, snapshot.studentTotal());
        assertEquals(2, snapshot.shouldCount());
        assertEquals(1, snapshot.actualCount());
        assertEquals(0, snapshot.leaveCount());
        assertEquals(1, snapshot.absentCount());
        assertEquals(50.0, snapshot.rate(), 1e-9);
        assertEquals(2, item(snapshot, SummarySnapshot.DIM_BUILDING, "1号楼").studentTotal());
        assertEquals(2, item(snapshot, SummarySnapshot.DIM_CLASS_NAME, "计算机2301").studentTotal());
        // 单任务只有一个日期，不重复下发 date 维度
        assertTrue(snapshot.items().stream()
                .noneMatch(i -> SummarySnapshot.DIM_DATE.equals(i.dimension())));
    }

    @Test
    @DisplayName("allTasksSummary：按学生去重并取最新记录")
    void allTasksSummaryDedupesByStudentTakingLatestRecord() {
        Task oldTask = task("t1", DAY.minusDays(1), "1号楼", null);
        Task newTask = task("t2", DAY, "1号楼", null);
        when(taskRepository.findCompletedTasks(TaskStatus.COMPLETED))
                .thenReturn(List.of(oldTask, newTask));
        // 故意打乱顺序，验证实现按任务日期取最新而不是按列表顺序
        when(checkRecordRepository.findByTaskIdIn(anyList())).thenReturn(List.of(
                record("r-old", oldTask,
                        student("s1", "张三", "计算机2301", "计算机学院", "1号楼"),
                        CheckStatus.ABSENT, DAY.minusDays(1).atTime(22, 0)),
                record("r-new", newTask,
                        student("s1", "张三", "计算机2301", "计算机学院", "1号楼"),
                        CheckStatus.PRESENT, DAY.atTime(22, 0))));

        SummarySnapshot snapshot = aggregator.allTasksSummary();

        assertEquals(SummarySnapshot.Scope.ALL, snapshot.scope());
        assertEquals(2, snapshot.taskCount());
        assertEquals(1, snapshot.studentTotal());
        assertEquals(1, snapshot.shouldCount());
        assertEquals(1, snapshot.actualCount());
        assertEquals(0, snapshot.absentCount());
        assertEquals(100.0, snapshot.rate(), 1e-9);
        assertEquals(1, item(snapshot, SummarySnapshot.DIM_COLLEGE, "计算机学院").studentTotal());
    }

    @Test
    @DisplayName("学院缺失时按班级名回查 ClassInfo.department")
    void collegeFallsBackToClassInfoDepartment() {
        Task task = task("t1", DAY, "1号楼", null);
        Student withoutClassInfo = Student.builder()
                .id("s1").studentNo("S1").name("张三").className("计算机2301")
                .buildingName("1号楼").build();
        when(taskRepository.findByTaskDateBetween(
                any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<Task>(List.of(task)));
        when(checkRecordRepository.findByTaskIdIn(anyList())).thenReturn(List.of(
                record("r1", task, withoutClassInfo, CheckStatus.PRESENT)));
        when(classInfoRepository.findAll()).thenReturn(List.of(
                ClassInfo.builder().id("c1").name("计算机2301").department("计算机学院").build()));

        SummarySnapshot snapshot = aggregator.scopeSummary(DAY, DAY, null, null);

        assertEquals(1, item(snapshot, SummarySnapshot.DIM_COLLEGE, "计算机学院").studentTotal());
    }

    @Test
    @DisplayName("空范围：任务数为 0、rate 为 0、聚合项为空")
    void emptyScopeReturnsZeroSnapshot() {
        when(taskRepository.findByTaskDateBetween(
                any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<Task>(List.of()));

        SummarySnapshot snapshot = aggregator.scopeSummary(DAY, DAY, null, null);

        assertEquals(0, snapshot.taskCount());
        assertEquals(0, snapshot.studentTotal());
        assertEquals(0, snapshot.shouldCount());
        assertEquals(0.0, snapshot.rate(), 1e-9);
        assertTrue(snapshot.items().isEmpty());
    }

    @Test
    @DisplayName("taskSummary：任务不存在时抛出业务异常")
    void taskSummaryThrowsWhenTaskMissing() {
        when(taskRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(BizException.class, () -> aggregator.taskSummary("missing"));
    }

    // ------------------------------------------------------------------
    // 测试数据构造
    // ------------------------------------------------------------------

    private static Task task(String id, LocalDate date, String buildingName, Counselor counselor) {
        return Task.builder()
                .id(id)
                .title("晚查寝-" + id)
                .taskNo("NO-" + id)
                .taskDate(date)
                .buildingName(buildingName)
                .assignedCounselor(counselor)
                .build();
    }

    private static Student student(String id, String name, String className,
                                   String department, String buildingName) {
        ClassInfo classInfo = department == null ? null : ClassInfo.builder()
                .id("class-" + className)
                .name(className)
                .department(department)
                .build();
        return Student.builder()
                .id(id)
                .studentNo("S-" + id)
                .name(name)
                .className(className)
                .classInfo(classInfo)
                .buildingName(buildingName)
                .floorNumber(1)
                .isActive(true)
                .build();
    }

    private static CheckRecord record(String id, Task task, Student student, CheckStatus status) {
        return record(id, task, student, status, task.getTaskDate() == null
                ? null : task.getTaskDate().atTime(22, 0));
    }

    private static CheckRecord record(String id, Task task, Student student,
                                      CheckStatus status, LocalDateTime submittedAt) {
        return CheckRecord.builder()
                .id(id)
                .task(task)
                .student(student)
                .studentNo(student.getStudentNo())
                .studentName(student.getName())
                .checkStatus(status)
                .submittedAt(submittedAt)
                .build();
    }

    private static SummarySnapshot.Item item(SummarySnapshot snapshot, String dimension, String key) {
        return snapshot.items().stream()
                .filter(i -> dimension.equals(i.dimension()) && key.equals(i.key()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少聚合项: " + dimension + "=" + key));
    }
}
