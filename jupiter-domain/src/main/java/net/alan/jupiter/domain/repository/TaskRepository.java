package net.alan.jupiter.domain.repository;

import net.alan.jupiter.domain.enums.TaskStatus;
import net.alan.jupiter.domain.model.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, String> {

    Page<Task> findByBuildingId(String buildingId, Pageable pageable);

    Page<Task> findByStatus(TaskStatus status, Pageable pageable);

    Page<Task> findByBuildingIdAndStatusAndTaskDateBetween(
            String buildingId, TaskStatus status, LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<Task> findByBuildingIdAndTaskDateBetween(
            String buildingId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<Task> findByStatusAndTaskDateBetween(
            TaskStatus status, LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<Task> findByTaskDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<Task> findByBuildingIdAndStatus(String buildingId, TaskStatus status, Pageable pageable);

    List<Task> findByStatusAndIsDeleteFalse(TaskStatus status);

    @Query("SELECT t FROM Task t WHERE t.status = :status ORDER BY t.taskDate DESC")
    List<Task> findCompletedTasks(@Param("status") TaskStatus status);

    List<Task> findByBuildingIdAndTaskDate(String buildingId, LocalDate taskDate);

    List<Task> findByTaskDate(LocalDate taskDate);

    long countByStatus(TaskStatus status);

    List<Task> findTop5ByOrderByCreatedAtDesc();

    Page<Task> findByAssignedCounselorId(String counselorId, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.assignedCounselor.id = :counselorId OR t.assignedCounselor IS NULL")
    Page<Task> findByAssignedCounselorIdOrUnassigned(@Param("counselorId") String counselorId, Pageable pageable);

    Page<Task> findByAssignedCounselorIdAndStatus(String counselorId, TaskStatus status, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE (t.assignedCounselor.id = :counselorId OR t.assignedCounselor IS NULL) AND t.status = :status")
    Page<Task> findByAssignedCounselorIdOrUnassignedAndStatus(@Param("counselorId") String counselorId,
                                                               @Param("status") TaskStatus status,
                                                               Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Task t SET t.assignedCounselor = null WHERE t.assignedCounselor.id = :counselorId")
    int clearCounselorReference(@Param("counselorId") String counselorId);
}