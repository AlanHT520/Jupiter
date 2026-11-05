package net.alan.jupiter.tcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.proof.ProofFileService;
import net.alan.jupiter.application.security.AccessGuard;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.domain.repository.CounselorRepository;
import net.alan.jupiter.domain.repository.TaskRepository;
import net.alan.jupiter.tcp.model.TcpMessage;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProofHandler implements TcpHandler {

    private final ProofFileService proofFileService;
    private final TcpAuthHelper tcpAuthHelper;
    private final AccessGuard accessGuard;
    private final TaskRepository taskRepository;
    private final CounselorRepository counselorRepository;
    private final ObjectMapper mapper;

    @Override
    public String type() {
        return "UPLOAD_PROOF";
    }

    @Override
    public TcpMessage handle(String requestId, JsonNode payload) {
        try {
            String userId = requireUserId(payload, requestId);
            if (userId == null) return authError(requestId);

            if (!payload.has("checkRecordId") || payload.get("checkRecordId").isNull()) {
                return TcpMessage.response(requestId, 400, "缺少checkRecordId参数", null);
            }
            if (!payload.has("data") || payload.get("data").isNull()) {
                return TcpMessage.response(requestId, 400, "缺少图片数据", null);
            }

            String checkRecordId = payload.get("checkRecordId").asText();
            String base64Data = payload.get("data").asText();
            String extension = payload.has("extension") && !payload.get("extension").isNull()
                    ? payload.get("extension").asText() : ".jpg";

            // 证明文件必须挂到当前用户有权限的任务下，避免越权覆盖他人记录
            String taskId = AccessGuard.taskIdOfCheckRecordKey(checkRecordId);
            if (taskId == null) {
                return TcpMessage.response(requestId, 400, "checkRecordId 格式不正确", null);
            }
            accessGuard.requireTaskAccess(userId, taskId);

            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            String proofFilePath = proofFileService.uploadProof(
                    new ByteArrayInputStream(imageBytes), "proof" + extension, checkRecordId);

            ObjectNode result = mapper.createObjectNode();
            result.put("proofFilePath", proofFilePath);

            log.info("TCP证明文件上传成功: checkRecordId={}, path={}", checkRecordId, proofFilePath);
            return TcpMessage.response(requestId, 200, "上传成功", result);
        } catch (BizException e) {
            log.warn("TCP上传证明失败(业务异常): {}", e.getMessage());
            return TcpMessage.response(requestId, 400, e.getMessage(), null);
        } catch (Exception e) {
            log.error("TCP上传证明失败", e);
            return TcpMessage.response(requestId, 500, "系统异常，请稍后再试", null);
        }
    }

    public TcpMessage handleDelete(String requestId, JsonNode payload) {
        try {
            String userId = requireUserId(payload, requestId);
            if (userId == null) return authError(requestId);

            if (!payload.has("filePath") || payload.get("filePath").isNull()) {
                return TcpMessage.response(requestId, 400, "缺少filePath参数", null);
            }
            if (!payload.has("taskId") || payload.get("taskId").isNull()) {
                return TcpMessage.response(requestId, 400, "缺少taskId参数", null);
            }
            if (!payload.has("studentId") || payload.get("studentId").isNull()) {
                return TcpMessage.response(requestId, 400, "缺少studentId参数", null);
            }

            String filePath = payload.get("filePath").asText();
            String taskId = payload.get("taskId").asText();
            String studentId = payload.get("studentId").asText();

            Task task = taskRepository.findById(taskId).orElse(null);
            if (task == null) {
                return TcpMessage.response(requestId, 404, "任务不存在", null);
            }

            var counselor = counselorRepository.findById(userId).orElse(null);
            String role = counselor != null ? counselor.getRole() : "";
            boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
            boolean isOwner = task.getAssignedCounselor() != null
                    && task.getAssignedCounselor().getId().equals(userId);
            if (!isAdmin && !isOwner) {
                return TcpMessage.response(requestId, 403, "无权操作该文件", null);
            }

            String expectedPrefix = "proofs/" + taskId + "_" + studentId + "/";
            if (!filePath.startsWith(expectedPrefix)) {
                return TcpMessage.response(requestId, 403, "非法文件路径", null);
            }

            proofFileService.deleteProof(filePath);

            ObjectNode result = mapper.createObjectNode();
            result.put("deleted", filePath);
            return TcpMessage.response(requestId, 200, "删除成功", result);
        } catch (Exception e) {
            log.error("TCP删除证明失败", e);
            return TcpMessage.response(requestId, 500, "系统异常，请稍后再试", null);
        }
    }

    private String requireUserId(JsonNode payload, String requestId) {
        if (payload == null || !payload.has("token") || payload.get("token").isNull()) return null;
        return tcpAuthHelper.validateAndGetUserId(payload.get("token").asText());
    }

    private TcpMessage authError(String requestId) {
        return TcpMessage.response(requestId, 401, "未登录或令牌已过期", null);
    }
}