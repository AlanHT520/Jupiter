package net.alan.jupiter.domain.repository;

import net.alan.jupiter.domain.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupRepository extends JpaRepository<Group, String> {

    List<Group> findByBuildingId(String buildingId);
}