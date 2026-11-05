package net.alan.jupiter.application.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.common.util.IdUtils;
import net.alan.jupiter.domain.enums.TaskStatus;
import net.alan.jupiter.domain.model.*;
import net.alan.jupiter.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignTaskService {

    private final TaskRepository taskRepository;
    private final DormBuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;
    private final CounselorRepository counselorRepository;
    private final CheckRecordRepository checkRecordRepository;

    @Transactional
    public Task assignTask(String title, LocalDate taskDate, String buildingId, String floorId,
                           String roomNumbers, String assignedCounselorId, LocalDateTime deadlineTime) {
        if (title == null || title.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "任务标题不能为空");
        }
        if (taskDate == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "任务日期不能为空");
        }
        if (assignedCounselorId == null || assignedCounselorId.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请选择查宿者");
        }

        DormBuilding building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new BizException(ErrorCode.BUILDING_NOT_FOUND));

        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "楼层不存在"));

        if (!floor.getBuilding().getId().equals(buildingId)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "楼层不属于该楼栋");
        }

        int totalRooms;
        if (roomNumbers != null && !roomNumbers.isBlank()) {
            String[] rooms = roomNumbers.split(",");
            java.util.List<String> roomNumberList = new java.util.ArrayList<>();
            for (String rn : rooms) {
                String trimmed = rn.trim();
                if (!trimmed.isEmpty()) {
                    roomNumberList.add(trimmed);
                }
            }
            if (roomNumberList.isEmpty()) {
                throw new BizException(ErrorCode.BAD_REQUEST, "请至少选择一个房间");
            }

            List<Room> existingRooms = roomRepository.findByFloorIdOrderByRoomNumber(floorId);
            java.util.Set<String> existingRoomNumbers = existingRooms.stream()
                    .map(Room::getRoomNumber)
                    .collect(java.util.stream.Collectors.toSet());
            for (String rn : roomNumberList) {
                if (!existingRoomNumbers.contains(rn)) {
                    throw new BizException(ErrorCode.BAD_REQUEST,
                            "房间号 " + rn + " 不存在于楼层 " + floor.getFloorNumber());
                }
            }
            totalRooms = roomNumberList.size();
        } else {
            totalRooms = (int) roomRepository.countByFloorIdAndIsActiveTrue(floorId);
            if (totalRooms <= 0) {
                throw new BizException(ErrorCode.BAD_REQUEST, "该楼层没有房间，无法创建任务");
            }
        }

        Task.TaskBuilder taskBuilder = Task.builder()
                .taskNo(IdUtils.taskNo())
                .title(title)
                .taskDate(taskDate)
                .status(TaskStatus.PENDING)
                .building(building)
                .buildingName(building.getName())
                .floor(floor)
                .floorNumber(floor.getFloorNumber() != null ? String.valueOf(floor.getFloorNumber()) : floor.getFloorName())
                .roomNumbers(roomNumbers)
                .totalRooms(totalRooms)
                .deadlineTime(deadlineTime);

        Counselor counselor = counselorRepository.findById(assignedCounselorId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "查宿者不存在"));
        taskBuilder.assignedCounselor(counselor)
                .assignedCounselorName(counselor.getName());

        return taskRepository.save(taskBuilder.build());
    }

    @Transactional
    public void startTask(String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.TASK_NOT_FOUND));
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new BizException(ErrorCode.TASK_STATUS_ERROR);
        }
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setStartTime(LocalDateTime.now());
        taskRepository.save(task);
    }

    @Transactional
    public void completeTask(String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.TASK_NOT_FOUND));
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new BizException(ErrorCode.TASK_STATUS_ERROR, "只有进行中的任务才能完成");
        }

        Integer totalRooms = task.getTotalRooms();
        Integer checkedRooms = task.getCheckedRooms();
        if (totalRooms != null && totalRooms > 0) {
            long actual = checkRecordRepository.countDistinctRoomByTaskId(taskId);
            if (actual < totalRooms) {
                throw new BizException(ErrorCode.TASK_STATUS_ERROR,
                        String.format("尚有房间未完成查寝（已完成 %d/%d），请完成所有房间后再完成",
                                actual, totalRooms));
            }
        }

        task.setStatus(TaskStatus.COMPLETED);
        task.setFinishTime(LocalDateTime.now());
        taskRepository.save(task);
    }

    @Transactional
    public void cancelTask(String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.TASK_NOT_FOUND));
        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new BizException(ErrorCode.TASK_STATUS_ERROR, "已完成的任务不能取消");
        }
        if (task.getStatus() == TaskStatus.CANCELLED) {
            throw new BizException(ErrorCode.TASK_STATUS_ERROR, "任务已取消，不能重复取消");
        }
        task.setStatus(TaskStatus.CANCELLED);
        taskRepository.save(task);
    }

    @Transactional
    public void softDeleteTask(String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.TASK_NOT_FOUND));
        if (Boolean.TRUE.equals(task.getIsDelete())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "任务已删除，不能重复删除");
        }
        task.setIsDelete(true);
        taskRepository.save(task);
        log.info("任务 {} 已软删除", task.getTaskNo());
    }

    @Transactional
    public int clearCompletedTasks() {
        List<Task> completedTasks = taskRepository.findByStatusAndIsDeleteFalse(TaskStatus.COMPLETED);
        int count = completedTasks.size();
        for (Task task : completedTasks) {
            task.setIsDelete(true);
        }
        taskRepository.saveAll(completedTasks);
        log.info("已清除 {} 个已完成任务", count);
        return count;
    }
}