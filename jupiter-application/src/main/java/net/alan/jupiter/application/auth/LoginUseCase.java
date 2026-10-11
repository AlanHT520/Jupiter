package net.alan.jupiter.application.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private final AuthService authService;

    public LoginResult execute(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "用户名不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "密码不能为空");
        }
        return authService.login(username, password);
    }

    public void changePassword(String userId, String oldPassword, String newPassword) {
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "旧密码不能为空");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "新密码不能为空");
        }
        authService.changePassword(userId, oldPassword, newPassword);
    }
}