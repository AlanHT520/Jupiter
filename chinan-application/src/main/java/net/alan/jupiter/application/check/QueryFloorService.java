package net.alan.jupiter.application.check;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.model.*;
import net.alan.jupiter.domain.repository.*;
import net.alan.jupiter.domain.service.CheckRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryFloorService {

    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;
    private final StudentRepository studentRepository;
    private final DormBuildingRepository buildingRepository;
    private final CheckRecordRepository checkRecordRepository;
    private final TaskRepository taskRepository;
    private final CheckRuleService checkRuleService;

    public List<Floor> getFloorsByBuilding(String buildingId) {
        DormBuilding building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new BizException(ErrorCode.BUILDING_NOT_FOUND));
        return floorRepository.findByBuildingIdOrderByFloorNumber(buildingId);
    }

    public List<Room> getRoomsByFloor(String floorId) {
        return getRoomsByFloor(floorId, null);
    }

    public List<Room> getRoomsByFloor(String floorId, String roomNumbers) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new BizException(ErrorCode.FLOOR_NOT_FOUND));
        List<Room> allRooms = roomRepository.findByFloorIdOrderByRoomNumber(floorId);
        return filterByRoomNumbers(allRooms, roomNumbers);
    }

    @Transactional(readOnly = true)
    public List<Student> getStudentsByRoom(String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BizException(ErrorCode.ROOM_NOT_FOUND));
        List<Student> allStudents = studentRepository.findByRoomIdOrderByBedNumber(roomId);
        return filterCheckableStudents(allStudents);
    }

    private List<Student> filterCheckableStudents(List<Student> students) {
        return students.stream()
                .filter(s -> {
                    CheckStatus preset = checkRuleService.determinePresetStatus(s);
                    return preset != CheckStatus.DAY_STUDENT
                            && preset != CheckStatus.LONG_SICK_LEAVE;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FloorDetail getFloorDetail(String taskId, String floorId) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new BizException(ErrorCode.FLOOR_NOT_FOUND));

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.TASK_NOT_FOUND));
        List<Room> rooms = filterByRoomNumbers(
                roomRepository.findByFloorIdOrderByRoomNumber(floorId),
                task.getRoomNumbers());

        List<String> roomIds = rooms.stream().map(Room::getId).toList();
        List<Student> allStudents = filterCheckableStudents(
                studentRepository.findByRoomIdInOrderByBedNumber(roomIds));
        Map<String, List<Student>> studentsByRoom = allStudents.stream()
                .collect(Collectors.groupingBy(s -> s.getRoom().getId()));

        List<CheckRecord> allRecords = checkRecordRepository.findByTaskIdAndRoomIdIn(taskId, roomIds);
        Set<String> checkedRoomIds = allRecords.stream()
                .map(r -> r.getRoom().getId())
                .collect(Collectors.toSet());

        List<RoomDetail> roomDetails = rooms.stream().map(room -> {
            List<Student> students = studentsByRoom.getOrDefault(room.getId(), List.of());
            return RoomDetail.builder()
                    .roomId(room.getId())
                    .roomNumber(room.getRoomNumber())
                    .studentCount(students.size())
                    .checked(checkedRoomIds.contains(room.getId()))
                    .students(students)
                    .build();
        }).collect(Collectors.toList());

        return FloorDetail.builder()
                .floorId(floor.getId())
                .floorNumber(floor.getFloorNumber())
                .floorName(floor.getFloorName())
                .roomCount(rooms.size())
                .checkedRoomCount((int) roomDetails.stream().filter(RoomDetail::isChecked).count())
                .rooms(roomDetails)
                .build();
    }

    private List<Room> filterByRoomNumbers(List<Room> rooms, String roomNumbers) {
        if (roomNumbers == null || roomNumbers.isBlank()) {
            return rooms;
        }
        Set<String> allowed = Arrays.stream(roomNumbers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        if (allowed.isEmpty()) {
            return rooms;
        }
        return rooms.stream()
                .filter(r -> allowed.contains(r.getRoomNumber()))
                .collect(Collectors.toList());
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FloorDetail {
        private String floorId;
        private Integer floorNumber;
        private String floorName;
        private int roomCount;
        private int checkedRoomCount;
        private List<RoomDetail> rooms;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RoomDetail {
        private String roomId;
        private String roomNumber;
        private int studentCount;
        private boolean checked;
        private List<Student> students;
    }
}