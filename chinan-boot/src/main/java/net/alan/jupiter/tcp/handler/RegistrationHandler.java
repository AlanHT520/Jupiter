package net.alan.jupiter.tcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.registration.RegistrationService;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.tcp.model.TcpMessage;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationHandler implements TcpHandler {

    private final RegistrationService registrationService;
    private final ObjectMapper mapper;

    @Override
    public String type() {
        return "REGISTER";
    }

    @Override
    public TcpMessage handle(String requestId, JsonNode payload) {
        try {
            String name = payload.get("name").asText();
            String studentNo = payload.get("studentNo").asText();
            String className = payload.get("className").asText();
            String phone = payload.has("phone") ? payload.get("phone").asText() : "";
            String password = payload.get("password").asText();

            if (name.isBlank() || studentNo.isBlank() ||
                    className.isBlank() || password.isBlank()) {
                return TcpMessage.response(requestId == null ? "" : requestId,
                        400, "请填写所有必填字段", null);
            }

            registrationService.submit(name.trim(), studentNo.trim(),
                    className.trim(), phone.trim(), password);

            ObjectNode data = mapper.createObjectNode();
            data.put("message", "注册申请已提交，请等待管理员审核");
            return TcpMessage.response(requestId == null ? "" : requestId,
                    200, "", data);
        } catch (BizException e) {
            return TcpMessage.response(requestId == null ? "" : requestId,
                    e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("注册处理异常", e);
            return TcpMessage.response(requestId == null ? "" : requestId,
                    500, "系统异常，请稍后再试", null);
        }
    }
}