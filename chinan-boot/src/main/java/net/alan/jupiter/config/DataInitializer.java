package net.alan.jupiter.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.domain.model.Counselor;
import net.alan.jupiter.domain.model.SystemConfig;
import net.alan.jupiter.domain.repository.CounselorRepository;
import net.alan.jupiter.domain.repository.SystemConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final CounselorRepository counselorRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${Chinan.admin.default-password:}")
    private String configuredPassword;

    @Override
    public void run(String... args) {
        initAdminAccount();
        initSystemConfig();
    }

    private void initAdminAccount() {
        if (counselorRepository.findByUsername("AlanHT520").isEmpty()) {
            String adminPassword = (configuredPassword != null && !configuredPassword.isBlank())
                    ? configuredPassword
                    : generateRandomPassword();
            Counselor admin = Counselor.builder()
                    .username("AlanHT520")
                    .password(passwordEncoder.encode(adminPassword))
                    .name("Alan")
                    .role("ADMIN")
                    .isActive(true)
                    .build();
            counselorRepository.save(admin);
            if (configuredPassword == null || configuredPassword.isBlank()) {
                log.warn("========================================");
                log.warn("  未配置管理员密码，已生成随机密码: {}", adminPassword);
                log.warn("  请在 application.yml 中设置:");
                log.warn("  Chinan.admin.default-password: {}", adminPassword);
                log.warn("========================================");
            } else {
                log.info("已创建默认管理员: AlanHT520");
            }
        }
    }

    private void initSystemConfig() {
        if (systemConfigRepository.findById("registration.enabled").isEmpty()) {
            SystemConfig cfg = new SystemConfig();
            cfg.setKey("registration.enabled");
            cfg.setValue("false");
            systemConfigRepository.save(cfg);
            log.info("初始化系统配置: registration.enabled = false");
        }
    }

    private String generateRandomPassword() {
        byte[] randomBytes = new byte[12];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}