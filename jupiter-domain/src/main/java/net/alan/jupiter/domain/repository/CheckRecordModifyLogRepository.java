package net.alan.jupiter.domain.repository;

import net.alan.jupiter.domain.model.CheckRecordModifyLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckRecordModifyLogRepository extends JpaRepository<CheckRecordModifyLog, String> {

    List<CheckRecordModifyLog> findByCheckRecordIdOrderByModifiedAtDesc(String checkRecordId);

    List<CheckRecordModifyLog> findByCheckRecordIdInOrderByModifiedAtDesc(List<String> checkRecordIds);
}