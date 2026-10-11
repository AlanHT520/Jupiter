package net.alan.jupiter.tcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.check.QueryFloorService;
import net.alan.jupiter.application.check.SubmitRoomCheckService;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.domain.model.Student;
import net.alan.jupiter.tcp.model.TcpMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomHandler implements TcpHandler {

    private final QueryFloorService queryFloorService;
    private final SubmitRoomCheckService submitRoomCheckService;
    private final TcpAuthHelper tcpAuthHelper;
    private final ObjectMapper mapper;

    @Override
    public String type() {
        return "GET_STUDENTS";
    }

    @Override
    public TcpMessage handle(String requestId, JsonNode payload) {
        try {
            String userId = requireUserId(payload, requestId);
            if (userId == null) return authError(requestId);

            String roomId = payload.get("roomId").asText();
            String taskId = payload.has("taskId") ? payload.get("taskId").asText() : null;
            List<Student> students = queryFloorService.getStudentsByRoom(roomId);

            Map<String, CheckRecord> recordMap = Map.of();
            if (taskId != null) {
                List<CheckRecord> records = submitRoomCheckService
                        .getRoomCheckRecords(taskId, roomId);
                recordMap = records.stream()
                        .filter(r -> r.getStudent() != null)
                        .collect(Collectors.toMap(
                                r -> r.getStudent().getId(), r -> r, (a, b) -> a));
            }

            Map<String, CheckRecord> finalRecordMap = recordMap;
            ArrayNode recordsNode = mapper.createArrayNode();
            for (Student s : students) {
                CheckRecord record = finalRecordMap.get(s.getId());
                ObjectNode node = mapper.createObjectNode();
                node.put("id", s.getId());
                node.put("studentNo", s.getStudentNo());
                node.put("name", s.getName());
                node.put("gender", s.getGender());
                node.put("className", s.getClassName());
                node.put("roomId", roomId);
                node.put("roomNumber", s.getRoomNumber());
                node.put("bedNumber", s.getBedNumber());
                node.put("phone", s.getPhone());
                if (record != null) {
                    if (record.getCheckStatus() != null) {
                        node.put("checkStatus", record.getCheckStatus().name());
                        node.put("statusCode", record.getCheckStatus().getCode());
                    } else {
                        node.put("checkStatus", "PRESENT");
                        node.put("statusCode", 1);
                    }
                    if (record.getPresetStatus() != null) {
                        node.put("presetStatus", record.getPresetStatus().name());
                    }
                    if (record.getStatusSource() != null) {
                        node.put("statusSource", record.getStatusSource());
                    }
                    if (record.getRemark() != null) {
                        node.put("remark", record.getRemark());
                    }
                    if (record.getProofFile() != null) {
                        node.put("proofFile", record.getProofFile());
                    }
                } else {
                    node.put("checkStatus", "PRESENT");
                    node.put("statusCode", 1);
                }
                recordsNode.add(node);
            }

            ObjectNode data = mapper.createObjectNode();
            data.set("students", recordsNode);
            data.put("studentCount", students.size());

            return TcpMessage.response(requestId, 200, "", data);
        } catch (BizException e) {
            log.warn("TCP获取宿舍学生失败(业务异常): {}", e.getMessage());
            return TcpMessage.response(requestId, 400, e.getMessage(), null);
        } catch (Exception e) {
            log.error("TCP获取宿舍学生失败", e);
            return TcpMessage.response(requestId, 500, "系统异常，请稍后再试", null);
        }
    }

    public TcpMessage handleSubmit(String requestId, JsonNode payload, String roomId) {
        try {
            String userId = requireUserId(payload, requestId);
            if (userId == null) return authError(requestId);

            String taskId = payload.get("taskId").asText();
            JsonNode students = payload.get("students");
            List<SubmitRoomCheckService.StudentCheckItem> items = new ArrayList<>();
            if (students != null) {
                for (JsonNode item : students) {
                    CheckStatus checkStatus = CheckStatus.valueOf(item.get("checkStatus").asText());

                    SubmitRoomCheckService.StudentCheckItem citem =
                            SubmitRoomCheckService.StudentCheckItem.builder()
                                    .studentId(item.get("studentId").asText())
                                    .checkStatus(checkStatus)
                                    .presetStatus(item.has("presetStatus") && !item.get("presetStatus").isNull()
                                            ? CheckStatus.valueOf(item.get("presetStatus").asText())
                                            : null)
                                    .presetOverridden(item.has("presetOverridden") && !item.get("presetOverridden").isNull()
                                            ? item.get("presetOverridden").asBoolean()
                                            : null)
                                    .statusSource(item.has("statusSource") && !item.get("statusSource").isNull()
                                            ? item.get("statusSource").asText()
                                            : "MANUAL")
                                    .remark(item.has("remark") && !item.get("remark").isNull()
                                            ? item.get("remark").asText()
                                            : null)
                                    .proofFile(item.has("proofFile") && !item.get("proofFile").isNull()
                                            ? item.get("proofFile").asText()
                                            : null)
                                    .build();
                    items.add(citem);
                }
            }

            submitRoomCheckService.submitRoomCheck(taskId, roomId, items, userId);

            return TcpMessage.response(requestId, 200, "提交成功", null);
        } catch (BizException e) {
            log.warn("TCP提交查寝失败(业务异常): {}", e.getMessage());
            return TcpMessage.response(requestId, 400, e.getMessage(), null);
        } catch (Exception e) {
            log.error("TCP提交查寝失败", e);
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