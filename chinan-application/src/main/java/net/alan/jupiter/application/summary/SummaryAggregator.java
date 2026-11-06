package net.alan.jupiter.application.summary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.enums.TaskStatus;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.domain.model.ClassInfo;
import net.alan.jupiter.domain.model.DormBuilding;
import net.alan.jupiter.domain.model.Room;
import net.alan.jupiter.domain.model.Student;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.domain.repository.CheckRecordRepository;
import net.alan.jupiter.domain.repository.ClassInfoRepository;
import net.alan.jupiter.domain.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 总汇总共享聚合器：只产出数字与聚合项，不下发明细。
 *
 * <p>三个入口分别对应三种形态：</p>
 * <ul>
 *   <li>{@link #taskSummary(String)}：单任务（L1），按学生去重统计该任务的查寝记录；</li>
 *   <li>{@link #scopeSummary(LocalDate, LocalDate, String, String)}：日期范围 / 学院 / 楼栋（L2，总汇总主形态）；</li>
 *   <li>{@link #allTasksSummary()}：全量已完成任务，按学生去重取最新记录（L3）。</li>
 * </ul>
 *
 * <p>统计口径（与既有 {@code ReportQueryService} 报表保持一致，且<b>不修改其行为</b>）：</p>
 * <ul>
 *   <li>走读（{@code DAY_STUDENT}）、长期病假（{@code LONG_SICK_LEAVE}）不计入应到；</li>
 *   <li>实到 = {@code PRESENT}；请假 = 事假 + 病假；旷宿 = {@code ABSENT}；</li>
 *   <li>{@code rate} = 实到 / 应到 * 100，保留两位小数，应到为 0 时取 0.0。</li>
 * </ul>
 *
 * <p>数据来源为 {@code CheckRecord}（已提交的查寝记录）：未产生记录的学生不会被计入，
 * 因此 {@code studentTotal} 是"口径内已登记查寝记录的去重学生数"。
 * 学生维度（学院 / 楼栋 / 班级）优先取 {@link Student} 上的冗余字段，
 * 学院缺失时用 {@link ClassInfo#getDepartment()} 按班级名补齐。</p>
 *
 * <p>权限：本服务不做鉴权，由控制器调用 {@code AccessGuard} 判定归属；
 * 需要限制"辅导员只看自己任务"时，使用带 {@code counselorId} 参数的重载，
 * 它会把口径收窄到仅分配给该辅导员的任务（未分配任务不计入）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryAggregator {

    private static final String UNKNOWN_COLLEGE = "未知学院";
    private static final String UNKNOWN_BUILDING = "未知楼栋";
    private static final String UNKNOWN_CLASS_NAME = "未知班级";

    private final TaskRepository taskRepository;
    private final CheckRecordRepository checkRecordRepository;
    private final ClassInfoRepository classInfoRepository;

    // ========================================================================
    // a) 单任务汇总（L1）
    // ========================================================================

    /**
     * 单任务汇总：按学生维度统计该任务的 {@link CheckRecord}。
     * <p>聚合项包含 college / building / className 三个维度（单任务只有一个日期，故不重复下发 date 维度）。</p>
     *
     * @throws BizException 任务不存在（{@link ErrorCode#TASK_NOT_FOUND}）或 taskId 为空
     */
    @Transactional(readOnly = true)
    public SummarySnapshot taskSummary(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "taskId 不能为空");
        }
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.TASK_NOT_FOUND));

        ClassDepartmentIndex departmentIndex = new ClassDepartmentIndex(classInfoRepository);
        Map<String, Row> latestPerStudent = new LinkedHashMap<>();
        for (CheckRecord record : safe(checkRecordRepository.findByTaskId(taskId))) {
            Row row = toRow(record, departmentIndex);
            if (row == null) {
                continue;
            }
            latestPerStudent.merge(row.studentKey(), row, SummaryAggregator::latest);
        }

        log.debug("单任务汇总: taskId={}, taskTitle={}, records={}",
                task.getId(), task.getTitle(), latestPerStudent.size());
        return build(SummarySnapshot.Scope.TASK, 1, latestPerStudent.values(), false);
    }

    // ========================================================================
    // b) 范围汇总（L2，总汇总主形态）
    // ========================================================================

    /**
     * 日期范围汇总：统计 {@code [from, to]} 内任务日期命中的任务记录，并按 date / college / building / className 聚合。
     * <p>任务维度筛选规则：</p>
     * <ul>
     *   <li>任务日期（缺失时回退创建日期）落在 {@code [from, to]} 内；</li>
     *   <li>已取消（{@code CANCELLED}）任务不计入；</li>
     *   <li>指定 {@code buildingName} 时按任务楼栋名匹配（无楼栋信息的任务不纳入）；</li>
     *   <li>指定 {@code college} 时在记录/学生维度过滤（任务本身不带学院信息）。</li>
     * </ul>
     * <p>{@code from} / {@code to} 为单边时可视为单日；两者均为空时取当天。</p>
     *
     * @throws BizException {@code from} 晚于 {@code to}
     */
    @Transactional(readOnly = true)
    public SummarySnapshot scopeSummary(LocalDate from, LocalDate to,
                                        String college, String buildingName) {
        return scopeSummary(from, to, college, buildingName, null);
    }

    /**
     * 日期范围汇总（可收窄到某个辅导员自己的任务）。
     *
     * @param counselorId 非空时只统计分配给该辅导员的任务；{@code null} 表示不限制（管理员口径）
     */
    @Transactional(readOnly = true)
    public SummarySnapshot scopeSummary(LocalDate from, LocalDate to,
                                        String college, String buildingName,
                                        String counselorId) {
        LocalDate start = from != null ? from : to;
        LocalDate end = to != null ? to : from;
        if (start == null || end == null) {
            LocalDate today = LocalDate.now();
            start = today;
            end = today;
        }
        if (start.isAfter(end)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "起始日期不能晚于结束日期");
        }

        final String buildingFilter = normalize(buildingName);
        List<Task> tasks = new ArrayList<>();
        for (Task task : findTasksBetween(start, end)) {
            if (task == null || task.getStatus() == TaskStatus.CANCELLED) {
                continue;
            }
            if (counselorId != null && !isOwnedBy(task, counselorId)) {
                continue;
            }
            if (buildingFilter != null && !buildingFilter.equalsIgnoreCase(buildingNameOf(task))) {
                continue;
            }
            tasks.add(task);
        }

        List<CheckRecord> records = recordsOfTasks(tasks);
        ClassDepartmentIndex departmentIndex = new ClassDepartmentIndex(classInfoRepository);

        // 同一学生同一天只计一次（同一天多个任务时取最新记录），避免重复计入应到
        Map<String, Row> latestPerStudentPerDate = new LinkedHashMap<>();
        for (Row row : rowsOf(records, departmentIndex, college)) {
            String key = row.studentKey() + "@" + (row.date() == null ? "unknown" : row.date());
            latestPerStudentPerDate.merge(key, row, SummaryAggregator::latest);
        }

        log.debug("范围汇总: {}~{} college={} building={} tasks={} students={}",
                start, end, college, buildingName, tasks.size(), latestPerStudentPerDate.size());
        return build(SummarySnapshot.Scope.SCOPE, tasks.size(),
                latestPerStudentPerDate.values(), true);
    }

    // ========================================================================
    // c) 全量汇总（L3）
    // ========================================================================

    /**
     * 全量汇总：所有已完成任务（与既有"全量报表"口径一致），按学生去重并取最新记录。
     * <p>最新记录 = 任务日期更晚者优先，同日再按记录提交时间更晚者优先；
     * 聚合项包含 college / building / className 三个维度。</p>
     */
    @Transactional(readOnly = true)
    public SummarySnapshot allTasksSummary() {
        return allTasksSummary(null);
    }

    /**
     * 全量汇总（可收窄到某个辅导员自己的任务）。
     *
     * @param counselorId 非空时只统计分配给该辅导员的任务；{@code null} 表示不限制（管理员口径）
     */
    @Transactional(readOnly = true)
    public SummarySnapshot allTasksSummary(String counselorId) {
        List<Task> completedTasks = safe(taskRepository.findCompletedTasks(TaskStatus.COMPLETED));
        List<Task> tasks = new ArrayList<>();
        for (Task task : completedTasks) {
            if (task == null) {
                continue;
            }
            if (counselorId != null && !isOwnedBy(task, counselorId)) {
                continue;
            }
            tasks.add(task);
        }

        ClassDepartmentIndex departmentIndex = new ClassDepartmentIndex(classInfoRepository);
        List<Row> rows = rowsOf(recordsOfTasks(tasks), departmentIndex, null);
        rows.sort(LATEST_FIRST);

        Map<String, Row> latestPerStudent = new LinkedHashMap<>();
        for (Row row : rows) {
            latestPerStudent.putIfAbsent(row.studentKey(), row);
        }

        log.debug("全量汇总: completedTasks={} countedTasks={} students={}",
                completedTasks.size(), tasks.size(), latestPerStudent.size());
        return build(SummarySnapshot.Scope.ALL, tasks.size(), latestPerStudent.values(), false);
    }

    // ========================================================================
    // 查询与行构建
    // ========================================================================

    private List<Task> findTasksBetween(LocalDate start, LocalDate end) {
        Page<Task> page = taskRepository.findByTaskDateBetween(start, end, Pageable.unpaged());
        return page == null ? List.of() : page.getContent();
    }

    private List<CheckRecord> recordsOfTasks(List<Task> tasks) {
        List<String> taskIds = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            if (task.getId() != null) {
                taskIds.add(task.getId());
            }
        }
        if (taskIds.isEmpty()) {
            return List.of();
        }
        return safe(checkRecordRepository.findByTaskIdIn(taskIds));
    }

    private List<Row> rowsOf(List<CheckRecord> records, ClassDepartmentIndex departmentIndex,
                             String collegeFilter) {
        String wantedCollege = normalize(collegeFilter);
        List<Row> rows = new ArrayList<>(records.size());
        for (CheckRecord record : records) {
            Row row = toRow(record, departmentIndex);
            if (row == null) {
                continue;
            }
            if (wantedCollege != null && !wantedCollege.equalsIgnoreCase(row.college())) {
                continue;
            }
            rows.add(row);
        }
        return rows;
    }

    private Row toRow(CheckRecord record, ClassDepartmentIndex departmentIndex) {
        if (record == null) {
            return null;
        }
        Student student = record.getStudent();
        return new Row(
                studentKey(record),
                taskDateOf(record.getTask()),
                recordTime(record),
                buildingOf(record, student),
                departmentIndex.departmentOf(student),
                classNameOf(student),
                effectiveStatus(record));
    }

    // ========================================================================
    // 聚合
    // ========================================================================

    private SummarySnapshot build(SummarySnapshot.Scope scope, int taskCount,
                                  Collection<Row> rows, boolean withDateDimension) {
        Tally total = new Tally();
        Map<String, Tally> byDate = new LinkedHashMap<>();
        Map<String, Tally> byCollege = new LinkedHashMap<>();
        Map<String, Tally> byBuilding = new LinkedHashMap<>();
        Map<String, Tally> byClassName = new LinkedHashMap<>();

        for (Row row : rows) {
            total.accept(row);
            if (withDateDimension && row.date() != null) {
                byDate.computeIfAbsent(row.date().toString(), key -> new Tally()).accept(row);
            }
            byCollege.computeIfAbsent(orUnknown(row.college(), UNKNOWN_COLLEGE),
                    key -> new Tally()).accept(row);
            byBuilding.computeIfAbsent(orUnknown(row.building(), UNKNOWN_BUILDING),
                    key -> new Tally()).accept(row);
            byClassName.computeIfAbsent(orUnknown(row.className(), UNKNOWN_CLASS_NAME),
                    key -> new Tally()).accept(row);
        }

        List<SummarySnapshot.Item> items = new ArrayList<>();
        addItems(items, SummarySnapshot.DIM_DATE, byDate);
        addItems(items, SummarySnapshot.DIM_COLLEGE, byCollege);
        addItems(items, SummarySnapshot.DIM_BUILDING, byBuilding);
        addItems(items, SummarySnapshot.DIM_CLASS_NAME, byClassName);

        return new SummarySnapshot(scope, LocalDateTime.now(), taskCount,
                total.students.size(), total.should, total.actual, total.leave, total.absent,
                rate(total.actual, total.should), items);
    }

    private static void addItems(List<SummarySnapshot.Item> target, String dimension,
                                 Map<String, Tally> groups) {
        groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Tally tally = entry.getValue();
                    target.add(new SummarySnapshot.Item(dimension, entry.getKey(),
                            tally.students.size(), tally.should, tally.actual,
                            tally.leave, tally.absent,
                            rate(tally.actual, tally.should)));
                });
    }

    /** 人均应到 / 实到 / 请假 / 旷宿计数，并按学生去重。 */
    private static final class Tally {
        private final Set<String> students = new HashSet<>();
        private int should;
        private int actual;
        private int leave;
        private int absent;

        private void accept(Row row) {
            students.add(row.studentKey());
            CheckStatus status = row.status() != null ? row.status() : CheckStatus.UNKNOWN;
            if (status != CheckStatus.DAY_STUDENT && status != CheckStatus.LONG_SICK_LEAVE) {
                should++;
            }
            switch (status) {
                case PRESENT -> actual++;
                case PERSONAL_LEAVE, SICK_LEAVE -> leave++;
                case ABSENT -> absent++;
                default -> {
                    // 征兵 / 实习 / 晚归 / 未知：只计入应到，不计入实到、请假、旷宿
                }
            }
        }
    }

    // ========================================================================
    // 学生维度解析
    // ========================================================================

    /**
     * 班级名 → 学院（department）索引，按需加载，避免每次汇总都全量查询班级表。
     */
    private static final class ClassDepartmentIndex {
        private final ClassInfoRepository classInfoRepository;
        private Map<String, String> departmentByClassName;

        private ClassDepartmentIndex(ClassInfoRepository classInfoRepository) {
            this.classInfoRepository = classInfoRepository;
        }

        private String departmentOf(Student student) {
            if (student == null) {
                return null;
            }
            ClassInfo classInfo = student.getClassInfo();
            if (classInfo != null && notBlank(classInfo.getDepartment())) {
                return classInfo.getDepartment().trim();
            }
            String className = classNameOf(student);
            if (className == null) {
                return null;
            }
            if (departmentByClassName == null) {
                departmentByClassName = loadDepartmentByClassName();
            }
            return departmentByClassName.get(className);
        }

        private Map<String, String> loadDepartmentByClassName() {
            Map<String, String> map = new HashMap<>();
            List<ClassInfo> classes = classInfoRepository.findAll();
            if (classes == null) {
                return map;
            }
            for (ClassInfo classInfo : classes) {
                if (classInfo != null && notBlank(classInfo.getName())
                        && notBlank(classInfo.getDepartment())) {
                    map.putIfAbsent(classInfo.getName().trim(), classInfo.getDepartment().trim());
                }
            }
            return map;
        }
    }

    // ========================================================================
    // 记录行与排序
    // ========================================================================

    private record Row(String studentKey,
                       LocalDate date,
                       LocalDateTime at,
                       String building,
                       String college,
                       String className,
                       CheckStatus status) {
    }

    private static final Comparator<Row> LATEST_FIRST = (a, b) -> {
        int byDate = compareNullsLow(a.date(), b.date());
        if (byDate != 0) {
            return -byDate;
        }
        return -compareNullsLow(a.at(), b.at());
    };

    private static Row latest(Row left, Row right) {
        return LATEST_FIRST.compare(left, right) <= 0 ? left : right;
    }

    private static <T extends Comparable<T>> int compareNullsLow(T left, T right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    // ========================================================================
    // 字段解析
    // ========================================================================

    private static CheckStatus effectiveStatus(CheckRecord record) {
        if (record.getCheckStatus() != null) {
            return record.getCheckStatus();
        }
        if (record.getPresetStatus() != null) {
            return record.getPresetStatus();
        }
        return CheckStatus.UNKNOWN;
    }

    private static String studentKey(CheckRecord record) {
        Student student = record.getStudent();
        if (student != null) {
            if (notBlank(student.getId())) {
                return student.getId();
            }
            if (notBlank(student.getStudentNo())) {
                return "no:" + student.getStudentNo();
            }
        }
        if (notBlank(record.getStudentNo())) {
            return "no:" + record.getStudentNo();
        }
        if (notBlank(record.getId())) {
            return "record:" + record.getId();
        }
        return "record:" + System.identityHashCode(record);
    }

    private static LocalDate taskDateOf(Task task) {
        if (task == null) {
            return null;
        }
        if (task.getTaskDate() != null) {
            return task.getTaskDate();
        }
        return task.getCreatedAt() != null ? task.getCreatedAt().toLocalDate() : null;
    }

    private static LocalDateTime recordTime(CheckRecord record) {
        if (record.getSubmittedAt() != null) {
            return record.getSubmittedAt();
        }
        if (record.getUpdatedAt() != null) {
            return record.getUpdatedAt();
        }
        return record.getCreatedAt();
    }

    private static String classNameOf(Student student) {
        if (student == null) {
            return null;
        }
        if (notBlank(student.getClassName())) {
            return student.getClassName().trim();
        }
        ClassInfo classInfo = student.getClassInfo();
        if (classInfo != null && notBlank(classInfo.getName())) {
            return classInfo.getName().trim();
        }
        return null;
    }

    private static String buildingOf(CheckRecord record, Student student) {
        if (student != null) {
            if (notBlank(student.getBuildingName())) {
                return student.getBuildingName().trim();
            }
            DormBuilding building = student.getBuilding();
            if (building != null && notBlank(building.getName())) {
                return building.getName().trim();
            }
        }
        Room room = record.getRoom();
        if (room != null) {
            DormBuilding building = room.getBuilding();
            if (building != null && notBlank(building.getName())) {
                return building.getName().trim();
            }
        }
        return null;
    }

    private static String buildingNameOf(Task task) {
        if (task == null) {
            return null;
        }
        if (notBlank(task.getBuildingName())) {
            return task.getBuildingName().trim();
        }
        DormBuilding building = task.getBuilding();
        return building != null && notBlank(building.getName()) ? building.getName().trim() : null;
    }

    private static boolean isOwnedBy(Task task, String counselorId) {
        return task.getAssignedCounselor() != null
                && counselorId.equals(task.getAssignedCounselor().getId());
    }

    private static double rate(int actual, int should) {
        if (should <= 0) {
            return 0.0;
        }
        return Math.round(actual * 10000.0 / should) / 100.0;
    }

    private static String orUnknown(String value, String fallback) {
        return notBlank(value) ? value : fallback;
    }

    private static String normalize(String value) {
        return notBlank(value) ? value.trim() : null;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
