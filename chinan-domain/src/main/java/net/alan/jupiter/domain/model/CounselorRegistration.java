package net.alan.jupiter.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "counselor_registration")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounselorRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 20, nullable = false)
    private String name;

    @Column(name = "student_no", length = 20, nullable = false)
    private String studentNo;

    @Column(name = "class_name", length = 50, nullable = false)
    private String className;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "password", length = 200, nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private RegistrationStatus status;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewer_name", length = 50)
    private String reviewerName;

    public enum RegistrationStatus {
        PENDING, APPROVED, REJECTED
    }
}