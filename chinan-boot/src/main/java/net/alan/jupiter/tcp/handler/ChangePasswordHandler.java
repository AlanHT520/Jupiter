package net.alan.jupiter.tcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.auth.AuthService;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.tcp.model.TcpMessage;
import org.springframework.stereotype.Component;

/**
 * 修改密码：使用当前 TCP 会话的 token 定位用户，旧密码错误/新密码过短会返回明确错误。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangePasswordHandler implements TcpHandler {

    private final TcpAuthHelper tcpAuthHelper;
    private final AuthService authService;

    @Override
    public String type() {
        return "CHANGE_PASSWORD";
    }

    @Override
    public TcpMessage handle(String requestId, JsonNode payload) {
        String req = requestId == null ? "" : requestId;
        String token = payload.has("token") ? payload.get("token").asText() : "";
        String userId = tcpAuthHelper.validateAndGetUserId(token);
        if (userId == null) {
            return TcpMessage.response(req, 401, "登录已过期，请重新登录", null);
        }

        String oldPassword = payload.has("oldPassword") ? payload.get("oldPassword").asText() : "";
        String newPassword = payload.has("newPassword") ? payload.get("newPassword").asText() : "";
        if (oldPassword.isBlank() || newPassword.isBlank()) {
            return TcpMessage.response(req, 400, "旧密码和新密码都不能为空", null);
        }
        if (newPassword.length() < 4) {
            return TcpMessage.response(req, 400, "新密码至少 4 位", null);
        }

        try {
            authService.changePassword(userId, oldPassword, newPassword);
            return TcpMessage.response(req, 200, "密码已修改，请重新登录", null);
        } catch (BizException e) {
            return TcpMessage.response(req, e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("修改密码失败", e);
            return TcpMessage.response(req, 500, "修改失败，请稍后再试", null);
        }
    }
}
