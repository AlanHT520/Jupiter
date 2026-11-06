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

    /**
     * 提交注册申请：姓名 / 性别 / 手机号 / 密码 / 部门；只落「待审核」，不自动建号。
     */
    @Transactional
    public void submit(String name, String gender, String phone,
                       String department, String className, String password) {
        if (!systemConfigService.isRegistrationEnabled()) {
            throw new BizException(ErrorCode.FORBIDDEN, "注册已关闭");
        }
        if (phone == null || !phone.matches("\\d{11}")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "手机号必须是 11 位数字");
        }
        if (password == null || password.length() < 4) {
            throw new BizException(ErrorCode.BAD_REQUEST, "密码至少 4 位");
        }
        if (counselorRepo.findByPhone(phone).isPresent()
                || counselorRepo.existsByUsername(phone)) {
            throw new BizException(ErrorCode.CONFLICT, "该手机号已注册，请直接登录");
        }
        if (registrationRepo.existsByPhoneAndStatus(phone, RegistrationStatus.PENDING)) {
            throw new BizException(ErrorCode.CONFLICT, "该手机号已有待审核申请，请等待管理员处理");
        }

        CounselorRegistration reg = CounselorRegistration.builder()
                .name(name)
                .gender(gender)
                .phone(phone)
                .department(department)
                .className(className)
                .password(passwordEncoder.encode(password))
                .passwordPlain(password)
                .status(RegistrationStatus.PENDING)
                .submittedAt(LocalDateTime.now())
                .build();
        registrationRepo.save(reg);
        log.info("收到注册申请，等待审核: {} ({})", name, phone);
    }

    /**
     * 审核通过：手机号作为登录名，创建工作人员账号。
     */
    @Transactional
    public void approve(Long registrationId, String reviewerName) {
        CounselorRegistration reg = requirePending(registrationId);
        if (counselorRepo.findByPhone(reg.getPhone()).isPresent()
                || counselorRepo.existsByUsername(reg.getPhone())) {
            throw new BizException(ErrorCode.CONFLICT, "该手机号已存在工作人员账号");
        }

        Counselor counselor = Counselor.builder()
                .username(reg.getPhone())
                .phone(reg.getPhone())
                .name(reg.getName())
                .gender(reg.getGender())
                .className(reg.getClassName())
                .department(reg.getDepartment())
                .password(reg.getPassword())
                .passwordPlain(reg.getPasswordPlain())
                .role("COUNSELOR")
                .isActive(true)
                .build();
        counselorRepo.save(counselor);

        reg.setStatus(RegistrationStatus.APPROVED);
        reg.setReviewedAt(LocalDateTime.now());
        reg.setReviewerName(reviewerName);
        registrationRepo.save(reg);
        log.info("注册申请已通过: {} ({})", reg.getName(), reg.getPhone());
    }

    /**
     * 审核拒绝：只改状态，不创建账号。
     */
    @Transactional
    public void reject(Long registrationId, String reviewerName) {
        CounselorRegistration reg = requirePending(registrationId);
        reg.setStatus(RegistrationStatus.REJECTED);
        reg.setReviewedAt(LocalDateTime.now());
        reg.setReviewerName(reviewerName);
        registrationRepo.save(reg);
        log.info("注册申请已拒绝: {} ({})", reg.getName(), reg.getPhone());
    }

    private CounselorRegistration requirePending(Long registrationId) {
        CounselorRegistration reg = registrationRepo.findById(registrationId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "注册申请不存在"));
        if (reg.getStatus() != RegistrationStatus.PENDING) {
            throw new BizException(ErrorCode.CONFLICT, "该申请已处理");
        }
        return reg;
    }
}
