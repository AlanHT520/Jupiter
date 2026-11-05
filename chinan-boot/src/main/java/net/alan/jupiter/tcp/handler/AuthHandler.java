package net.alan.jupiter.tcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.auth.LoginResult;
import net.alan.jupiter.application.auth.LoginUseCase;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.infrastructure.security.JwtTokenProvider;
import net.alan.jupiter.tcp.model.TcpMessage;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthHandler implements TcpHandler {

    private final LoginUseCase loginUseCase;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper mapper;

    @Override
    public String type() {
        return "LOGIN";
    }

    @Override
    public TcpMessage handle(String requestId, JsonNode payload) {
        try {
            String username = payload.get("username").asText();
            String password = payload.get("password").asText();
            LoginResult result = loginUseCase.execute(username, password);

            ObjectNode data = mapper.createObjectNode();
            data.put("token", result.getToken());
            data.put("userId", result.getUserId());
            data.put("username", result.getUsername());
            data.put("role", result.getRole());
            data.put("name", result.getName());
            data.put("phone", result.getPhone());
            data.put("studentNo", result.getStudentNo());
            if (result.getClassName() != null) {
                data.put("className", result.getClassName());
            }
            if (result.getDepartmentName() != null) {
                data.put("departmentName", result.getDepartmentName());
            }

            return TcpMessage.response(requestId == null ? "" : requestId,
                    200, "", data);
        } catch (BizException e) {
            log.warn("TCP登录失败(业务异常): {}", e.getMessage());
            return TcpMessage.response(requestId == null ? "" : requestId,
                    401, "登录失败: " + e.getMessage(), null);
        } catch (Exception e) {
            log.error("TCP登录异常", e);
            return TcpMessage.response(requestId == null ? "" : requestId,
                    500, "系统异常，请稍后再试", null);
        }
    }

    public String validateToken(String token) {
        if (token == null || token.isBlank()) return null;
        if (!jwtTokenProvider.validateToken(token)) return null;
        return jwtTokenProvider.getUserIdFromToken(token);
    }
}