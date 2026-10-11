package net.alan.jupiter.web.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.application.auth.LoginResult;
import net.alan.jupiter.application.auth.LoginUseCase;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.common.result.ApiResult;
import net.alan.jupiter.web.dto.LoginRequest;
import net.alan.jupiter.web.dto.LoginResponse;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端-认证", description = "管理端认证接口")
@RestController
@RequestMapping("/admin-api/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final LoginUseCase loginUseCase;

    @Operation(summary = "管理端登录")
    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginUseCase.execute(request.getUsername(), request.getPassword());
        if (!"ADMIN".equals(result.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "非管理员账号，无法登录管理后台");
        }
        return ApiResult.success(LoginResponse.builder()
                .token(result.getToken())
                .userId(result.getUserId())
                .username(result.getUsername())
                .role(result.getRole())
                .name(result.getName())
                .build());
    }
}