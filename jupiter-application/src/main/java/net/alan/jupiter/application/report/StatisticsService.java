package net.alan.jupiter.application.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.enums.TaskStatus;
import net.alan.jupiter.domain.model.*;
import net.alan.jupiter.domain.service.AttendanceCalculator;
import net.alan.jupiter.domain.repository.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final TaskRepository taskRepository;
    private final CheckRecordRepository checkRecordRepository;
    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;
    private final StudentRepository studentRepository;
    private final AttendanceCalculator attendanceCalculator;

    public ReportData generateReport(String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.TASK_NOT_FOUND));

        DormBuilding building = task.getBuilding();
        java.util.Set<String> taskRoomNumbers = parseTaskRoomNumbers(task);

        List<Floor> floors = floorRepository.findByBuildingIdOrderByFloorNumber(building.getId());

        List<Room> allRooms = roomRepository.findByBuildingId(building.getId());
        if (taskRoomNumbers != null) {
            allRooms = allRooms.stream()
                    .filter(r -> taskRoomNumbers.contains(r.getRoomNumber()))
                    .collect(Collectors.toList());
        }
        Map<String, List<Room>> roomsByFloor = allRooms.stream()
                .collect(Collectors.groupingBy(r -> r.getFloor().getId()));

        List<String> allRoomIds = allRooms.stream().map(Room::getId).toList();
        List<Student> allStudents = studentRepository.findByRoomIdInOrderByBedNumber(allRoomIds);
        Map<String, List<Student>> studentsByRoom = allStudents.stream()
                .collect(Collectors.groupingBy(s -> s.getRoom().getId()));

        List<BuildingBlock> buildingBlocks = new ArrayList<>();
        BuildingBlock block = new BuildingBlock();
        block.setBuilding(building.getName());

        int totalShould = 0, totalActual = 0, totalPersonalLeave = 0, totalSickLeave = 0;
        int totalLateReturn = 0, totalMilitary = 0, totalIntern = 0;
        int totalDayStudent = 0, totalAbsent = 0, totalUnchecked = 0;
        List<BuildingBlock.FloorStat> floorStats = new ArrayList<>();
        List<CheckRecord> records = checkRecordRepository.findByTaskId(taskId);

        for (Floor floor : floors) {
            List<Room> rooms = roomsByFloor.getOrDefault(floor.getId(), List.of());
            List<Student> floorStudents = new ArrayList<>();
            for (Room room : rooms) {
                floorStudents.addAll(studentsByRoom.getOrDefault(room.getId(), List.of()));
            }

            AttendanceCalculator.FloorAttendance fa = attendanceCalculator
                    .calcFloorAttendance(floor, floorStudents, records);

            BuildingBlock.FloorStat fs = buildFloorStat(floor, fa);
            floorStats.add(fs);

            totalShould += fa.getShould();
            totalActual += fa.getActual();
            totalPersonalLeave += fa.getPersonalLeave();
            totalSickLeave += fa.getSickLeave();
            totalLateReturn += fa.getLateReturn();
            totalMilitary += fa.getMilitary();
            totalIntern += fa.getIntern();
            totalDayStudent += fa.getDayStudent();
            totalAbsent += fa.getAbsent();
            totalUnchecked += fa.getUnchecked();
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("should", totalShould);
        summary.put("actual", totalActual);
        summary.put("leave", totalPersonalLeave + totalSickLeave);
        summary.put("personalLeave", totalPersonalLeave);
        summary.put("sickLeave", totalSickLeave);
        summary.put("lateReturn", totalLateReturn);
        summary.put("military", totalMilitary);
        summary.put("intern", totalIntern);
        summary.put("dayStudent", totalDayStudent);
        summary.put("absent", totalAbsent);
        summary.put("unchecked", totalUnchecked);
        block.setSummary(summary);
        block.setFloors(floorStats);
        buildingBlocks.add(block);

        ReportData data = new ReportData();
        data.setDate(task.getTaskDate() != null ? task.getTaskDate().toString() : java.time.LocalDate.now().toString());
        data.setCollege("");
        data.setBlocks(buildingBlocks);
        return data;
    }

    public ReportData generateAggregatedReport() {
        List<Task> completedTasks = taskRepository.findCompletedTasks(TaskStatus.COMPLETED);
        if (completedTasks.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "没有已完成的查寝任务");
        }

        List<String> taskIds = completedTasks.stream().map(Task::getId).collect(Collectors.toList());
        List<CheckRecord> allRecords = checkRecordRepository.findByTaskIdIn(taskIds);

        Map<String, java.time.LocalDate> taskDateMap = completedTasks.stream()
                .collect(Collectors.toMap(Task::getId, t ->
                        t.getTaskDate() != null ? t.getTaskDate() : java.time.LocalDate.now()));

        Map<String, CheckRecord> latestByStudent = mergeLatestPerStudent(allRecords, taskDateMap);

        Map<String, DormBuilding> buildingMap = new LinkedHashMap<>();
        for (Task task : completedTasks) {
            if (task.getBuilding() != null) {
                buildingMap.putIfAbsent(task.getBuilding().getId(), task.getBuilding());
            }
        }

        List<BuildingBlock> buildingBlocks = new ArrayList<>();
        String dateRange = calculateDateRange(completedTasks);

        for (DormBuilding building : buildingMap.values()) {
            BuildingBlock block = buildBlockForBuilding(
                    building, new ArrayList<>(latestByStudent.values()));
            buildingBlocks.add(block);
        }

        ReportData data = new ReportData();
        data.setDate(dateRange);
        data.setCollege("");
        data.setBlocks(buildingBlocks);
        return data;
    }

    private Map<String, CheckRecord> mergeLatestPerStudent(
            List<CheckRecord> allRecords, Map<String, java.time.LocalDate> taskDateMap) {
        List<CheckRecord> sorted = new ArrayList<>(allRecords);
        sorted.sort((a, b) -> {
            java.time.LocalDate da = taskDateMap.getOrDefault(a.getTask().getId(), java.time.LocalDate.MIN);
            java.time.LocalDate db = taskDateMap.getOrDefault(b.getTask().getId(), java.time.LocalDate.MIN);
            return db.compareTo(da);
        });

        Map<String, CheckRecord> latest = new LinkedHashMap<>();
        for (CheckRecord r : sorted) {
            String key = r.getStudent() != null ? r.getStudent().getId()
                    : (r.getStudentNo() != null ? r.getStudentNo() : "");
            if (!key.isEmpty()) {
                latest.putIfAbsent(key, r);
            }
        }
        return latest;
    }

    private BuildingBlock buildBlockForBuilding(DormBuilding building, List<CheckRecord> mergedRecords) {
        List<Floor> floors = floorRepository.findByBuildingIdOrderByFloorNumber(building.getId());
        List<Room> allRooms = roomRepository.findByBuildingId(building.getId());
        Map<String, List<Room>> roomsByFloor = allRooms.stream()
                .collect(Collectors.groupingBy(r -> r.getFloor().getId()));

        List<String> allRoomIds = allRooms.stream().map(Room::getId).toList();
        List<Student> allStudents = studentRepository.findByRoomIdInOrderByBedNumber(allRoomIds);
        Map<String, List<Student>> studentsByRoom = allStudents.stream()
                .collect(Collectors.groupingBy(s -> s.getRoom().getId()));

        BuildingBlock block = new BuildingBlock();
        block.setBuilding(building.getName());

        int totalShould = 0, totalActual = 0, totalPersonalLeave = 0, totalSickLeave = 0;
        int totalLateReturn = 0, totalMilitary = 0, totalIntern = 0;
        int totalDayStudent = 0, totalAbsent = 0, totalUnchecked = 0;
        List<BuildingBlock.FloorStat> floorStats = new ArrayList<>();

        for (Floor floor : floors) {
            List<Room> rooms = roomsByFloor.getOrDefault(floor.getId(), List.of());
            List<Student> floorStudents = new ArrayList<>();
            for (Room room : rooms) {
                floorStudents.addAll(studentsByRoom.getOrDefault(room.getId(), List.of()));
            }

            AttendanceCalculator.FloorAttendance fa = attendanceCalculator
                    .calcFloorAttendance(floor, floorStudents, mergedRecords);

            BuildingBlock.FloorStat fs = buildFloorStat(floor, fa);
            floorStats.add(fs);

            totalShould += fa.getShould();
            totalActual += fa.getActual();
            totalPersonalLeave += fa.getPersonalLeave();
            totalSickLeave += fa.getSickLeave();
            totalLateReturn += fa.getLateReturn();
            totalMilitary += fa.getMilitary();
            totalIntern += fa.getIntern();
            totalDayStudent += fa.getDayStudent();
            totalAbsent += fa.getAbsent();
            totalUnchecked += fa.getUnchecked();
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("should", totalShould);
        summary.put("actual", totalActual);
        summary.put("leave", totalPersonalLeave + totalSickLeave);
        summary.put("personalLeave", totalPersonalLeave);
        summary.put("sickLeave", totalSickLeave);
        summary.put("lateReturn", totalLateReturn);
        summary.put("military", totalMilitary);
        summary.put("intern", totalIntern);
        summary.put("dayStudent", totalDayStudent);
        summary.put("absent", totalAbsent);
        summary.put("unchecked", totalUnchecked);
        block.setSummary(summary);
        block.setFloors(floorStats);

        return block;
    }

    private BuildingBlock.FloorStat buildFloorStat(Floor floor, AttendanceCalculator.FloorAttendance fa) {
        BuildingBlock.FloorStat fs = new BuildingBlock.FloorStat();
        fs.setFloor(String.valueOf(floor.getFloorNumber()));
        fs.setShould(fa.getShould());
        fs.setActual(fa.getActual());
        fs.setPersonalLeave(fa.getPersonalLeave());
        fs.setSickLeave(fa.getSickLeave());
        fs.setLeave(fa.getPersonalLeave() + fa.getSickLeave());
        fs.setLateReturn(fa.getLateReturn());
        fs.setMilitary(fa.getMilitary());
        fs.setIntern(fa.getIntern());
        fs.setDayStudent(fa.getDayStudent());
        fs.setUnchecked(fa.getUnchecked());
        fs.setNewShould(fa.getNewShould());
        fs.setNewActual(fa.getNewActual());
        fs.setNewPersonalLeave(fa.getNewPersonalLeave());
        fs.setNewSickLeave(fa.getNewSickLeave());
        fs.setNewLeave(fa.getNewPersonalLeave() + fa.getNewSickLeave());
        fs.setNewLateReturn(fa.getNewLateReturn());
        fs.setNewMilitary(fa.getNewMilitary());
        fs.setNewIntern(fa.getNewIntern());
        fs.setNewDayStudent(fa.getNewDayStudent());
        fs.setNewLongSickLeave(fa.getNewLongSickLeave());
        fs.setNewAbsent(fa.getNewAbsent());
        return fs;
    }

    private String calculateDateRange(List<Task> tasks) {
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

    private java.util.Set<String> parseTaskRoomNumbers(Task task) {
        String roomNumbers = task.getRoomNumbers();
        if (roomNumbers == null || roomNumbers.isBlank()) {
            return null;
        }
        return java.util.Arrays.stream(roomNumbers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
    }

    @lombok.Data
    public static class ReportData {
        private String date;
        private String college;
        private List<BuildingBlock> blocks;
    }

    @lombok.Data
    public static class BuildingBlock {
        private String building;
        private Map<String, Object> summary;
        private List<FloorStat> floors;

        @lombok.Data
        public static class FloorStat {
            private String floor;
            private int should;
            private int actual;
            private int personalLeave;
            private int sickLeave;
            private int leave;
            private int lateReturn;
            private int military;
            private int intern;
            private int dayStudent;
            private int unchecked;
            private int newShould;
            private int newActual;
            private int newPersonalLeave;
            private int newSickLeave;
            private int newLeave;
            private int newLateReturn;
            private int newMilitary;
            private int newIntern;
            private int newDayStudent;
            private int newLongSickLeave;
            private int newAbsent;
        }
    }
}