package net.alan.jupiter.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.domain.model.Counselor;
import net.alan.jupiter.domain.repository.CounselorRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final CounselorRepository counselorRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (counselorRepository.findByUsername("AlanHT520").isEmpty()) {
            Counselor admin = Counselor.builder()
                    .username("AlanHT520")
                    .password(passwordEncoder.encode("minecraft"))
                    .name("Alan")
                    .role("ADMIN")
                    .isActive(true)
                    .build();
            counselorRepository.save(admin);
            log.info("已创建默认管理员: AlanHT520 / minecraft");
        }
    }
}