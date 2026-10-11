package net.alan.jupiter.application.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.enums.TaskStatus;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.domain.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryTaskService {

    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public Task getTask(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.TASK_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page<Task> listTasks(String buildingId, TaskStatus status, LocalDate startDate,
                                 LocalDate endDate, Pageable pageable) {
        boolean hasBuilding = buildingId != null && !buildingId.isBlank();
        boolean hasStatus = status != null;
        boolean hasDateRange = startDate != null && endDate != null;

        if (hasBuilding && hasStatus && hasDateRange) {
            return taskRepository.findByBuildingIdAndStatusAndTaskDateBetween(
                    buildingId, status, startDate, endDate, pageable);
        }
        if (hasBuilding && hasDateRange) {
            return taskRepository.findByBuildingIdAndTaskDateBetween(
                    buildingId, startDate, endDate, pageable);
        }
        if (hasStatus && hasDateRange) {
            return taskRepository.findByStatusAndTaskDateBetween(
                    status, startDate, endDate, pageable);
        }
        if (hasDateRange) {
            return taskRepository.findByTaskDateBetween(startDate, endDate, pageable);
        }
        if (hasBuilding && hasStatus) {
            return taskRepository.findByBuildingIdAndStatus(buildingId, status, pageable);
        }
        if (hasBuilding) {
            return taskRepository.findByBuildingId(buildingId, pageable);
        }
        if (hasStatus) {
            return taskRepository.findByStatus(status, pageable);
        }
        return taskRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Task> getPendingTasks() {
        return List.copyOf(taskRepository.findByStatusAndIsDeleteFalse(TaskStatus.PENDING));
    }

    @Transactional(readOnly = true)
    public List<Task> getInProgressTasks() {
        return List.copyOf(taskRepository.findByStatusAndIsDeleteFalse(TaskStatus.IN_PROGRESS));
    }

    @Transactional(readOnly = true)
    public Page<Task> listTasksByCounselor(String counselorId, Pageable pageable) {
        return taskRepository.findByAssignedCounselorIdOrUnassigned(counselorId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Task> listTasksByCounselorAndStatus(String counselorId, TaskStatus status, Pageable pageable) {
        return taskRepository.findByAssignedCounselorIdOrUnassignedAndStatus(counselorId, status, pageable);
    }
}