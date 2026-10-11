package net.alan.jupiter.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDto {

    private String id;
    private String taskNo;
    private String title;
    private String status;
    private LocalDate taskDate;
    private String buildingId;
    private String buildingName;
    private String floorId;
    private String floorNumber;
    private String roomNumbers;
    private String assignedCounselorId;
    private String assignedCounselorName;
    private Integer totalRooms;
    private Integer checkedRooms;
    private LocalDateTime deadlineTime;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    private LocalDateTime createdAt;
}