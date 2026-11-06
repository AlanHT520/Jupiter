package net.alan.jupiter.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "counselor")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Counselor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String phone;

    @Column(name = "student_no", unique = true)
    private String studentNo;

    @Column(name = "class_name")
    private String className;

    private String gender;

    private String department;

    @Column(name = "username", unique = true)
    private String username;

    private String password;

    /** 可还原的明文密码，仅用于管理员导出账户；登录仍校验上面的 BCrypt 哈希。 */
    @Column(name = "password_plain")
    private String passwordPlain;

    @Column(name = "role")
    private String role;

    @Column(name = "token_version")
    @Builder.Default
    private Long tokenVersion = 0L;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return name != null ? name : (username != null ? username : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Counselor that)) return false;
        return id != null && java.util.Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}