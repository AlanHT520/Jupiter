package net.alan.jupiter.tcp;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.tcp.handler.AuthHandler;
import net.alan.jupiter.tcp.handler.ProofHandler;
import net.alan.jupiter.tcp.handler.RegistrationHandler;
import net.alan.jupiter.tcp.handler.RegistrationStatusHandler;
import net.alan.jupiter.tcp.handler.RoomHandler;
import net.alan.jupiter.tcp.handler.TaskHandler;
import net.alan.jupiter.tcp.model.TcpMessage;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TcpDispatcher {

    private final AuthHandler authHandler;
    private final TaskHandler taskHandler;
    private final RoomHandler roomHandler;
    private final ProofHandler proofHandler;
    private final RegistrationStatusHandler registrationStatusHandler;
    private final RegistrationHandler registrationHandler;

    public TcpMessage dispatch(TcpMessage request) {
        String type = request.getType();
        String requestId = request.getRequestId();
        JsonNode payload = request.getPayload();

        if (type == null) {
            return error(requestId, "消息类型不能为空");
        }

        if (payload == null) {
            return error(requestId, "消息体不能为空");
        }

        log.debug("TCP收到消息: type={}, requestId={}", type, requestId);

        return switch (type) {
            case "LOGIN" -> authHandler.handle(requestId, payload);
            case "GET_TASKS" -> taskHandler.handle(requestId, payload);
            case "GET_OVERVIEW" -> {
                String taskId = payload.has("taskId")
                        ? payload.get("taskId").asText() : null;
                yield taskId != null
                        ? taskHandler.handleOverview(requestId, payload, taskId)
                        : error(requestId, "缺少taskId参数");
            }
            case "GET_STUDENTS" -> roomHandler.handle(requestId, payload);
            case "SUBMIT_CHECK" -> {
                String roomId = payload.has("roomId")
                        ? payload.get("roomId").asText() : null;
                yield roomId != null
                        ? roomHandler.handleSubmit(requestId, payload, roomId)
                        : error(requestId, "缺少roomId参数");
            }
            case "UPLOAD_PROOF" -> proofHandler.handle(requestId, payload);
            case "DELETE_PROOF" -> proofHandler.handleDelete(requestId, payload);
            case "GET_REGISTRATION_STATUS" -> registrationStatusHandler.handle(requestId, payload);
            case "REGISTER" -> registrationHandler.handle(requestId, payload);
            default -> error(requestId, "未知消息类型: " + type);
        };
    }

    private TcpMessage error(String requestId, String message) {
        return TcpMessage.response(requestId != null ? requestId : "",
                400, message, null);
    }
}