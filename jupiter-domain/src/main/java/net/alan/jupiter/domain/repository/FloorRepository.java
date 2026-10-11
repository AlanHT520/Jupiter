package net.alan.jupiter.domain.repository;

import net.alan.jupiter.domain.model.Floor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FloorRepository extends JpaRepository<Floor, String> {

    List<Floor> findByBuildingIdOrderByFloorNumber(String buildingId);

    List<Floor> findByBuildingId(String buildingId);

    Optional<Floor> findByBuildingIdAndFloorNumber(String buildingId, Integer floorNumber);

    long countByBuildingId(String buildingId);
}