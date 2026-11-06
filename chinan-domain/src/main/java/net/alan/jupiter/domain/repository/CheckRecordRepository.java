package net.alan.jupiter.domain.repository;

import net.alan.jupiter.domain.model.CheckRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CheckRecordRepository extends JpaRepository<CheckRecord, String> {

    List<CheckRecord> findByTaskId(String taskId);

    List<CheckRecord> findByTaskIdIn(List<String> taskIds);

    List<CheckRecord> findByTaskIdAndRoomId(String taskId, String roomId);

    List<CheckRecord> findByTaskIdAndRoomIdIn(String taskId, List<String> roomIds);

    List<CheckRecord> findByTaskIdAndStudentId(String taskId, String studentId);

    boolean existsByTaskIdAndRoomId(String taskId, String roomId);

    boolean existsByTaskIdAndStudentId(String taskId, String studentId);

    List<CheckRecord> findByTaskIdAndStudentIdIn(String taskId, List<String> studentIds);

    /**
     * 按客户端幂等键查询首次提交落库的记录（一次提交对应一行/一个学生）。
     * 重复提交时据此直接返回首次结果，避免重复写入与重复审计。
     */
    List<CheckRecord> findByClientOpId(String clientOpId);

    long countByTaskId(String taskId);

    long countByTaskIdAndRoomId(String taskId, String roomId);

    @Query("SELECT COUNT(DISTINCT cr.room.id) FROM CheckRecord cr WHERE cr.task.id = :taskId")
    long countDistinctRoomByTaskId(@Param("taskId") String taskId);

    @Query("SELECT DISTINCT cr.room.id FROM CheckRecord cr WHERE cr.task.id = :taskId")
    List<String> findCheckedRoomIdsByTaskId(@Param("taskId") String taskId);

    void deleteByStudentId(String studentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CheckRecord cr SET cr.student = null")
    void clearStudentReferences();
}