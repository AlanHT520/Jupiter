package net.alan.jupiter.application.config;

import lombok.RequiredArgsConstructor;
import net.alan.jupiter.domain.model.SystemConfig;
import net.alan.jupiter.domain.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository repo;

    public boolean isRegistrationEnabled() {
        return repo.isRegistrationEnabled();
    }

    @Transactional
    public void setRegistrationEnabled(boolean enabled) {
        SystemConfig cfg = repo.findById("registration.enabled")
                .orElseGet(() -> {
                    SystemConfig c = new SystemConfig();
                    c.setKey("registration.enabled");
                    return c;
                });
        cfg.setValue(String.valueOf(enabled));
        cfg.setUpdatedAt(LocalDateTime.now());
        repo.save(cfg);
    }
}