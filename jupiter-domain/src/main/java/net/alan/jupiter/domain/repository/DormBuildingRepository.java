package net.alan.jupiter.domain.repository;

import net.alan.jupiter.domain.model.DormBuilding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DormBuildingRepository extends JpaRepository<DormBuilding, String> {

    Optional<DormBuilding> findByName(String name);

    List<DormBuilding> findByIsActiveTrueOrderBySortOrder();

    List<DormBuilding> findByIsActiveTrue();

    boolean existsByName(String name);

    long countByIsActiveTrue();
}