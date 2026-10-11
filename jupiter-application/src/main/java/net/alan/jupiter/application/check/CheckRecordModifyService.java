package net.alan.jupiter.application.check;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.enums.TaskStatus;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.domain.model.CheckRecordModifyLog;
import net.alan.jupiter.domain.repository.CheckRecordModifyLogRepository;
import net.alan.jupiter.domain.repository.CheckRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckRecordModifyService {

    private final CheckRecordRepository checkRecordRepository;
    private final CheckRecordModifyLogRepository modifyLogRepository;

    @Transactional
    public CheckRecord modifyCheckRecord(String recordId, ModifyRequest request, String modifiedBy) {
        CheckRecord record = checkRecordRepository.findById(recordId)
                .orElseThrow(() -> new BizException(ErrorCode.CHECK_RECORD_NOT_FOUND));

        if (record.getTask() != null) {
            TaskStatus taskStatus = record.getTask().getStatus();
            if (taskStatus == TaskStatus.COMPLETED || taskStatus == TaskStatus.CANCELLED) {
                throw new BizException(ErrorCode.TASK_STATUS_ERROR,
                        "任务已" + (taskStatus == TaskStatus.COMPLETED ? "完成" : "取消") + "，不能修改查寝记录");
            }
        }

        if (request.getNewCheckStatus() != null
                && request.getNewCheckStatus() != record.getCheckStatus()) {
            writeLog(record.getId(), "checkStatus",
                    record.getCheckStatus() != null ? record.getCheckStatus().name() : null,
                    request.getNewCheckStatus().name(),
                    modifiedBy, request.getReason());

            record.setCheckStatus(request.getNewCheckStatus());
        }

        if (request.getNewRemark() != null
                && !request.getNewRemark().equals(record.getRemark())) {
            writeLog(record.getId(), "remark",
                    record.getRemark(),
                    request.getNewRemark(),
                    modifiedBy, request.getReason());

            record.setRemark(request.getNewRemark());
        }

        return checkRecordRepository.save(record);
    }

    private void writeLog(String checkRecordId, String fieldName,
                           String oldValue, String newValue,
                           String modifiedBy, String reason) {
        CheckRecordModifyLog logEntry = CheckRecordModifyLog.builder()
                .checkRecordId(checkRecordId)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .modifiedBy(modifiedBy)
                .modifiedAt(LocalDateTime.now())
                .reason(reason)
                .build();
        modifyLogRepository.save(logEntry);
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ModifyRequest {
        private CheckStatus newCheckStatus;
        private String newRemark;
        private String reason;
    }
}