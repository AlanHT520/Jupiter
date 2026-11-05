package net.alan.jupiter.domain.repository;

import net.alan.jupiter.domain.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, String>,
        JpaSpecificationExecutor<Student> {

    Optional<Student> findByStudentNo(String studentNo);

    boolean existsByStudentNo(String studentNo);

    List<Student> findByStudentNoIn(List<String> studentNos);

    List<Student> findByRoomIdOrderByBedNumber(String roomId);

    List<Student> findByRoomIdInOrderByBedNumber(List<String> roomIds);

    List<Student> findByBuildingId(String buildingId);

    List<Student> findByClassInfoId(String classId);

    List<Student> findByClassInfoIdIn(List<String> classIds);

    List<Student> findByClassName(String className);

    List<Student> findByClassNameContaining(String className);

    List<Student> findByIsNewStudentTrue();

    long countByBuildingId(String buildingId);

    long countByRoomId(String roomId);

    long countByIsActiveTrue();

    @Query("SELECT s FROM Student s WHERE "
            + "(:buildingId IS NULL OR s.building.id = :buildingId) AND "
            + "(:className IS NULL OR s.className LIKE %:className%) AND "
            + "(:keyword IS NULL OR s.name LIKE %:keyword% OR s.studentNo LIKE %:keyword%)")
    List<Student> searchStudents(@Param("keyword") String keyword,
                                  @Param("buildingId") String buildingId,
                                  @Param("className") String className);

    @Query("SELECT DISTINCT s.classInfo.id FROM Student s WHERE s.classInfo IS NOT NULL")
    List<String> findDistinctClassIds();

    @Query("SELECT DISTINCT s.classInfo.id FROM Student s WHERE s.classInfo IS NOT NULL AND s.building.id IN :buildingIds")
    List<String> findDistinctClassIdsByBuildingIds(@Param("buildingIds") List<String> buildingIds);
}