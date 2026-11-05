package net.alan.jupiter.web.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.application.check.CheckRecordModifyService;
import net.alan.jupiter.common.result.ApiResult;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.domain.model.CheckRecordModifyLog;
import net.alan.jupiter.domain.repository.CheckRecordModifyLogRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "管理端-查寝记录修改", description = "管理端修改查寝记录并留痕")
@RestController
@RequestMapping("/admin-api/check-records")
@RequiredArgsConstructor
public class AdminCheckRecordModifyController {

    private final CheckRecordModifyService modifyService;
    private final CheckRecordModifyLogRepository modifyLogRepository;

    @Operation(summary = "修改查寝记录")
    @PutMapping("/{recordId}")
    public ApiResult<ModifyResult> modifyCheckRecord(
            @PathVariable String recordId,
            @RequestBody CheckRecordModifyService.ModifyRequest request) {
        String modifiedBy = SecurityContextHolder.getContext().getAuthentication().getName();
        CheckRecord record = modifyService.modifyCheckRecord(recordId, request, modifiedBy);
        return ApiResult.success(new ModifyResult(
                record.getId(),
                record.getCheckStatus() != null ? record.getCheckStatus().name() : null,
                record.getPresetStatus() != null ? record.getPresetStatus().name() : null,
                record.getRemark(),
                record.getProofFile(),
                record.getSubmittedAt()));
    }

    /** 只返回标量字段，避免序列化懒加载关联。 */
    public record ModifyResult(String id, String checkStatus, String presetStatus,
                               String remark, String proofFile,
                               java.time.LocalDateTime submittedAt) {
    }

    @Operation(summary = "查询修改日志")
    @GetMapping("/{recordId}/modify-logs")
    public ApiResult<List<CheckRecordModifyLog>> getModifyLogs(
            @PathVariable String recordId) {
        return ApiResult.success(
                modifyLogRepository.findByCheckRecordIdOrderByModifiedAtDesc(recordId));
    }
}