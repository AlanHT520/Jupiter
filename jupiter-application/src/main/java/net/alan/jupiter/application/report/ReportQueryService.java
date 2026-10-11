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
    private final CheckRuleService checkRuleService;

    public Object generateReport(ReportType type, String taskId, String targetId) {
        return switch (type) {
            case BUILDING -> generateBuildingReport(taskId, targetId);
            case COLLEGE -> generateCollegeReport(taskId, targetId);
            case CLASS -> generateClassReport(taskId, targetId);
        };
    }

    public Object generateAggregatedReport(ReportType type, String targetId) {
        List<Task> completedTasks = taskRepository.findCompletedTasks(TaskStatus.COMPLETED);
        if (completedTasks.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "没有已完成的查寝任务");
        }

        List<String> taskIds = completedTasks.stream().map(Task::getId).collect(Collectors.toList());
        List<CheckRecord> allRecords = checkRecordRepository.findByTaskIdIn(taskIds);

        Map<String, java.time.LocalDate> taskDateMap = completedTasks.stream()
                .collect(Collectors.toMap(Task::getId, t ->
                        t.getTaskDate() != null ? t.getTaskDate() : java.time.LocalDate.now()));

        Map<String, CheckRecord> mergedRecordMap = buildMergedRecordMap(allRecords, taskDateMap);
        String dateRange = calculateReportDateRange(completedTasks);

        return switch (type) {
            case BUILDING -> generateBuildingReportAggregated(targetId, mergedRecordMap, dateRange);
            case COLLEGE -> generateCollegeReportAggregated(targetId, mergedRecordMap, dateRange);
            case CLASS -> generateClassReportAggregated(targetId, mergedRecordMap, dateRange);
        };
    }

    private Map<String, CheckRecord> buildMergedRecordMap(
            List<CheckRecord> allRecords, Map<String, java.time.LocalDate> taskDateMap) {
        List<CheckRecord> sorted = new ArrayList<>(allRecords);
        sorted.sort((a, b) -> {
            java.time.LocalDate da = taskDateMap.getOrDefault(a.getTask().getId(), java.time.LocalDate.MIN);
            java.time.LocalDate db = taskDateMap.getOrDefault(b.getTask().getId(), java.time.LocalDate.MIN);
            return db.compareTo(da);
        });
        return sorted.stream()
                .filter(r -> r.getStudent() != null)
                .collect(Collectors.toMap(
                        r -> r.getStudent().getId(), r -> r, (a, b) -> a, LinkedHashMap::new));
    }

    private String calculateReportDateRange(List<Task> tasks) {
        List<java.time.LocalDate> dates = tasks.stream()
                .map(Task::getTaskDate)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        if (dates.isEmpty()) {
            return java.time.LocalDate.now().toString();
        }
        if (dates.size() == 1) {
            return dates.get(0).toString();
        }
        return dates.get(0) + " ~ " + dates.get(dates.size() - 1);
    }

    private BuildingReportData generateBuildingReport(String taskId, String buildingId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.TASK_NOT_FOUND));
        java.util.Set<String> taskRoomNumbers = parseTaskRoomNumbers(task);

        List<Floor> floors = floorRepository.findByBuildingIdOrderByFloorNumber(buildingId);
        List<Room> allRooms = roomRepository.findByBuildingId(buildingId);
        if (taskRoomNumbers != null) {
            allRooms = allRooms.stream()
                    .filter(r -> taskRoomNumbers.contains(r.getRoomNumber()))
                    .collect(Collectors.toList());
        }
        List<Student> allStudents = studentRepository.findByBuildingId(buildingId);
        if (taskRoomNumbers != null) {
            allStudents = allStudents.stream()
                    .filter(s -> s.getRoomNumber() != null && taskRoomNumbers.contains(s.getRoomNumber()))
                    .collect(Collectors.toList());
        }
        List<CheckRecord> records = checkRecordRepository.findByTaskId(taskId);

        Map<String, CheckRecord> recordMap = buildRecordMap(records);
        Map<String, List<Student>> studentsByRoom = groupByRoomId(allStudents);
        Map<String, List<Room>> roomsByFloor = allRooms.stream()
                .filter(r -> r.getFloor() != null)
                .collect(Collectors.groupingBy(r -> r.getFloor().getId()));

        BuildingReportData data = new BuildingReportData();
        List<BuildingReportData.FloorSummary> floorSummaries = new ArrayList<>();
        List<BuildingReportData.RoomDetail> roomDetails = new ArrayList<>();

        for (Floor floor : floors) {
            var fs = new BuildingReportData.FloorSummary();
            fs.setFloor(String.valueOf(floor.getFloorNumber()));

            int should = 0, actual = 0, personalLeave = 0, sickLeave = 0,
                lateReturn = 0, military = 0, intern = 0, absent = 0, unchecked = 0;
            List<String> absentNames = new ArrayList<>();

            List<Room> floorRooms = roomsByFloor.getOrDefault(floor.getId(), List.of());
            for (Room room : floorRooms) {
                List<Student> roomStudents = studentsByRoom.getOrDefault(room.getId(), List.of());
                for (Student student : roomStudents) {
                    if (!isActive(student)) continue;
                    CheckStatus status = resolveStatus(student, recordMap);

                    should++;
                    switch (status) {
                        case PRESENT -> actual++;
                        case PERSONAL_LEAVE -> personalLeave++;
                        case SICK_LEAVE -> sickLeave++;
                        case LATE_RETURN -> lateReturn++;
                        case MILITARY -> military++;
                        case INTERN -> intern++;
                        case ABSENT -> { absent++; absentNames.add(student.getName()); }
                        case UNKNOWN -> unchecked++;
                    }

                    var rd = new BuildingReportData.RoomDetail();
                    rd.setFloor(String.valueOf(floor.getFloorNumber()));
                    rd.setRoomNumber(room.getRoomNumber());
                    rd.setStudentNo(student.getStudentNo());
                    rd.setStudentName(student.getName());
                    rd.setClassName(student.getClassName());
                    rd.setCheckStatus(status.getLabel());
                    rd.setLeaveType(leaveType(status));
                    rd.setProofFile(getProofFilePath(recordMap, student));
                    rd.setRemark(getRemark(recordMap, student));
                    rd.setStudentRemark(student.getRemark() != null ? student.getRemark() : "");
                    roomDetails.add(rd);
                }
            }

            fs.setShould(should); fs.setActual(actual);
            fs.setPersonalLeave(personalLeave); fs.setSickLeave(sickLeave);
            fs.setLateReturn(lateReturn);
            fs.setMilitary(military); fs.setIntern(intern);
            fs.setAbsent(absent); fs.setUnchecked(unchecked);
            fs.setAbsentNames(String.join("、", absentNames));
            floorSummaries.add(fs);
        }

        data.setFloorSummaries(floorSummaries);
        data.setRoomDetails(roomDetails);
        return data;
    }

    private CollegeReportData generateCollegeReport(String taskId, String collegeName) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.TASK_NOT_FOUND));

        java.util.Set<String> taskRoomNumbers = parseTaskRoomNumbers(task);

        List<DormBuilding> buildings = buildingRepository.findByIsActiveTrue();
        List<CheckRecord> records = checkRecordRepository.findByTaskId(taskId);
        Map<String, CheckRecord> recordMap = buildRecordMap(records);

        CollegeReportData data = new CollegeReportData();
        data.setDate(java.time.LocalDate.now().toString());
        data.setCollegeName(collegeName);

        List<CollegeReportData.BuildingSummary> buildingSummaries = new ArrayList<>();
        List<FloorDetail> floorDetails = new ArrayList<>();

        for (DormBuilding building : buildings) {
            List<Student> buildingStudents = studentRepository.findByBuildingId(building.getId());
            if (taskRoomNumbers != null) {
                buildingStudents = buildingStudents.stream()
                        .filter(s -> s.getRoomNumber() != null && taskRoomNumbers.contains(s.getRoomNumber()))
                        .collect(Collectors.toList());
            }
            List<Floor> floors = floorRepository.findByBuildingIdOrderByFloorNumber(building.getId());

            Map<Integer, List<Student>> studentsByFloor = buildingStudents.stream()
                    .filter(s -> s.getFloorNumber() != null)
                    .collect(Collectors.groupingBy(Student::getFloorNumber));

            var bs = new CollegeReportData.BuildingSummary();
            bs.setBuildingName(building.getName());

            int should = 0, actual = 0, personalLeave = 0, sickLeave = 0,
                lateReturn = 0, military = 0, intern = 0, dayStudent = 0,
                absent = 0, longSickLeave = 0, unchecked = 0;
            List<String> absentNames = new ArrayList<>();

            for (Student student : buildingStudents) {
                if (!isActive(student)) continue;
                CheckStatus status = resolveStatus(student, recordMap);
                should++;
                switch (status) {
                    case PRESENT -> actual++;
                    case PERSONAL_LEAVE -> personalLeave++;
                    case SICK_LEAVE -> sickLeave++;
                    case LATE_RETURN -> lateReturn++;
                    case MILITARY -> military++;
                    case INTERN -> intern++;
                    case DAY_STUDENT -> dayStudent++;
                    case LONG_SICK_LEAVE -> longSickLeave++;
                    case ABSENT -> { absent++; absentNames.add(student.getName()); }
                    case UNKNOWN -> unchecked++;
                }
            }

            bs.setShould(should); bs.setActual(actual);
            bs.setPersonalLeave(personalLeave); bs.setSickLeave(sickLeave);
            bs.setLateReturn(lateReturn);
            bs.setMilitary(military); bs.setIntern(intern);
            bs.setDayStudent(dayStudent); bs.setLongSickLeave(longSickLeave);
            bs.setAbsent(absent); bs.setUnchecked(unchecked);
            bs.setAbsentNames(String.join("、", absentNames));
            buildingSummaries.add(bs);

            for (Floor floor : floors) {
                var fd = new FloorDetail();
                fd.setBuildingName(building.getName());
                fd.setFloor(String.valueOf(floor.getFloorNumber()));

                int fShould = 0, fActual = 0, fPersonalLeave = 0, fSickLeave = 0,
                    fLateReturn = 0, fMilitary = 0, fIntern = 0, fAbsent = 0, fUnchecked = 0;
                List<String> fAbsentNames = new ArrayList<>();

                List<Student> floorStudents = studentsByFloor
                        .getOrDefault(floor.getFloorNumber(), List.of());
                for (Student student : floorStudents) {
                    if (!isActive(student)) continue;
                    CheckStatus status = resolveStatus(student, recordMap);
                    fShould++;
                    switch (status) {
                        case PRESENT -> fActual++;
                        case PERSONAL_LEAVE -> fPersonalLeave++;
                        case SICK_LEAVE -> fSickLeave++;
                        case LATE_RETURN -> fLateReturn++;
                        case MILITARY -> fMilitary++;
                        case INTERN -> fIntern++;
                        case ABSENT -> { fAbsent++; fAbsentNames.add(student.getName()); }
                        case UNKNOWN -> fUnchecked++;
                    }
                }
                fd.setShould(fShould); fd.setActual(fActual);
                fd.setPersonalLeave(fPersonalLeave); fd.setSickLeave(fSickLeave);
                fd.setLateReturn(fLateReturn);
                fd.setMilitary(fMilitary); fd.setIntern(fIntern);
                fd.setAbsent(fAbsent); fd.setUnchecked(fUnchecked);
                fd.setAbsentNames(String.join("、", fAbsentNames));
                floorDetails.add(fd);
            }
        }

        data.setNewStudent(calcNewStudentStats(buildings, recordMap));
        data.setBuildingSummaries(buildingSummaries);
        data.setFloorDetails(floorDetails);
        return data;
    }

    private ClassReportData generateClassReport(String taskId, String classId) {
        List<Student> students = studentRepository.findByClassInfoId(classId);
        List<CheckRecord> records = checkRecordRepository.findByTaskId(taskId);
        Map<String, CheckRecord> recordMap = buildRecordMap(records);

        ClassReportData data = new ClassReportData();
        List<ClassReportData.StudentRow> rows = new ArrayList<>();

        int present = 0, personalLeave = 0, sickLeave = 0, lateReturn = 0,
            absent = 0, military = 0, intern = 0, dayStudent = 0,
            longSickLeave = 0, unchecked = 0;

        for (Student student : students) {
            CheckStatus status = resolveStatus(student, recordMap);

            var row = new ClassReportData.StudentRow();
            row.setStudentNo(student.getStudentNo());
            row.setStudentName(student.getName());
            row.setGender(student.getGender());
            row.setBuildingName(student.getBuildingName());
            row.setFloorNumber(student.getFloorNumber());
            row.setRoomNumber(student.getRoomNumber());
            row.setCheckStatus(status.getLabel());
            row.setLeaveType(leaveType(status));
            row.setProofFilePath(getProofFilePath(recordMap, student));
            row.setRemark(getRemark(recordMap, student));
            row.setStudentRemark(student.getRemark() != null ? student.getRemark() : "");

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
            rows.add(row);
        }

        data.setRows(rows);
        Map<String, Object> summary = new HashMap<>();
        summary.put("total", students.size()); summary.put("present", present);
        summary.put("personalLeave", personalLeave); summary.put("sickLeave", sickLeave);
        summary.put("lateReturn", lateReturn); summary.put("absent", absent);
        summary.put("military", military); summary.put("intern", intern);
        summary.put("dayStudent", dayStudent); summary.put("longSickLeave", longSickLeave);
        summary.put("unchecked", unchecked);
        data.setSummary(summary);
        return data;
    }

    private NewStudentStats calcNewStudentStats(List<DormBuilding> buildings,
                                                  Map<String, CheckRecord> recordMap) {
        List<Student> allStudents = new ArrayList<>();
        for (DormBuilding b : buildings) {
            allStudents.addAll(studentRepository.findByBuildingId(b.getId()));
        }
        int should = 0, actual = 0, male = 0, female = 0, militaryMale = 0, leave = 0;
        int intern = 0, dayStudent = 0, longSickLeave = 0, absent = 0, lateReturn = 0;
        for (Student s : allStudents) {
            if (s.getIsNewStudent() == null || !s.getIsNewStudent()) continue;
            if (!isActive(s)) continue;

            CheckStatus status = resolveStatus(s, recordMap);
            should++;
            if ("男".equals(s.getGender())) male++;
            else if ("女".equals(s.getGender())) female++;

            switch (status) {
                case PRESENT -> actual++;
                case PERSONAL_LEAVE, SICK_LEAVE -> leave++;
                case MILITARY -> { if ("男".equals(s.getGender())) militaryMale++; }
                case INTERN -> intern++;
                case DAY_STUDENT -> dayStudent++;
                case LONG_SICK_LEAVE -> longSickLeave++;
                case ABSENT -> absent++;
                case LATE_RETURN -> lateReturn++;
                case UNKNOWN -> { /* 未检查不计入任何类别 */ }
            }
        }
        var ns = new NewStudentStats();
        ns.setShould(should); ns.setActual(actual);
        ns.setMale(male); ns.setFemale(female);
        ns.setMilitaryMale(militaryMale); ns.setLeave(leave);
        ns.setIntern(intern); ns.setDayStudent(dayStudent);
        ns.setLongSickLeave(longSickLeave); ns.setAbsent(absent);
        ns.setLateReturn(lateReturn);
        return ns;
    }

    private Map<String, CheckRecord> buildRecordMap(List<CheckRecord> records) {
        return records.stream()
                .filter(r -> r.getStudent() != null)
                .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r, (a, b) -> a));
    }

    private BuildingReportData generateBuildingReportAggregated(
            String buildingId, Map<String, CheckRecord> recordMap, String dateRange) {
        List<Floor> floors = floorRepository.findByBuildingIdOrderByFloorNumber(buildingId);
        List<Room> allRooms = roomRepository.findByBuildingId(buildingId);
        List<Student> allStudents = studentRepository.findByBuildingId(buildingId);

        Map<String, List<Student>> studentsByRoom = groupByRoomId(allStudents);
        Map<String, List<Room>> roomsByFloor = allRooms.stream()
                .filter(r -> r.getFloor() != null)
                .collect(Collectors.groupingBy(r -> r.getFloor().getId()));

        BuildingReportData data = new BuildingReportData();
        List<BuildingReportData.FloorSummary> floorSummaries = new ArrayList<>();
        List<BuildingReportData.RoomDetail> roomDetails = new ArrayList<>();

        for (Floor floor : floors) {
            var fs = new BuildingReportData.FloorSummary();
            fs.setFloor(String.valueOf(floor.getFloorNumber()));

            int should = 0, actual = 0, personalLeave = 0, sickLeave = 0,
                lateReturn = 0, military = 0, intern = 0, absent = 0, unchecked = 0;
            List<String> absentNames = new ArrayList<>();

            List<Room> floorRooms = roomsByFloor.getOrDefault(floor.getId(), List.of());
            for (Room room : floorRooms) {
                List<Student> roomStudents = studentsByRoom.getOrDefault(room.getId(), List.of());
                for (Student student : roomStudents) {
                    if (!isActive(student)) continue;
                    CheckStatus status = resolveStatus(student, recordMap);

                    should++;
                    switch (status) {
                        case PRESENT -> actual++;
                        case PERSONAL_LEAVE -> personalLeave++;
                        case SICK_LEAVE -> sickLeave++;
                        case LATE_RETURN -> lateReturn++;
                        case MILITARY -> military++;
                        case INTERN -> intern++;
                        case ABSENT -> { absent++; absentNames.add(student.getName()); }
                        case UNKNOWN -> unchecked++;
                    }

                    var rd = new BuildingReportData.RoomDetail();
                    rd.setFloor(String.valueOf(floor.getFloorNumber()));
                    rd.setRoomNumber(room.getRoomNumber());
                    rd.setStudentNo(student.getStudentNo());
                    rd.setStudentName(student.getName());
                    rd.setClassName(student.getClassName());
                    rd.setCheckStatus(status.getLabel());
                    rd.setLeaveType(leaveType(status));
                    rd.setProofFile(getProofFilePath(recordMap, student));
                    rd.setRemark(getRemark(recordMap, student));
                    rd.setStudentRemark(student.getRemark() != null ? student.getRemark() : "");
                    roomDetails.add(rd);
                }
            }

            fs.setShould(should); fs.setActual(actual);
            fs.setPersonalLeave(personalLeave); fs.setSickLeave(sickLeave);
            fs.setLateReturn(lateReturn);
            fs.setMilitary(military); fs.setIntern(intern);
            fs.setAbsent(absent); fs.setUnchecked(unchecked);
            fs.setAbsentNames(String.join("、", absentNames));
            floorSummaries.add(fs);
        }

        data.setFloorSummaries(floorSummaries);
        data.setRoomDetails(roomDetails);
        return data;
    }

    private CollegeReportData generateCollegeReportAggregated(
            String collegeName, Map<String, CheckRecord> recordMap, String dateRange) {
        List<DormBuilding> buildings = buildingRepository.findByIsActiveTrue();

        CollegeReportData data = new CollegeReportData();
        data.setDate(dateRange);
        data.setCollegeName(collegeName);

        List<CollegeReportData.BuildingSummary> buildingSummaries = new ArrayList<>();
        List<FloorDetail> floorDetails = new ArrayList<>();

        for (DormBuilding building : buildings) {
            List<Student> buildingStudents = studentRepository.findByBuildingId(building.getId());
            List<Floor> floors = floorRepository.findByBuildingIdOrderByFloorNumber(building.getId());

            Map<Integer, List<Student>> studentsByFloor = buildingStudents.stream()
                    .filter(s -> s.getFloorNumber() != null)
                    .collect(Collectors.groupingBy(Student::getFloorNumber));

            var bs = new CollegeReportData.BuildingSummary();
            bs.setBuildingName(building.getName());

            int should = 0, actual = 0, personalLeave = 0, sickLeave = 0,
                lateReturn = 0, military = 0, intern = 0, dayStudent = 0,
                absent = 0, longSickLeave = 0, unchecked = 0;
            List<String> absentNames = new ArrayList<>();

            for (Student student : buildingStudents) {
                if (!isActive(student)) continue;
                CheckStatus status = resolveStatus(student, recordMap);
                should++;
                switch (status) {
                    case PRESENT -> actual++;
                    case PERSONAL_LEAVE -> personalLeave++;
                    case SICK_LEAVE -> sickLeave++;
                    case LATE_RETURN -> lateReturn++;
                    case MILITARY -> military++;
                    case INTERN -> intern++;
                    case DAY_STUDENT -> dayStudent++;
                    case LONG_SICK_LEAVE -> longSickLeave++;
                    case ABSENT -> { absent++; absentNames.add(student.getName()); }
                    case UNKNOWN -> unchecked++;
                }
            }

            bs.setShould(should); bs.setActual(actual);
            bs.setPersonalLeave(personalLeave); bs.setSickLeave(sickLeave);
            bs.setLateReturn(lateReturn);
            bs.setMilitary(military); bs.setIntern(intern);
            bs.setDayStudent(dayStudent); bs.setLongSickLeave(longSickLeave);
            bs.setAbsent(absent); bs.setUnchecked(unchecked);
            bs.setAbsentNames(String.join("、", absentNames));
            buildingSummaries.add(bs);

            for (Floor floor : floors) {
                var fd = new FloorDetail();
                fd.setBuildingName(building.getName());
                fd.setFloor(String.valueOf(floor.getFloorNumber()));

                int fShould = 0, fActual = 0, fPersonalLeave = 0, fSickLeave = 0,
                    fLateReturn = 0, fMilitary = 0, fIntern = 0, fAbsent = 0, fUnchecked = 0;
                List<String> fAbsentNames = new ArrayList<>();

                List<Student> floorStudents = studentsByFloor
                        .getOrDefault(floor.getFloorNumber(), List.of());
                for (Student student : floorStudents) {
                    if (!isActive(student)) continue;
                    CheckStatus status = resolveStatus(student, recordMap);
                    fShould++;
                    switch (status) {
                        case PRESENT -> fActual++;
                        case PERSONAL_LEAVE -> fPersonalLeave++;
                        case SICK_LEAVE -> fSickLeave++;
                        case LATE_RETURN -> fLateReturn++;
                        case MILITARY -> fMilitary++;
                        case INTERN -> fIntern++;
                        case ABSENT -> { fAbsent++; fAbsentNames.add(student.getName()); }
                        case UNKNOWN -> fUnchecked++;
                    }
                }
                fd.setShould(fShould); fd.setActual(fActual);
                fd.setPersonalLeave(fPersonalLeave); fd.setSickLeave(fSickLeave);
                fd.setLateReturn(fLateReturn);
                fd.setMilitary(fMilitary); fd.setIntern(fIntern);
                fd.setAbsent(fAbsent); fd.setUnchecked(fUnchecked);
                fd.setAbsentNames(String.join("、", fAbsentNames));
                floorDetails.add(fd);
            }
        }

        data.setNewStudent(calcNewStudentStatsAggregated(buildings, recordMap));
        data.setBuildingSummaries(buildingSummaries);
        data.setFloorDetails(floorDetails);
        return data;
    }

    private NewStudentStats calcNewStudentStatsAggregated(
            List<DormBuilding> buildings, Map<String, CheckRecord> recordMap) {
        List<Student> allStudents = new ArrayList<>();
        for (DormBuilding b : buildings) {
            allStudents.addAll(studentRepository.findByBuildingId(b.getId()));
        }
        int should = 0, actual = 0, male = 0, female = 0, militaryMale = 0, leave = 0;
        int intern = 0, dayStudent = 0, longSickLeave = 0, absent = 0, lateReturn = 0;
        for (Student s : allStudents) {
            if (s.getIsNewStudent() == null || !s.getIsNewStudent()) continue;
            if (!isActive(s)) continue;

            CheckStatus status = resolveStatus(s, recordMap);
            should++;
            if ("男".equals(s.getGender())) male++;
            else if ("女".equals(s.getGender())) female++;

            switch (status) {
                case PRESENT -> actual++;
                case PERSONAL_LEAVE, SICK_LEAVE -> leave++;
                case MILITARY -> { if ("男".equals(s.getGender())) militaryMale++; }
                case INTERN -> intern++;
                case DAY_STUDENT -> dayStudent++;
                case LONG_SICK_LEAVE -> longSickLeave++;
                case ABSENT -> absent++;
                case LATE_RETURN -> lateReturn++;
                case UNKNOWN -> { /* 未检查不计入任何类别 */ }
            }
        }
        var ns = new NewStudentStats();
        ns.setShould(should); ns.setActual(actual);
        ns.setMale(male); ns.setFemale(female);
        ns.setMilitaryMale(militaryMale); ns.setLeave(leave);
        ns.setIntern(intern); ns.setDayStudent(dayStudent);
        ns.setLongSickLeave(longSickLeave); ns.setAbsent(absent);
        ns.setLateReturn(lateReturn);
        return ns;
    }

    private ClassReportData generateClassReportAggregated(
            String classId, Map<String, CheckRecord> recordMap, String dateRange) {
        List<Student> students = studentRepository.findByClassInfoId(classId);

        ClassReportData data = new ClassReportData();
        List<ClassReportData.StudentRow> rows = new ArrayList<>();

        int present = 0, personalLeave = 0, sickLeave = 0, lateReturn = 0,
            absent = 0, military = 0, intern = 0, dayStudent = 0,
            longSickLeave = 0, unchecked = 0;

        for (Student student : students) {
            CheckStatus status = resolveStatus(student, recordMap);

            var row = new ClassReportData.StudentRow();
            row.setStudentNo(student.getStudentNo());
            row.setStudentName(student.getName());
            row.setGender(student.getGender());
            row.setBuildingName(student.getBuildingName());
            row.setFloorNumber(student.getFloorNumber());
            row.setRoomNumber(student.getRoomNumber());
            row.setCheckStatus(status.getLabel());
            row.setLeaveType(leaveType(status));
            row.setProofFilePath(getProofFilePath(recordMap, student));
            row.setRemark(getRemark(recordMap, student));
            row.setStudentRemark(student.getRemark() != null ? student.getRemark() : "");

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
            rows.add(row);
        }

        data.setRows(rows);
        Map<String, Object> summary = new HashMap<>();
        summary.put("total", students.size()); summary.put("present", present);
        summary.put("personalLeave", personalLeave); summary.put("sickLeave", sickLeave);
        summary.put("lateReturn", lateReturn); summary.put("absent", absent);
        summary.put("military", military); summary.put("intern", intern);
        summary.put("dayStudent", dayStudent); summary.put("longSickLeave", longSickLeave);
        summary.put("unchecked", unchecked);
        data.setSummary(summary);
        return data;
    }

    private java.util.Set<String> parseTaskRoomNumbers(Task task) {
        String roomNumbers = task.getRoomNumbers();
        if (roomNumbers == null || roomNumbers.isBlank()) {
            return null;
        }
        return java.util.Arrays.stream(roomNumbers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private Map<String, List<Student>> groupByRoomId(List<Student> students) {
        return students.stream()
                .collect(Collectors.groupingBy(s -> s.getRoom() != null ? s.getRoom().getId() : ""));
    }

    private boolean isActive(Student s) {
        return s.getIsActive() == null || s.getIsActive();
    }

    private CheckStatus resolveStatus(Student student, Map<String, CheckRecord> recordMap) {
        CheckRecord record = recordMap.get(student.getId());
        if (record != null && record.getCheckStatus() != null) {
            return record.getCheckStatus();
        }
        CheckStatus preset = checkRuleService.determinePresetStatus(student);
        if (preset != null) {
            return preset;
        }
        return CheckStatus.UNKNOWN;
    }

    private String getRemark(Map<String, CheckRecord> recordMap, Student student) {
        CheckRecord record = recordMap.get(student.getId());
        return record != null && record.getRemark() != null ? record.getRemark() : "";
    }

    private String getProofFilePath(Map<String, CheckRecord> recordMap, Student student) {
        CheckRecord record = recordMap.get(student.getId());
        return record != null ? record.getProofFile() : null;
    }

    private String leaveType(CheckStatus status) {
        return switch (status) {
            case PERSONAL_LEAVE -> "事假";
            case SICK_LEAVE -> "病假";
            case LONG_SICK_LEAVE -> "长期病假";
            default -> "";
        };
    }

    public enum ReportType {
        BUILDING, COLLEGE, CLASS
    }

    @lombok.Data
    public static class BuildingReportData {
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
            private String floor, roomNumber, studentNo, studentName, className, checkStatus, leaveType, proofFile, remark, studentRemark;
        }
    }

    @lombok.Data
    public static class CollegeReportData {
        private String date, collegeName;
        private List<BuildingSummary> buildingSummaries;
        private List<FloorDetail> floorDetails;
        private NewStudentStats newStudent;

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

    @lombok.Data
    public static class NewStudentStats {
        private int should, actual, male, female, militaryMale, leave;
        private int intern, dayStudent, longSickLeave, absent, lateReturn;
    }

    @lombok.Data
    public static class ClassReportData {
        private List<StudentRow> rows;
        private Map<String, Object> summary;

        @lombok.Data
        public static class StudentRow {
            private String studentNo, studentName, gender, buildingName, roomNumber, checkStatus, leaveType,
                    proofFilePath, remark, studentRemark;
            private Integer floorNumber;
        }
    }
}