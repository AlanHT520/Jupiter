package net.alan.jupiter.domain.repository;

import net.alan.jupiter.domain.model.TaskTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskTemplateRepository extends JpaRepository<TaskTemplate, String> {

    List<TaskTemplate> findAllByOrderByCreatedAtDesc();
}
