package net.alan.jupiter.tcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.config.SystemConfigService;
import net.alan.jupiter.tcp.model.TcpMessage;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationStatusHandler implements TcpHandler {

    private final SystemConfigService systemConfigService;
    private final ObjectMapper mapper;

    @Override
    public String type() {
        return "GET_REGISTRATION_STATUS";
    }

    @Override
    public TcpMessage handle(String requestId, JsonNode payload) {
        try {
            ObjectNode data = mapper.createObjectNode();
            data.put("enabled", systemConfigService.isRegistrationEnabled());
            return TcpMessage.response(requestId == null ? "" : requestId,
                    200, "", data);
        } catch (Exception e) {
            log.error("查询注册状态异常", e);
            return TcpMessage.response(requestId == null ? "" : requestId,
                    500, "系统异常，请稍后再试", null);
        }
    }
}