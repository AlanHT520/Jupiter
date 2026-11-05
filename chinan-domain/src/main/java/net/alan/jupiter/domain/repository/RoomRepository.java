package net.alan.jupiter.domain.repository;

import net.alan.jupiter.domain.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, String> {

    List<Room> findByFloorIdOrderByRoomNumber(String floorId);

    List<Room> findByBuildingId(String buildingId);

    Optional<Room> findByRoomNumberAndBuildingId(String roomNumber, String buildingId);

    List<Room> findByRoomNumberInAndBuildingId(List<String> roomNumbers, String buildingId);

    boolean existsByRoomNumber(String roomNumber);

    int countByBuildingIdAndIsActiveTrue(String buildingId);

    long countByFloorId(String floorId);

    long countByFloorIdAndIsActiveTrue(String floorId);

    long countByIsActiveTrue();
}