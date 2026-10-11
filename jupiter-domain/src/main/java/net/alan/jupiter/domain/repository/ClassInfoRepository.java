package net.alan.jupiter.domain.repository;

import net.alan.jupiter.domain.model.ClassInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClassInfoRepository extends JpaRepository<ClassInfo, String> {

    Optional<ClassInfo> findByClassCode(String classCode);

    Optional<ClassInfo> findByName(String name);
}