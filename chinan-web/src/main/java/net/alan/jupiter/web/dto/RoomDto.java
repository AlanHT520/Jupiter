package net.alan.jupiter.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomDto {

    private String id;
    private String roomNumber;
    private String floorId;
    private Integer floorNumber;
    private String buildingId;
    private String buildingName;
    private Integer bedCount;
    private Integer occupiedCount;
    private Boolean checked;
}