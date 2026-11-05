package net.alan.jupiter.application.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.enums.TaskStatus;
import net.alan.jupiter.domain.model.*;
import net.alan.jupiter.domain.repository.*;
import net.alan.jupiter.domain.service.CheckRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportQueryService {

    private final CheckRecordRepository checkRecordRepository;
    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;
    private final StudentRepository studentRepository;
    private final DormBuildingRepository buildingRepository;
    private final TaskRepository taskRepository;
    private final ClassInfoRepository classInfoRepository;
    private final CheckRuleService checkRuleService;

    // ========================================================================
    // 对外入口：单任务
    // ========================================================================

    @Transactional(readOnly = true)
    public Object generateReport(ReportType type, String taskId, String targetId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.TASK_NOT_FOUND));

        Set<String> taskRoomNumbers = parseRoomNumbers(task.getRoomNumbers());
        Map<String, CheckRecord> recordMap = buildRecordMap(
                checkRecordRepository.findByTaskId(taskId));

        String date = resolveSingleTaskDate(task);

        return switch (type) {
            case BUILDING -> generateBuildingReportInternal(
                    task.getBuilding(), taskRoomNumbers, recordMap, date);
            case COLLEGE  -> generateCollegeReportInternal(
                    taskRoomNumbers, recordMap, date, targetId);
            case CLASS    -> generateClassReportInternal(
                    targetId, recordMap, date);
        };
    }

    // ========================================================================
    // 对外入口：全量（所有已完成任务）
    // ========================================================================

    @Transactional(readOnly = true)
    public Object generateAggregatedReport(ReportType type, String targetId) {
        List<Task> completedTasks = taskRepository.findCompletedTasks(TaskStatus.COMPLETED);
        if (completedTasks.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "没有已完成的查寝任务");
        }

        Map<String, CheckRecord> mergedRecordMap = loadAndMergeRecords(completedTasks);
        String dateRange = buildDateRange(completedTasks);

        return generateAggregatedReport(type, targetId, mergedRecordMap, dateRange);
    }

    /**
     * 使用预计算的 mergedRecordMap，避免全量导出时每个楼栋/班级重复加载全表。
     * 调用方先在 exportBundle 入口调用 {@link #prepareExportContext()}，
     * 然后传入此方法。
     */
    public Object generateAggregatedReport(ReportType type, String targetId,
                                            Map<String, CheckRecord> mergedRecordMap,
                                            String dateRange) {
        return switch (type) {
            case BUILDING -> {
                DormBuilding building = buildingRepository.findById(targetId)
                        .orElseThrow(() -> new BizException(ErrorCode.BUILDING_NOT_FOUND));
                yield generateBuildingReportInternal(
                        building, null, mergedRecordMap, dateRange);
            }
            case COLLEGE -> generateCollegeReportInternal(
                    null, mergedRecordMap, dateRange, targetId);
            case CLASS   -> generateClassReportInternal(
                    targetId, mergedRecordMap, dateRange);
        };
    }

    /**
     * 为全量导出准备共享上下文：一次加载所有已完成任务的查寝记录并合并。
     */
    public ExportContext prepareExportContext() {
        List<Task> completedTasks = taskRepository.findCompletedTasks(TaskStatus.COMPLETED);
        if (completedTasks.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "没有已完成的查寝任务");
        }
        Map<String, CheckRecord> mergedRecordMap = loadAndMergeRecords(completedTasks);
        String dateRange = buildDateRange(completedTasks);
        return new ExportContext(mergedRecordMap, dateRange);
    }

    public record ExportContext(Map<String, CheckRecord> mergedRecordMap, String dateRange) {}


    // ========================================================================
    // 楼栋报表
    // ========================================================================

    private BuildingReportData generateBuildingReportInternal(
            DormBuilding building,
            Set<String> taskRoomNumbers,
            Map<String, CheckRecord> recordMap,
            String date) {

        List<Floor> floors = floorRepository.findByBuildingIdOrderByFloorNumber(building.getId());
        List<Room> rooms = filterRooms(
                roomRepository.findByBuildingId(building.getId()), taskRoomNumbers);

        Map<String, List<Room>> roomsByFloor = rooms.stream()
                .filter(r -> r.getFloor() != null)
                .collect(Collectors.groupingBy(r -> r.getFloor().getId()));

        Map<String, List<Student>> studentsByRoom = loadStudentsByRoom(rooms);

        List<BuildingReportData.FloorSummary> floorSummaries = new ArrayList<>();
        List<BuildingReportData.RoomDetail> roomDetails = new ArrayList<>();

        for (Floor floor : floors) {
            List<Room> floorRooms = roomsByFloor.getOrDefault(floor.getId(), List.of());
            FloorStats stats = new FloorStats();

            for (Room room : floorRooms) {
                for (Student student : studentsByRoom.getOrDefault(room.getId(), List.of())) {
                    if (!isActive(student)) continue;

                    CheckStatus status = resolveStatus(student, recordMap);
                    stats.accept(status);

                    roomDetails.add(buildRoomDetail(floor, room, student, status, recordMap));
                }
            }

            floorSummaries.add(stats.toFloorSummary(String.valueOf(floor.getFloorNumber())));
        }

        BuildingReportData data = new BuildingReportData();
        data.setDate(date);
        data.setFloorSummaries(floorSummaries);
        data.setRoomDetails(roomDetails);
        return data;
    }

    private BuildingReportData.RoomDetail buildRoomDetail(
            Floor floor, Room room, Student student,
            CheckStatus status, Map<String, CheckRecord> recordMap) {
        BuildingReportData.RoomDetail rd = new BuildingReportData.RoomDetail();
        rd.setFloor(String.valueOf(floor.getFloorNumber()));
        rd.setRoomNumber(room.getRoomNumber());
        rd.setStudentNo(student.getStudentNo());
        rd.setStudentName(student.getName());
        rd.setClassName(student.getClassName());
        rd.setCheckStatus(status.getLabel());
        rd.setLeaveType(leaveType(status));
        rd.setProofFile(getProofFile(recordMap, student));
        rd.setRemark(getRemark(recordMap, student));
        rd.setStudentRemark(student.getRemark() != null ? student.getRemark() : "");
        return rd;
    }

    // ========================================================================
    // 学院报表
    // ========================================================================

    private CollegeReportData generateCollegeReportInternal(
            Set<String> taskRoomNumbers,
            Map<String, CheckRecord> recordMap,
            String date,
            String collegeName) {

        List<DormBuilding> buildings = buildingRepository.findByIsActiveTrue();

        // 按学院名称过滤：加载对应学院所有班级，匹配学生的 className 或 classInfo FK
        java.util.Set<String> collegeClassNames = null;
        java.util.Set<String> collegeClassStudentIds = null;
        if (collegeName != null && !collegeName.isBlank()) {
            List<ClassInfo> allClasses = classInfoRepository.findAll();
            collegeClassNames = allClasses.stream()
                    .filter(c -> collegeName.equals(c.getDepartment()))
                    .map(ClassInfo::getName)
                    .collect(Collectors.toSet());
            List<String> collegeClassIds = allClasses.stream()
                    .filter(c -> collegeName.equals(c.getDepartment()))
                    .map(ClassInfo::getId)
                    .toList();
            collegeClassStudentIds = collegeClassIds.isEmpty()
                    ? java.util.Set.of()
                    : studentRepository.findByClassInfoIdIn(collegeClassIds)
                            .stream()
                            .filter(this::isActive)
                            .map(Student::getId)
                            .collect(Collectors.toSet());
        }

        List<CollegeReportData.BuildingSummary> buildingSummaries = new ArrayList<>();
        List<FloorDetail> floorDetails = new ArrayList<>();

        for (DormBuilding building : buildings) {
            List<Student> buildingStudents = filterStudentsByRooms(
                    studentRepository.findByBuildingId(building.getId()), taskRoomNumbers);

            // 按学院名称过滤
            if (collegeClassNames != null && collegeClassStudentIds != null) {
                final java.util.Set<String> names = collegeClassNames;
                final java.util.Set<String> ids = collegeClassStudentIds;
                buildingStudents = buildingStudents.stream()
                        .filter(s -> names.contains(s.getClassName())
                                || ids.contains(s.getId()))
                        .toList();
            }

            // ===== 楼栋汇总 =====
            BuildingStats bStats = new BuildingStats();
            for (Student s : buildingStudents) {
                if (!isActive(s)) continue;
                bStats.accept(resolveStatus(s, recordMap));
            }
            buildingSummaries.add(bStats.toBuildingSummary(building.getName()));

            // ===== 楼层明细 =====
            Map<Integer, List<Student>> studentsByFloor = buildingStudents.stream()
                    .filter(s -> s.getFloorNumber() != null)
                    .collect(Collectors.groupingBy(Student::getFloorNumber));

            for (Floor floor : floorRepository.findByBuildingIdOrderByFloorNumber(building.getId())) {
                List<Student> floorStudents = studentsByFloor.getOrDefault(
                        floor.getFloorNumber(), List.of());

                FloorStats fStats = new FloorStats();
                for (Student s : floorStudents) {
                    if (!isActive(s)) continue;
                    fStats.accept(resolveStatus(s, recordMap));
                }

                FloorDetail fd = new FloorDetail();
                fd.setBuildingName(building.getName());
                fd.setFloor(String.valueOf(floor.getFloorNumber()));
                fd.setShould(fStats.should);
                fd.setActual(fStats.actual);
                fd.setPersonalLeave(fStats.personalLeave);
                fd.setSickLeave(fStats.sickLeave);
                fd.setLateReturn(fStats.lateReturn);
                fd.setMilitary(fStats.military);
                fd.setIntern(fStats.intern);
                fd.setAbsent(fStats.absent);
                fd.setUnchecked(fStats.unchecked);
                fd.setAbsentNames(fStats.absentNames.isEmpty()
                        ? "" : String.join("、", fStats.absentNames));
                floorDetails.add(fd);
            }
        }

        CollegeReportData data = new CollegeReportData();
        data.setDate(date);
        data.setCollegeName(collegeName);
        data.setBuildingSummaries(buildingSummaries);
        data.setFloorDetails(floorDetails);
        return data;
    }

    // ========================================================================
    // 班级报表
    // ========================================================================

    private ClassReportData generateClassReportInternal(
            String classId,
            Map<String, CheckRecord> recordMap,
            String date) {

        List<Student> students = studentRepository.findByClassInfoId(classId);

        List<ClassReportData.StudentRow> rows = new ArrayList<>();
        int present = 0, personalLeave = 0, sickLeave = 0, lateReturn = 0,
                absent = 0, military = 0, intern = 0, dayStudent = 0,
                longSickLeave = 0, unchecked = 0, total = 0;

        for (Student student : students) {
            if (!isActive(student)) continue;

            CheckStatus status = resolveStatus(student, recordMap);
            total++;

            ClassReportData.StudentRow row = new ClassReportData.StudentRow();
            row.setStudentNo(student.getStudentNo());
            row.setStudentName(student.getName());
            row.setGender(student.getGender());
            row.setBuildingName(student.getBuildingName());
            row.setFloorNumber(student.getFloorNumber());
            row.setRoomNumber(student.getRoomNumber());
            row.setCheckStatus(status.getLabel());
            row.setLeaveType(leaveType(status));
            row.setProofFilePath(getProofFile(recordMap, student));
            row.setRemark(getRemark(recordMap, student));
            row.setStudentRemark(student.getRemark() != null ? student.getRemark() : "");
            rows.add(row);

            switch (status) {
                case PRESENT -> present++;
                case PERSONAL_LEAVE -> personalLeave++;
                case SICK_LEAVE -> sickLeave++;
                case LATE_RETURN -> lateReturn++;
                case ABSENT -> absent++;
                case MILITARY -> military++;
                case INTERN -> intern++;
                case DAY_STUDENT -> dayStudent++;
                case LONG_SICK_LEAVE -> longSickLeave++;
                case UNKNOWN -> unchecked++;
            }
        }

        ClassReportData data = new ClassReportData();
        data.setDate(date);
        data.setRows(rows);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("present", present);
        summary.put("personalLeave", personalLeave);
        summary.put("sickLeave", sickLeave);
        summary.put("lateReturn", lateReturn);
        summary.put("absent", absent);
        summary.put("military", military);
        summary.put("intern", intern);
        summary.put("dayStudent", dayStudent);
        summary.put("longSickLeave", longSickLeave);
        summary.put("unchecked", unchecked);
        data.setSummary(summary);
        return data;
    }

    // ========================================================================
    // 聚合：一次加载 + 合并
    // ========================================================================

    /**
     * 加载所有已完成任务的查寝记录，按学生合并（每个学生只保留最新一次）。
     * 只在全量入口调用一次，后续所有报表共享这个 Map。
     */
    private Map<String, CheckRecord> loadAndMergeRecords(List<Task> tasks) {
        List<String> taskIds = tasks.stream().map(Task::getId).toList();
        List<CheckRecord> allRecords = checkRecordRepository.findByTaskIdIn(taskIds);

        Map<String, LocalDate> taskDateMap = tasks.stream()
                .collect(Collectors.toMap(
                        Task::getId,
                        t -> t.getTaskDate() != null ? t.getTaskDate() : LocalDate.MIN,
                        (a, b) -> a));

        // 按任务日期降序，让 toMap 时第一次遇到的即最新
        allRecords.sort((a, b) -> {
            LocalDate da = taskDateMap.getOrDefault(
                    a.getTask() != null ? a.getTask().getId() : "", LocalDate.MIN);
            LocalDate db = taskDateMap.getOrDefault(
                    b.getTask() != null ? b.getTask().getId() : "", LocalDate.MIN);
            return db.compareTo(da);
        });

        Map<String, CheckRecord> merged = new LinkedHashMap<>();
        for (CheckRecord r : allRecords) {
            if (r.getStudent() == null) continue;
            merged.putIfAbsent(r.getStudent().getId(), r);
        }
        return merged;
    }

    private Map<String, CheckRecord> buildRecordMap(List<CheckRecord> records) {
        return records.stream()
                .filter(r -> r.getStudent() != null)
                .collect(Collectors.toMap(
                        r -> r.getStudent().getId(),
                        r -> r,
                        (a, b) -> a));
    }

    // ========================================================================
    // 状态解析
    // ========================================================================

    private CheckStatus resolveStatus(Student student, Map<String, CheckRecord> recordMap) {
        CheckRecord record = recordMap.get(student.getId());
        if (record != null && record.getCheckStatus() != null) {
            return record.getCheckStatus();
        }
        CheckStatus preset = checkRuleService.determinePresetStatus(student);
        return preset != null ? preset : CheckStatus.UNKNOWN;
    }

    private String leaveType(CheckStatus status) {
        return switch (status) {
            case PERSONAL_LEAVE -> "事假";
            case SICK_LEAVE -> "病假";
            case LONG_SICK_LEAVE -> "长期病假";
            default -> "";
        };
    }

    private String getRemark(Map<String, CheckRecord> recordMap, Student student) {
        CheckRecord r = recordMap.get(student.getId());
        return r != null && r.getRemark() != null ? r.getRemark() : "";
    }

    private String getProofFile(Map<String, CheckRecord> recordMap, Student student) {
        CheckRecord r = recordMap.get(student.getId());
        return r != null ? r.getProofFile() : null;
    }

    // ========================================================================
    // 过滤 / 日期
    // ========================================================================

    private boolean isActive(Student s) {
        return s.getIsActive() == null || s.getIsActive();
    }

    private Set<String> parseRoomNumbers(String roomNumbers) {
        if (roomNumbers == null || roomNumbers.isBlank()) return null;
        return Arrays.stream(roomNumbers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private List<Room> filterRooms(List<Room> rooms, Set<String> taskRoomNumbers) {
        if (taskRoomNumbers == null) return rooms;
        return rooms.stream()
                .filter(r -> taskRoomNumbers.contains(r.getRoomNumber()))
                .toList();
    }

    private List<Student> filterStudentsByRooms(List<Student> students, Set<String> taskRoomNumbers) {
        if (taskRoomNumbers == null) return students;
        return students.stream()
                .filter(s -> s.getRoomNumber() != null
                        && taskRoomNumbers.contains(s.getRoomNumber()))
                .toList();
    }

    private Map<String, List<Student>> loadStudentsByRoom(List<Room> rooms) {
        if (rooms.isEmpty()) return Map.of();
        List<String> roomIds = rooms.stream().map(Room::getId).toList();
        return studentRepository.findByRoomIdInOrderByBedNumber(roomIds).stream()
                .filter(s -> s.getRoom() != null)
                .collect(Collectors.groupingBy(s -> s.getRoom().getId()));
    }

    private String resolveSingleTaskDate(Task task) {
        if (task.getTaskDate() != null) return task.getTaskDate().toString();
        if (task.getCreatedAt() != null) return task.getCreatedAt().toLocalDate().toString();
        return LocalDate.now().toString();
    }

    private String buildDateRange(List<Task> tasks) {
        List<LocalDate> dates = tasks.stream()
                .map(Task::getTaskDate)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        if (dates.isEmpty()) return LocalDate.now().toString();
        if (dates.size() == 1) return dates.get(0).toString();
        return dates.get(0) + " ~ " + dates.get(dates.size() - 1);
    }

    // ========================================================================
    // 内部统计器
    // ========================================================================

    private static class FloorStats {
        int should, actual, personalLeave, sickLeave, lateReturn,
                military, intern, dayStudent, longSickLeave, absent, unchecked;
        List<String> absentNames = new ArrayList<>();

        void accept(CheckStatus status) {
            if (status != CheckStatus.DAY_STUDENT && status != CheckStatus.LONG_SICK_LEAVE) {
                should++;
            }
            switch (status) {
                case PRESENT -> actual++;
                case PERSONAL_LEAVE -> personalLeave++;
                case SICK_LEAVE -> sickLeave++;
                case LATE_RETURN -> lateReturn++;
                case MILITARY -> military++;
                case INTERN -> intern++;
                case DAY_STUDENT -> dayStudent++;
                case LONG_SICK_LEAVE -> longSickLeave++;
                case ABSENT -> absent++;
                case UNKNOWN -> unchecked++;
            }
        }

        BuildingReportData.FloorSummary toFloorSummary(String floorLabel) {
            BuildingReportData.FloorSummary fs = new BuildingReportData.FloorSummary();
            fs.setFloor(floorLabel);
            fs.setShould(should);
            fs.setActual(actual);
            fs.setPersonalLeave(personalLeave);
            fs.setSickLeave(sickLeave);
            fs.setLateReturn(lateReturn);
            fs.setMilitary(military);
            fs.setIntern(intern);
            fs.setAbsent(absent);
            fs.setUnchecked(unchecked);
            fs.setAbsentNames(absentNames.isEmpty() ? "" : String.join("、", absentNames));
            return fs;
        }
    }

    private static class BuildingStats {
        int should, actual, personalLeave, sickLeave, lateReturn,
                military, intern, dayStudent, longSickLeave, absent, unchecked;
        List<String> absentNames = new ArrayList<>();

        void accept(CheckStatus status) {
            if (status != CheckStatus.DAY_STUDENT && status != CheckStatus.LONG_SICK_LEAVE) {
                should++;
            }
            switch (status) {
                case PRESENT -> actual++;
                case PERSONAL_LEAVE -> personalLeave++;
                case SICK_LEAVE -> sickLeave++;
                case LATE_RETURN -> lateReturn++;
                case MILITARY -> military++;
                case INTERN -> intern++;
                case DAY_STUDENT -> dayStudent++;
                case LONG_SICK_LEAVE -> longSickLeave++;
                case ABSENT -> absent++;
                case UNKNOWN -> unchecked++;
            }
        }

        CollegeReportData.BuildingSummary toBuildingSummary(String buildingName) {
            CollegeReportData.BuildingSummary bs = new CollegeReportData.BuildingSummary();
            bs.setBuildingName(buildingName);
            bs.setShould(should);
            bs.setActual(actual);
            bs.setPersonalLeave(personalLeave);
            bs.setSickLeave(sickLeave);
            bs.setLateReturn(lateReturn);
            bs.setMilitary(military);
            bs.setIntern(intern);
            bs.setDayStudent(dayStudent);
            bs.setLongSickLeave(longSickLeave);
            bs.setAbsent(absent);
            bs.setUnchecked(unchecked);
            bs.setAbsentNames(absentNames.isEmpty() ? "" : String.join("、", absentNames));
            return bs;
        }
    }

    // ========================================================================
    // 枚举与 DTO
    // ========================================================================

    public enum ReportType {
        BUILDING, COLLEGE, CLASS
    }

    @lombok.Data
    public static class BuildingReportData {
        private String date;
        private List<FloorSummary> floorSummaries;
        private List<RoomDetail> roomDetails;

        @lombok.Data
        public static class FloorSummary {
            private String floor;
            private int should, actual, personalLeave, sickLeave, lateReturn,
                    military, intern, absent, unchecked;
            private String absentNames;
        }

        @lombok.Data
        public static class RoomDetail {
            private String floor, roomNumber, studentNo, studentName, className,
                    checkStatus, leaveType, proofFile, remark, studentRemark;
        }
    }

    @lombok.Data
    public static class CollegeReportData {
        private String date;
        private String collegeName;
        private List<BuildingSummary> buildingSummaries;
        private List<FloorDetail> floorDetails;

        @lombok.Data
        public static class BuildingSummary {
            private String buildingName;
            private int should, actual, personalLeave, sickLeave, lateReturn,
                    military, intern, dayStudent, longSickLeave, absent, unchecked;
            private String absentNames;
        }
    }

    @lombok.Data
    public static class FloorDetail {
        private String buildingName, floor;
        private int should, actual, personalLeave, sickLeave, lateReturn,
                military, intern, absent, unchecked;
        private String absentNames;
    }

    // ========================================================================
    // 晚查寝数据汇总（学院 → 楼栋 → 楼层 三段式）
    // ========================================================================

    public NightCheckSummaryData buildNightCheckSummary(String collegeName,
                                                         Map<String, CheckRecord> recordMap,
                                                         String date) {
        List<DormBuilding> buildings = buildingRepository.findByIsActiveTrue();

        NightCheckSummaryData summary = new NightCheckSummaryData();
        summary.setCollegeName(collegeName);
        summary.setDate(date);

        // 预加载所有 ClassInfo
        List<ClassInfo> allClasses = classInfoRepository.findAll();

        // 查找指定学院下的所有班级（名称 + ID，支持字符串和 FK 两种关联）
        Set<String> collegeClassNames = allClasses.stream()
                .filter(c -> collegeName.equals(c.getDepartment()))
                .map(ClassInfo::getName)
                .collect(Collectors.toSet());

        Set<String> collegeClassIds = allClasses.stream()
                .filter(c -> collegeName.equals(c.getDepartment()))
                .map(ClassInfo::getId)
                .collect(Collectors.toSet());

        // 兜底：通过 FK 关联加载学生（当 className 未正确填充时）
        Set<String> classLinkedStudentIds = collegeClassIds.isEmpty()
                ? Set.of()
                : studentRepository.findByClassInfoIdIn(new ArrayList<>(collegeClassIds))
                        .stream()
                        .filter(this::isActive)
                        .map(Student::getId)
                        .collect(Collectors.toSet());

        List<NightCheckSummaryData.BuildingRow> buildingRows = new ArrayList<>();
        List<NightCheckSummaryData.FloorRow> floorRows = new ArrayList<>();

        int collegeTotalShould = 0;
        int collegeTotalActual = 0;
        int collegeTotalPersonalLeave = 0;
        int collegeTotalSickLeave = 0;
        int collegeTotalDayStudent = 0;
        int collegeTotalLongSickLeave = 0;
        int collegeTotalMilitary = 0;
        int collegeTotalAbsent = 0;
        int collegeTotalUnchecked = 0;
        int collegeTotalLateReturn = 0;
        int collegeTotalIntern = 0;

        for (DormBuilding building : buildings) {
            List<Student> buildingStudents = studentRepository.findByBuildingId(building.getId())
                    .stream()
                    .filter(this::isActive)
                    .filter(s -> collegeClassNames.contains(s.getClassName())
                            || classLinkedStudentIds.contains(s.getId()))
                    .toList();

            if (buildingStudents.isEmpty()) continue;

            // 楼栋级统计：走读/长期病假不计入应到
            int bShould = 0, bActual = 0, bPersonalLeave = 0, bSickLeave = 0;
            int bMilitary = 0, bDayStudent = 0, bAbsent = 0, bLongSickLeave = 0, bUnchecked = 0;
            int bLateReturn = 0, bIntern = 0;
            List<String> bAbsentNames = new ArrayList<>();

            // 楼层级统计
            Map<Integer, FloorStats> floorStatsMap = new LinkedHashMap<>();

            for (Student s : buildingStudents) {
                CheckStatus status = resolveStatus(s, recordMap);
                boolean isDayOrLongSick = status == CheckStatus.DAY_STUDENT
                        || status == CheckStatus.LONG_SICK_LEAVE;

                // 学院总体统计：应到与楼栋/楼层口径一致（走读、长期病假不计入应到）
                boolean isDayOrLongSickForTotal = status == CheckStatus.DAY_STUDENT
                        || status == CheckStatus.LONG_SICK_LEAVE;
                if (!isDayOrLongSickForTotal) {
                    collegeTotalShould++;
                }
                switch (status) {
                    case PRESENT -> collegeTotalActual++;
                    case PERSONAL_LEAVE -> collegeTotalPersonalLeave++;
                    case SICK_LEAVE -> collegeTotalSickLeave++;
                    case DAY_STUDENT -> collegeTotalDayStudent++;
                    case LONG_SICK_LEAVE -> collegeTotalLongSickLeave++;
                    case MILITARY -> collegeTotalMilitary++;
                    case ABSENT -> collegeTotalAbsent++;
                    case UNKNOWN -> collegeTotalUnchecked++;
                    case LATE_RETURN -> collegeTotalLateReturn++;
                    case INTERN -> collegeTotalIntern++;
                }

                // 楼栋统计：走读/长期病假不参与
                if (isDayOrLongSick) {
                    if (status == CheckStatus.DAY_STUDENT) bDayStudent++;
                    else bLongSickLeave++;
                } else {
                    bShould++;
                    switch (status) {
                        case PRESENT -> bActual++;
                        case PERSONAL_LEAVE -> bPersonalLeave++;
                        case SICK_LEAVE -> bSickLeave++;
                        case MILITARY -> bMilitary++;
                        case ABSENT -> {
                            bAbsent++;
                            if (s.getName() != null) bAbsentNames.add(s.getName());
                        }
                        case UNKNOWN -> bUnchecked++;
                        case LATE_RETURN -> bLateReturn++;
                        case INTERN -> bIntern++;
                    }
                }

                // 楼层统计
                if (s.getFloorNumber() != null) {
                    FloorStats fs = floorStatsMap.computeIfAbsent(
                            s.getFloorNumber(), k -> new FloorStats());
                    if (!isDayOrLongSick) {
                        fs.should++;
                        switch (status) {
                            case PRESENT -> fs.actual++;
                            case PERSONAL_LEAVE -> fs.personalLeave++;
                            case SICK_LEAVE -> fs.sickLeave++;
                            case MILITARY -> fs.military++;
                            case ABSENT -> fs.absent++;
                            case LATE_RETURN -> {} // 已计入 should，不计入具体类目
                            case INTERN -> {}      // 已计入 should，不计入具体类目
                        }
                    }
                }
            }

            buildingRows.add(NightCheckSummaryData.BuildingRow.builder()
                    .buildingName(building.getName())
                    .shouldAmount(bShould)
                    .actualAmount(bActual)
                    .personalLeave(bPersonalLeave)
                    .sickLeave(bSickLeave)
                    .military(bMilitary)
                    .dayStudent(bDayStudent)
                    .absent(bAbsent)
                    .longSickLeave(bLongSickLeave)
                    .lateReturn(bLateReturn)
                    .intern(bIntern)
                    .unchecked(bUnchecked)
                    .absentNames(bAbsentNames.isEmpty() ? ""
                            : String.join("、", bAbsentNames))
                    .build());

            for (Map.Entry<Integer, FloorStats> entry : floorStatsMap.entrySet()) {
                FloorStats fs = entry.getValue();
                floorRows.add(NightCheckSummaryData.FloorRow.builder()
                        .buildingName(building.getName())
                        .floorLabel(entry.getKey() + "层")
                        .shouldAmount(fs.should)
                        .actualAmount(fs.actual)
                        .personalLeave(fs.personalLeave)
                        .sickLeave(fs.sickLeave)
                        .military(fs.military)
                        .absent(fs.absent)
                        .build());
            }
        }

        summary.setTotalShould(collegeTotalShould);
        summary.setTotalActual(collegeTotalActual);
        summary.setTotalPersonalLeave(collegeTotalPersonalLeave);
        summary.setTotalSickLeave(collegeTotalSickLeave);
        summary.setTotalDayStudent(collegeTotalDayStudent);
        summary.setTotalLongSickLeave(collegeTotalLongSickLeave);
        summary.setTotalMilitary(collegeTotalMilitary);
        summary.setTotalAbsent(collegeTotalAbsent);
        summary.setTotalUnchecked(collegeTotalUnchecked);
        summary.setTotalLateReturn(collegeTotalLateReturn);
        summary.setTotalIntern(collegeTotalIntern);
        summary.setBuildingRows(buildingRows);
        summary.setFloorRows(floorRows);
        return summary;
    }

    // ========================================================================
    // 请假明细（班级维度）
    // ========================================================================

    private static final Map<CheckStatus, Integer> DETAIL_ORDER = Map.of(
            CheckStatus.DAY_STUDENT, 1,
            CheckStatus.LONG_SICK_LEAVE, 2,
            CheckStatus.PERSONAL_LEAVE, 3,
            CheckStatus.SICK_LEAVE, 4,
            CheckStatus.MILITARY, 5,
            CheckStatus.INTERN, 6
    );

    public LeaveDetailData buildLeaveDetail(String collegeName,
                                             Map<String, CheckRecord> recordMap,
                                             String date) {
        List<ClassInfo> classes = classInfoRepository.findAll().stream()
                .filter(c -> collegeName.equals(c.getDepartment()))
                .toList();

        LeaveDetailData data = new LeaveDetailData();
        data.setDate(date);
        data.setCollegeName(collegeName);

        var rows = new java.util.ArrayList<LeaveDetailData.ClassLeaveRow>();
        int totalEnrolled = 0, totalActual = 0, totalLeave = 0;
        int totalMilitary = 0, totalDayStudent = 0, totalAbsent = 0;
        int totalPersonalLeave = 0, totalSickLeave = 0, totalLongSickLeave = 0;
        int totalIntern = 0, totalLateReturn = 0;
        int totalMale = 0, totalFemale = 0, actualMale = 0, actualFemale = 0;

        for (ClassInfo classInfo : classes) {
            // 优先通过 FK 关联加载，兜底通过 className 字符串匹配
            List<Student> classStudents = studentRepository.findByClassInfoId(classInfo.getId())
                    .stream()
                    .filter(this::isActive)
                    .toList();

            if (classStudents.isEmpty() && classInfo.getName() != null) {
                classStudents = studentRepository.findByClassName(classInfo.getName())
                        .stream()
                        .filter(this::isActive)
                        .toList();
            }

            if (classStudents.isEmpty()) continue;

            int enrolled = 0, actual = 0, leave = 0, military = 0, dayStudent = 0;
            int absent = 0, personalLeave = 0, sickLeave = 0, longSickLeave = 0;
            int intern = 0, lateReturn = 0;
            int classMale = 0, classFemale = 0, classActualMale = 0, classActualFemale = 0;

            List<LeaveDetailData.LeaveDetailItem> details = new ArrayList<>();

            for (Student s : classStudents) {
                CheckStatus status = resolveStatus(s, recordMap);
                enrolled++;
                if ("男".equals(s.getGender())) classMale++;
                else if ("女".equals(s.getGender())) classFemale++;

                switch (status) {
                    case PRESENT -> { actual++; if ("男".equals(s.getGender())) classActualMale++;
                                          else if ("女".equals(s.getGender())) classActualFemale++; }
                    case PERSONAL_LEAVE -> { personalLeave++; leave++; }
                    case SICK_LEAVE -> { sickLeave++; leave++; }
                    case MILITARY -> military++;
                    case DAY_STUDENT -> dayStudent++;
                    case ABSENT -> absent++;
                    case LONG_SICK_LEAVE -> longSickLeave++;
                    case INTERN -> intern++;
                    case LATE_RETURN -> lateReturn++;
                }

                if (!isNormalStatus(status)) {
                    CheckRecord record = recordMap != null ? recordMap.get(s.getId()) : null;
                    details.add(LeaveDetailData.LeaveDetailItem.builder()
                            .studentName(s.getName())
                            .status(status)
                            .gender(s.getGender())
                            .proofFile(record != null ? record.getProofFile() : null)
                            .build());
                }
            }

            details.sort(Comparator.comparingInt(
                    d -> DETAIL_ORDER.getOrDefault(d.getStatus(), 99)));

            rows.add(LeaveDetailData.ClassLeaveRow.builder()
                    .className(classInfo.getName())
                    .headTeacher(classInfo.getCounselorName() != null
                            ? classInfo.getCounselorName() : "")
                    .enrolled(enrolled).actual(actual).leave(leave)
                    .military(military).dayStudent(dayStudent).absent(absent)
                    .longSickLeave(longSickLeave).intern(intern).lateReturn(lateReturn)
                    .transferOut(0).suspend(0).dropOut(0).specialCase("")
                    .details(details)
                    .build());

            totalEnrolled += enrolled;
            totalActual += actual;
            totalLeave += leave;
            totalMilitary += military;
            totalDayStudent += dayStudent;
            totalAbsent += absent;
            totalLongSickLeave += longSickLeave;
            totalIntern += intern;
            totalLateReturn += lateReturn;
            totalPersonalLeave += personalLeave;
            totalSickLeave += sickLeave;
            totalMale += classMale;
            totalFemale += classFemale;
            actualMale += classActualMale;
            actualFemale += classActualFemale;
        }

        // 顶部概要
        LeaveDetailData.HeaderSummary header = LeaveDetailData.HeaderSummary.builder()
                .totalStudents(totalEnrolled)
                .male(totalMale)
                .female(totalFemale)
                .actual(totalActual)
                .actualMale(actualMale)
                .actualFemale(actualFemale)
                .leaveTotal(totalLeave)
                .personalLeave(totalPersonalLeave)
                .sickLeave(totalSickLeave)
                .longSickLeave(totalLongSickLeave)
                .intern(totalIntern)
                .lateReturn(totalLateReturn)
                .dayStudent(totalDayStudent)
                .build();
        data.setHeader(header);
        data.setRows(rows);
        return data;
    }

    private boolean isNormalStatus(CheckStatus status) {
        return status == CheckStatus.PRESENT || status == CheckStatus.UNKNOWN;
    }

    @lombok.Data
    public static class ClassReportData {
        private String date;
        private List<StudentRow> rows;
        private Map<String, Object> summary;

        @lombok.Data
        public static class StudentRow {
            private String studentNo, studentName, gender, buildingName, roomNumber,
                    checkStatus, leaveType, proofFilePath, remark, studentRemark;
            private Integer floorNumber;
        }
    }
}