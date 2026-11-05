package net.alan.jupiter.application.check;

import lombok.Getter;
import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.model.CheckRecord;

import java.util.List;
import java.util.Map;

@Getter
public class CheckRecordSubmittedEvent {

    private final String taskId;
    private final String roomId;
    private final List<CheckRecord> records;
    /**
     * 提交前的字段快照（按学生 ID 索引）。
     * 必须在实体被修改之前采集，否则旧值会被覆盖，变更日志将永远为空。
     */
    private final Map<String, RecordSnapshot> beforeSnapshots;
    private final String submittedBy;

    public CheckRecordSubmittedEvent(String taskId, String roomId, List<CheckRecord> records,
                                     Map<String, RecordSnapshot> beforeSnapshots, String submittedBy) {
        this.taskId = taskId;
        this.roomId = roomId;
        this.records = records;
        this.beforeSnapshots = beforeSnapshots;
        this.submittedBy = submittedBy;
    }

    /** 单条查寝记录在本次提交前的字段快照。 */
    public record RecordSnapshot(CheckStatus checkStatus, String remark, String proofFile) {
    }
}
