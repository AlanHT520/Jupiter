package net.alan.jupiter.application.check;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.domain.model.CheckRecordModifyLog;
import net.alan.jupiter.domain.repository.CheckRecordModifyLogRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckRecordLogListener {

    private final CheckRecordModifyLogRepository modifyLogRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCheckRecordSubmitted(CheckRecordSubmittedEvent event) {
        try {
            List<CheckRecordModifyLog> logs = buildLogChanges(
                    event.getRecords(), event.getBeforeSnapshots(), event.getSubmittedBy());
            if (!logs.isEmpty()) {
                modifyLogRepository.saveAll(logs);
            }
        } catch (Exception e) {
            // 审计日志失败不应影响已提交的查寝结果，但必须留下痕迹
            log.error("写查寝变更日志失败: taskId={}, roomId={}",
                    event.getTaskId(), event.getRoomId(), e);
        }
    }

    private List<CheckRecordModifyLog> buildLogChanges(
            List<CheckRecord> records,
            Map<String, CheckRecordSubmittedEvent.RecordSnapshot> beforeSnapshots,
            String submittedBy) {
        LocalDateTime now = LocalDateTime.now();
        List<CheckRecordModifyLog> logs = new ArrayList<>();
        for (CheckRecord record : records) {
            String studentId = record.getStudent() != null ? record.getStudent().getId() : null;
            if (studentId == null) {
                continue;
            }
            CheckRecordSubmittedEvent.RecordSnapshot before = beforeSnapshots.get(studentId);

            if (before == null) {
                logs.add(buildLog(record.getId(), "checkStatus", "(无)",
                        nameOf(record.getCheckStatus()), submittedBy, now,
                        "创建查寝记录 - " + record.getStudentName()));
                continue;
            }

            if (before.checkStatus() != record.getCheckStatus()) {
                logs.add(buildLog(record.getId(), "checkStatus",
                        nameOf(before.checkStatus()), nameOf(record.getCheckStatus()),
                        submittedBy, now, "提交更新 - " + record.getStudentName()));
            }

            String oldRemark = blankToNull(before.remark());
            String newRemark = blankToNull(record.getRemark());
            if (!java.util.Objects.equals(oldRemark, newRemark)) {
                logs.add(buildLog(record.getId(), "remark",
                        oldRemark == null ? "(无)" : oldRemark,
                        newRemark == null ? "(无)" : newRemark,
                        submittedBy, now, "提交更新 - " + record.getStudentName()));
            }

            String oldProof = hasText(before.proofFile()) ? "有证明" : "(无)";
            String newProof = hasText(record.getProofFile()) ? "有证明" : "(无)";
            if (!oldProof.equals(newProof)) {
                logs.add(buildLog(record.getId(), "证明照片", oldProof, newProof,
                        submittedBy, now, "提交更新 - " + record.getStudentName()));
            }
        }
        return logs;
    }

    private static String nameOf(Enum<?> value) {
        return value != null ? value.name() : "(无)";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private CheckRecordModifyLog buildLog(String checkRecordId, String fieldName,
                                          String oldValue, String newValue,
                                          String modifiedBy, LocalDateTime modifiedAt, String reason) {
        return CheckRecordModifyLog.builder()
                .checkRecordId(checkRecordId)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .modifiedBy(modifiedBy)
                .modifiedAt(modifiedAt)
                .reason(reason)
                .build();
    }
}
