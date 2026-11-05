package net.alan.jupiter.domain.repository;

import net.alan.jupiter.domain.model.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {

    default boolean isRegistrationEnabled() {
        return findById("registration.enabled")
                .map(c -> "true".equalsIgnoreCase(c.getValue()))
                .orElse(false);
    }
}