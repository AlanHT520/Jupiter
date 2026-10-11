package net.alan.jupiter.web.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.application.check.SubmitRoomCheckService;
import net.alan.jupiter.common.result.ApiResult;
import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.web.dto.CheckRecordDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理端-查寝记录", description = "管理端查寝记录查询接口")
@RestController
@RequestMapping("/admin-api/check-records")
@RequiredArgsConstructor
public class AdminCheckRecordController {

    private final SubmitRoomCheckService submitRoomCheckService;
    private final net.alan.jupiter.web.mapper.CheckRecordMapper checkRecordMapper;

    @Operation(summary = "查询任务查寝记录")
    @GetMapping
    public ApiResult<List<CheckRecordDto>> listCheckRecords(
            @RequestParam String taskId,
            @RequestParam(required = false, defaultValue = "false") boolean abnormalOnly) {

        List<CheckRecord> records = submitRoomCheckService.getTaskCheckRecords(taskId);

        List<CheckRecordDto> result = records.stream()
                .filter(r -> !abnormalOnly
                        || r.getCheckStatus() != null
                        && r.getCheckStatus() != CheckStatus.PRESENT)
                .map(checkRecordMapper::toDto)
                .toList();

        return ApiResult.success(result);
    }

    @Operation(summary = "查询房间查寝记录")
    @GetMapping("/room")
    public ApiResult<List<CheckRecordDto>> getRoomCheckRecords(
            @RequestParam String taskId,
            @RequestParam String roomId) {

        List<CheckRecord> records = submitRoomCheckService.getRoomCheckRecords(taskId, roomId);

        List<CheckRecordDto> result = records.stream()
                .map(checkRecordMapper::toDto)
                .toList();

        return ApiResult.success(result);
    }
}