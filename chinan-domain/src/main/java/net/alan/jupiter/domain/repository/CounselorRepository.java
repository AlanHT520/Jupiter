package net.alan.jupiter.domain.repository;

import net.alan.jupiter.domain.model.Counselor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CounselorRepository extends JpaRepository<Counselor, String> {

    Optional<Counselor> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<Counselor> findByPhone(String phone);

    Optional<Counselor> findByStudentNo(String studentNo);

    Optional<Counselor> findFirstByName(String name);

    boolean existsByStudentNo(String studentNo);

    List<Counselor> findByStudentNoIn(List<String> studentNos);

    List<Counselor> findByIsActiveTrueAndRoleIgnoreCase(String role);

    List<Counselor> findByRoleNotIgnoreCase(String role);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Counselor c WHERE c.role <> 'ADMIN'")
    int deleteAllNonAdmin();
}