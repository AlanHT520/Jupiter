package net.alan.jupiter.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.application.check.QueryFloorService;
import net.alan.jupiter.application.task.QueryTaskService;
import net.alan.jupiter.common.result.ApiResult;
import net.alan.jupiter.common.result.PageResult;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.web.dto.TaskDto;
import net.alan.jupiter.web.dto.TaskOverviewDto;
import net.alan.jupiter.web.mapper.TaskMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "App-任务", description = "查宿者任务接口")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class AppTaskController {

    private final QueryTaskService queryTaskService;
    private final TaskMapper taskMapper;
    private final QueryFloorService queryFloorService;

    @Operation(summary = "我的任务列表")
    @GetMapping("/my")
    public ApiResult<PageResult<TaskDto>> listMyTasks(Pageable pageable) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        Page<Task> page = queryTaskService.listTasksByCounselor(userId, pageable);

        List<TaskDto> dtos = page.getContent().stream()
                .map(taskMapper::toDto)
                .toList();
        return ApiResult.success(PageResult.of(dtos, page.getTotalElements(),
                page.getNumber(), page.getSize()));
    }

    @Operation(summary = "任务概览（含楼层房间状态）")
    @GetMapping("/{taskId}/overview")
    public ApiResult<TaskOverviewDto> getTaskOverview(@PathVariable String taskId) {
        Task task = queryTaskService.getTask(taskId);
        if (task.getFloor() == null) {
            throw new net.alan.jupiter.common.exception.BizException(
                    net.alan.jupiter.common.exception.ErrorCode.BAD_REQUEST, "任务未关联楼层");
        }
        TaskDto taskDto = taskMapper.toDto(task);

        QueryFloorService.FloorDetail floorDetail =
                queryFloorService.getFloorDetail(taskId, task.getFloor().getId());

        List<TaskOverviewDto.RoomInfo> roomInfos = floorDetail.getRooms().stream()
                .map(r -> TaskOverviewDto.RoomInfo.builder()
                        .roomId(r.getRoomId())
                        .roomNumber(r.getRoomNumber())
                        .studentCount(r.getStudentCount())
                        .checked(r.isChecked())
                        .build())
                .collect(Collectors.toList());

        TaskOverviewDto.FloorInfo floorInfo = TaskOverviewDto.FloorInfo.builder()
                .floorId(floorDetail.getFloorId())
                .floorNumber(floorDetail.getFloorNumber())
                .floorName(floorDetail.getFloorName())
                .roomCount(floorDetail.getRoomCount())
                .checkedRoomCount(floorDetail.getCheckedRoomCount())
                .rooms(roomInfos)
                .build();

        TaskOverviewDto overview = TaskOverviewDto.builder()
                .task(taskDto)
                .floor(floorInfo)
                .build();

        return ApiResult.success(overview);
    }
}