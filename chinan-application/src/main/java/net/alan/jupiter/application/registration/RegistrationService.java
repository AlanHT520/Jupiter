package net.alan.jupiter.application.registration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.config.SystemConfigService;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.model.Counselor;
import net.alan.jupiter.domain.model.CounselorRegistration;
import net.alan.jupiter.domain.model.CounselorRegistration.RegistrationStatus;
import net.alan.jupiter.domain.repository.CounselorRegistrationRepository;
import net.alan.jupiter.domain.repository.CounselorRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final CounselorRegistrationRepository registrationRepo;
    private final CounselorRepository counselorRepo;
    private final SystemConfigService systemConfigService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void submit(String name, String studentNo, String className,
                       String phone, String password) {
        if (!systemConfigService.isRegistrationEnabled()) {
            throw new BizException(ErrorCode.FORBIDDEN, "注册已关闭");
        }

        if (counselorRepo.existsByStudentNo(studentNo)) {
            throw new BizException(ErrorCode.CONFLICT, "该学号已注册为查宿者，请直接登录");
        }

        String encodedPassword = passwordEncoder.encode(password);

        Counselor counselor = Counselor.builder()
                .username(studentNo)
                .name(name)
                .studentNo(studentNo)
                .className(className)
                .phone(phone)
                .password(encodedPassword)
                .role("COUNSELOR")
                .isActive(true)
                .build();
        counselorRepo.save(counselor);
        log.info("注册期间自动创建查宿者账户: {} ({})", name, studentNo);

        CounselorRegistration reg = CounselorRegistration.builder()
                .name(name)
                .studentNo(studentNo)
                .className(className)
                .phone(phone)
                .password(encodedPassword)
                .status(RegistrationStatus.APPROVED)
                .submittedAt(LocalDateTime.now())
                .reviewedAt(LocalDateTime.now())
                .reviewerName("系统自动通过")
                .build();
        registrationRepo.save(reg);
        log.info("注册申请已自动通过: {} ({})", name, studentNo);
    }
}