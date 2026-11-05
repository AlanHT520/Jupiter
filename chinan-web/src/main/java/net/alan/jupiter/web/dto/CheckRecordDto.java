package net.alan.jupiter.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 查寝记录 DTO。
 * 用于管理端查询查寝记录时返回，避免直接返回 Map 导致的类型推断问题。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckRecordDto {

    private String id;
    private String taskId;
    private String roomId;
    private String roomNumber;
    private String studentId;
    private String studentNo;
    private String studentName;
    private String checkStatus;
    private String statusSource;
    private String remark;
    private String proofFile;
    private String submittedBy;
    private LocalDateTime submittedAt;
}