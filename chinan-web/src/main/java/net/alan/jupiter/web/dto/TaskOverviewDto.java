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
public class TaskOverviewDto {

    private TaskDto task;
    private FloorInfo floor;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FloorInfo {
        private String floorId;
        private Integer floorNumber;
        private String floorName;
        private int roomCount;
        private int checkedRoomCount;
        private List<RoomInfo> rooms;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomInfo {
        private String roomId;
        private String roomNumber;
        private int studentCount;
        private boolean checked;
    }
}