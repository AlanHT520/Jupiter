package net.alan.jupiter.domain.repository;

import net.alan.jupiter.domain.model.CounselorRegistration;
import net.alan.jupiter.domain.model.CounselorRegistration.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CounselorRegistrationRepository extends JpaRepository<CounselorRegistration, Long> {

    List<CounselorRegistration> findByStatusOrderBySubmittedAtDesc(RegistrationStatus status);

    List<CounselorRegistration> findByStatusNotOrderBySubmittedAtDesc(RegistrationStatus status);

    boolean existsByStudentNoAndStatus(String studentNo, RegistrationStatus status);
}