package net.alan.jupiter.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAssignRequest {

    private String title;
    private LocalDate taskDate;
    private String buildingId;
    private String floorId;
    private String roomNumbers;
    private String assignedCounselorId;
    private String deadlineTime;
}