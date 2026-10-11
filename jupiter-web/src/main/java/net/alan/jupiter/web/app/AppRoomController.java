package net.alan.jupiter.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.application.check.QueryFloorService;
import net.alan.jupiter.application.check.SubmitRoomCheckService;
import net.alan.jupiter.common.result.ApiResult;
import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.domain.model.DormBuilding;
import net.alan.jupiter.domain.model.Floor;
import net.alan.jupiter.domain.model.Room;
import net.alan.jupiter.domain.model.Student;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.domain.repository.CheckRecordRepository;
import net.alan.jupiter.domain.repository.DormBuildingRepository;
import net.alan.jupiter.domain.repository.FloorRepository;
import net.alan.jupiter.domain.repository.TaskRepository;
import net.alan.jupiter.domain.service.CheckRuleService;
import net.alan.jupiter.web.dto.RoomDto;
import net.alan.jupiter.web.dto.StudentDto;
import net.alan.jupiter.web.dto.SubmitRoomRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "APP-房间", description = "PE端房间与查寝接口")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class AppRoomController {

    private final QueryFloorService queryFloorService;
    private final SubmitRoomCheckService submitRoomCheckService;
    private final CheckRecordRepository checkRecordRepository;
    private final DormBuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final CheckRuleService checkRuleService;
    private final TaskRepository taskRepository;
    private final net.alan.jupiter.web.mapper.StudentMapper studentMapper;

    @Operation(summary = "获取所有活跃楼栋")
    @GetMapping("/buildings")
    public ApiResult<List<Map<String, Object>>> getBuildings() {
        List<DormBuilding> buildings = buildingRepository.findByIsActiveTrueOrderBySortOrder();
        List<Map<String, Object>> result = buildings.stream().map(b -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", b.getId());
            map.put("name", b.getName());
            return map;
        }).collect(Collectors.toList());
        return ApiResult.success(result);
    }

    @Operation(summary = "获取楼栋的楼层列表")
    @GetMapping("/buildings/{buildingId}/floors")
    public ApiResult<List<Map<String, Object>>> getBuildingFloors(
            @PathVariable String buildingId) {
        List<Floor> floors = floorRepository.findByBuildingIdOrderByFloorNumber(buildingId);
        List<Map<String, Object>> result = floors.stream().map(f -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", f.getId());
            map.put("floorNumber", f.getFloorNumber());
            map.put("buildingId", f.getBuilding() != null ? f.getBuilding().getId() : null);
            map.put("buildingName", f.getBuilding() != null ? f.getBuilding().getName() : null);
            return map;
        }).collect(Collectors.toList());
        return ApiResult.success(result);
    }

    @Operation(summary = "获取楼层房间列表")
    @GetMapping("/floors/{floorId}")
    public ApiResult<List<RoomDto>> getFloorRooms(
            @PathVariable String floorId,
            @RequestParam(required = false) String taskId) {
        String roomNumbers = getTaskRoomNumbers(taskId);
        List<Room> rooms = queryFloorService.getRoomsByFloor(floorId, roomNumbers);
        Set<String> checkedRoomIds = new HashSet<>();
        if (taskId != null) {
            checkedRoomIds = new HashSet<>(
                    checkRecordRepository.findCheckedRoomIdsByTaskId(taskId));
        }
        final Set<String> finalCheckedRoomIds = checkedRoomIds;
        List<RoomDto> dtos = rooms.stream().map(room -> {
            return RoomDto.builder()
                    .id(room.getId())
                    .roomNumber(room.getRoomNumber())
                    .floorId(floorId)
                    .buildingId(room.getBuilding() != null ? room.getBuilding().getId() : null)
                    .bedCount(room.getBedCount())
                    .occupiedCount(room.getOccupiedCount())
                    .checked(finalCheckedRoomIds.contains(room.getId()))
                    .build();
        }).collect(Collectors.toList());
        return ApiResult.success(dtos);
    }

    @Operation(summary = "获取房间学生列表（含预设状态）")
    @GetMapping("/{roomId}/students")
    public ApiResult<List<StudentDto>> getRoomStudents(
            @PathVariable String roomId,
            @RequestParam(required = false) String taskId) {
        List<Student> students = queryFloorService.getStudentsByRoom(roomId);

        Map<String, CheckRecord> recordMap = Map.of();
        if (taskId != null) {
            List<CheckRecord> records = submitRoomCheckService
                    .getRoomCheckRecords(taskId, roomId);
            recordMap = records.stream()
                    .filter(r -> r.getStudent() != null)
                    .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r, (a, b) -> a));
        }

        Map<String, CheckRecord> finalRecordMap = recordMap;
        List<StudentDto> dtos = students.stream().map(s -> {
            CheckRecord record = finalRecordMap.get(s.getId());
            CheckStatus defaultStatus = checkRuleService.determinePresetStatus(s);
            return StudentDto.builder()
                    .id(s.getId())
                    .studentNo(s.getStudentNo())
                    .name(s.getName())
                    .gender(s.getGender())
                    .className(s.getClassName())
                    .roomId(roomId)
                    .roomNumber(s.getRoomNumber())
                    .bedNumber(s.getBedNumber())
                    .isNewStudent(s.getIsNewStudent())
                    .longSickLeaveStartDate(s.getLongSickLeaveStartDate())
                    .longSickLeaveEndDate(s.getLongSickLeaveEndDate())
                    .militaryStartDate(s.getMilitaryStartDate())
                    .militaryEndDate(s.getMilitaryEndDate())
                    .internStartDate(s.getInternStartDate())
                    .internEndDate(s.getInternEndDate())
                    .dayStudentStartDate(s.getDayStudentStartDate())
                    .dayStudentEndDate(s.getDayStudentEndDate())
                    .checkStatus(record != null && record.getCheckStatus() != null
                            ? record.getCheckStatus().name() : null)
                    .statusCode(record != null && record.getCheckStatus() != null
                                    ? record.getCheckStatus().getCode() : null)
                    .presetStatus(record != null && record.getPresetStatus() != null
                            ? record.getPresetStatus().name() : null)
                    .statusSource(record != null ? record.getStatusSource() : null)
                    .remark(record != null ? record.getRemark() : null)
                    .defaultStatus(defaultStatus != null ? defaultStatus.name() : null)
                    .lateReturnStatus(record != null && record.getCheckStatus() == CheckStatus.LATE_RETURN
                            ? CheckStatus.LATE_RETURN.name() : null)
                    .build();
        }).collect(Collectors.toList());
        return ApiResult.success(dtos);
    }

    @Operation(summary = "提交房间查寝结果")
    @PostMapping("/{roomId}/check")
    public ApiResult<Void> submitRoomCheck(
            @PathVariable String roomId,
            @Valid @RequestBody SubmitRoomRequest request) {
        validateAndSubmit(request, roomId);
        return ApiResult.success();
    }

    @Operation(summary = "批量提交查寝结果")
    @PostMapping("/batch-check")
    public ApiResult<Void> batchSubmitCheck(
            @Valid @RequestBody java.util.List<SubmitRoomRequest> requestList) {
        if (requestList == null || requestList.isEmpty()) {
            throw new net.alan.jupiter.common.exception.BizException(
                    net.alan.jupiter.common.exception.ErrorCode.BAD_REQUEST, "提交数据不能为空");
        }
        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
        java.util.List<SubmitRoomCheckService.BatchRoomSubmission> submissions = new ArrayList<>();
        for (SubmitRoomRequest request : requestList) {
            if (request.getTaskId() == null || request.getTaskId().isBlank()) {
                throw new net.alan.jupiter.common.exception.BizException(
                        net.alan.jupiter.common.exception.ErrorCode.BAD_REQUEST, "任务ID不能为空");
            }
            if (request.getStudents() == null || request.getStudents().isEmpty()) {
                throw new net.alan.jupiter.common.exception.BizException(
                        net.alan.jupiter.common.exception.ErrorCode.BAD_REQUEST, "房间提交数据不能为空");
            }
            java.util.List<SubmitRoomCheckService.StudentCheckItem> items = new ArrayList<>();
            for (SubmitRoomRequest.StudentCheckItem item : request.getStudents()) {
                items.add(SubmitRoomCheckService.StudentCheckItem.builder()
                        .studentId(item.getStudentId())
                        .checkStatus(CheckStatus.valueOf(item.getCheckStatus()))
                        .presetStatus(item.getPresetStatus() != null
                                ? CheckStatus.valueOf(item.getPresetStatus()) : null)
                        .presetOverridden(item.getPresetOverridden())
                        .statusSource(item.getStatusSource())
                        .remark(item.getRemark())
                        .proofFile(item.getProofFile())
                        .build());
            }
            submissions.add(SubmitRoomCheckService.BatchRoomSubmission.builder()
                    .taskId(request.getTaskId())
                    .roomId(request.getRoomId())
                    .students(items)
                    .build());
        }
        submitRoomCheckService.batchSubmitRoomCheck(submissions, currentUser);
        return ApiResult.success();
    }

    private void validateAndSubmit(SubmitRoomRequest request, String roomId) {
        if (request.getTaskId() == null || request.getTaskId().isBlank()) {
            throw new net.alan.jupiter.common.exception.BizException(
                    net.alan.jupiter.common.exception.ErrorCode.BAD_REQUEST, "任务ID不能为空");
        }
        if (request.getStudents() == null || request.getStudents().isEmpty()) {
            throw new net.alan.jupiter.common.exception.BizException(
                    net.alan.jupiter.common.exception.ErrorCode.BAD_REQUEST, "提交数据不能为空");
        }
        List<SubmitRoomCheckService.StudentCheckItem> items = new ArrayList<>();
        for (SubmitRoomRequest.StudentCheckItem item : request.getStudents()) {
            items.add(SubmitRoomCheckService.StudentCheckItem.builder()
                    .studentId(item.getStudentId())
                    .checkStatus(CheckStatus.valueOf(item.getCheckStatus()))
                    .presetStatus(item.getPresetStatus() != null
                            ? CheckStatus.valueOf(item.getPresetStatus()) : null)
                    .presetOverridden(item.getPresetOverridden())
                    .statusSource(item.getStatusSource())
                    .remark(item.getRemark())
                    .proofFile(item.getProofFile())
                    .build());
        }
        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
        submitRoomCheckService.submitRoomCheck(
                request.getTaskId(), roomId, items, currentUser);
    }

    private String getTaskRoomNumbers(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return taskRepository.findById(taskId)
                .map(Task::getRoomNumbers)
                .orElse(null);
    }
}