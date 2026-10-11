package net.alan.jupiter.web.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.application.dashboard.DashboardService;
import net.alan.jupiter.application.task.AssignTaskService;
import net.alan.jupiter.application.task.QueryTaskService;
import net.alan.jupiter.common.result.ApiResult;
import net.alan.jupiter.common.result.PageResult;
import net.alan.jupiter.domain.enums.TaskStatus;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.web.dto.TaskAssignRequest;
import net.alan.jupiter.web.dto.TaskDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "管理端-任务", description = "管理端任务管理接口")
@RestController
@RequestMapping("/admin-api/tasks")
@RequiredArgsConstructor
public class AdminTaskController {

    private final AssignTaskService assignTaskService;
    private final QueryTaskService queryTaskService;

    private final DashboardService dashboardService;
    private final net.alan.jupiter.web.mapper.TaskMapper taskMapper;

    @Operation(summary = "首页统计数据")
    @GetMapping("/dashboard/stats")
    public ApiResult<DashboardService.DashboardStats> getDashboardStats() {
        return ApiResult.success(dashboardService.getStats());
    }

    @Operation(summary = "最近任务")
    @GetMapping("/dashboard/recent")
    public ApiResult<List<TaskDto>> getRecentTasks() {
        List<TaskDto> dtos = dashboardService.getRecentTasks().stream()
                .map(taskMapper::toDto)
                .toList();
        return ApiResult.success(dtos);
    }

    @Operation(summary = "创建任务")
    @PostMapping
    public ApiResult<TaskDto> createTask(@RequestBody TaskAssignRequest request) {
        LocalDateTime deadline = null;
        if (request.getDeadlineTime() != null && !request.getDeadlineTime().isBlank()) {
            try {
                deadline = LocalDateTime.parse(request.getDeadlineTime());
            } catch (Exception e) {
                throw new net.alan.jupiter.common.exception.BizException(
                        net.alan.jupiter.common.exception.ErrorCode.BAD_REQUEST,
                        "截止时间格式无效");
            }
        }
        Task task = assignTaskService.assignTask(
                request.getTitle(), request.getTaskDate(),
                request.getBuildingId(), request.getFloorId(),
                request.getRoomNumbers(),
                request.getAssignedCounselorId(), deadline);
        return ApiResult.success(taskMapper.toDto(task));
    }

    @Operation(summary = "分页查询任务")
    @GetMapping
    public ApiResult<PageResult<TaskDto>> listTasks(
            @RequestParam(required = false) String buildingId,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        TaskStatus taskStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                taskStatus = TaskStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw new net.alan.jupiter.common.exception.BizException(
                        net.alan.jupiter.common.exception.ErrorCode.BAD_REQUEST,
                        "无效的任务状态: " + status);
            }
        }
        Page<Task> page = queryTaskService.listTasks(buildingId, taskStatus,
                null, null, pageable);

        List<TaskDto> dtos = page.getContent().stream()
                .map(taskMapper::toDto)
                .toList();
        return ApiResult.success(PageResult.of(dtos, page.getTotalElements(),
                page.getNumber(), page.getSize()));
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{taskId}")
    public ApiResult<TaskDto> getTask(@PathVariable String taskId) {
        Task task = queryTaskService.getTask(taskId);
        return ApiResult.success(taskMapper.toDto(task));
    }

    @Operation(summary = "开始任务")
    @PutMapping("/{taskId}/start")
    public ApiResult<Void> startTask(@PathVariable String taskId) {
        assignTaskService.startTask(taskId);
        return ApiResult.success();
    }

    @Operation(summary = "完成任务")
    @PutMapping("/{taskId}/complete")
    public ApiResult<Void> completeTask(@PathVariable String taskId) {
        assignTaskService.completeTask(taskId);
        return ApiResult.success();
    }

    @Operation(summary = "取消任务")
    @PutMapping("/{taskId}/cancel")
    public ApiResult<Void> cancelTask(@PathVariable String taskId) {
        assignTaskService.cancelTask(taskId);
        return ApiResult.success();
    }

    @Operation(summary = "删除任务（软删除）")
    @DeleteMapping("/{taskId}")
    public ApiResult<Void> deleteTask(@PathVariable String taskId) {
        assignTaskService.softDeleteTask(taskId);
        return ApiResult.success();
    }

    @Operation(summary = "一键清除所有已完成任务")
    @DeleteMapping("/completed")
    public ApiResult<Integer> clearCompletedTasks() {
        int count = assignTaskService.clearCompletedTasks();
        return ApiResult.success(count);
    }
}