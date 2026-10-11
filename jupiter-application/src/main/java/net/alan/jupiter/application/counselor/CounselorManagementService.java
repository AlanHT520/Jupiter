package net.alan.jupiter.application.counselor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.model.Counselor;
import net.alan.jupiter.domain.repository.CounselorRepository;
import net.alan.jupiter.domain.repository.TaskRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CounselorManagementService {

    private final CounselorRepository counselorRepository;
    private final TaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<Counselor> listCounselors() {
        return counselorRepository.findByRoleNotIgnoreCase("ADMIN");
    }

    @Transactional(readOnly = true)
    public List<Counselor> listActiveCounselorsByRole(String role) {
        return counselorRepository.findByIsActiveTrueAndRoleIgnoreCase(role);
    }

    @Transactional
    public void deleteCounselor(String id) {
        Counselor counselor = counselorRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "查宿者不存在"));
        if ("ADMIN".equalsIgnoreCase(counselor.getRole())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不能删除管理员账户");
        }
        taskRepository.clearCounselorReference(id);
        counselorRepository.delete(counselor);
    }

    @Transactional
    public void resetPassword(String id, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "新密码不能为空");
        }
        Counselor counselor = counselorRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "查宿者不存在"));
        counselor.setPassword(passwordEncoder.encode(newPassword));
        counselorRepository.save(counselor);
    }
}