package net.alan.jupiter.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.application.auth.LoginResult;
import net.alan.jupiter.application.auth.LoginUseCase;
import net.alan.jupiter.common.result.ApiResult;
import net.alan.jupiter.web.dto.ChangePasswordRequest;
import net.alan.jupiter.web.dto.LoginRequest;
import net.alan.jupiter.web.dto.LoginResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "APP-认证", description = "PE端认证接口")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AppAuthController {

    private final LoginUseCase loginUseCase;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginUseCase.execute(request.getUsername(), request.getPassword());
        return ApiResult.success(LoginResponse.builder()
                .token(result.getToken())
                .userId(result.getUserId())
                .username(result.getUsername())
                .role(result.getRole())
                .name(result.getName())
                .build());
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public ApiResult<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        loginUseCase.changePassword(userId, request.getOldPassword(), request.getNewPassword());
        return ApiResult.success();
    }
}