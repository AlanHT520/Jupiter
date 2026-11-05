package net.alan.jupiter.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitRoomRequest {

    private String taskId;
    private String roomId;
    private List<StudentCheckItem> students;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentCheckItem {
        private String studentId;
        private String checkStatus;
        private String presetStatus;
        private Boolean presetOverridden;
        private String statusSource;
        private String remark;
        private String proofFile;
    }
}