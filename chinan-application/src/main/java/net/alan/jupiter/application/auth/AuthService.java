package net.alan.jupiter.application.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.model.Counselor;
import net.alan.jupiter.domain.repository.CounselorRepository;
import net.alan.jupiter.infrastructure.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final CounselorRepository counselorRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResult login(String username, String password) {
        Counselor counselor = counselorRepository.findByUsername(username)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(password, counselor.getPassword())) {
            throw new BizException(ErrorCode.PASSWORD_ERROR);
        }

        String role = counselor.getRole() != null ? counselor.getRole() : "COUNSELOR";
        String tokenRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        String token = jwtTokenProvider.generateToken(counselor.getId(), counselor.getUsername(),
                tokenRole, counselor.getTokenVersion() != null ? counselor.getTokenVersion() : 0L);

        return LoginResult.builder()
                .token(token)
                .userId(counselor.getId())
                .username(counselor.getUsername())
                .role(role)
                .name(counselor.getName())
                .phone(counselor.getPhone())
                .studentNo(counselor.getStudentNo())
                .className(counselor.getClassName())
                .departmentName(counselor.getDepartment())
                .build();
    }

    @Transactional
    public void changePassword(String userId, String oldPassword, String newPassword) {
        Counselor counselor = counselorRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(oldPassword, counselor.getPassword())) {
            throw new BizException(ErrorCode.PASSWORD_ERROR);
        }

        counselor.setPassword(passwordEncoder.encode(newPassword));
        counselor.setTokenVersion(
                (counselor.getTokenVersion() != null ? counselor.getTokenVersion() : 0L) + 1);
        counselorRepository.save(counselor);
    }
}