package net.alan.jupiter.tcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.check.QueryFloorService;
import net.alan.jupiter.application.security.AccessGuard;
import net.alan.jupiter.application.task.QueryTaskService;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.tcp.model.TcpMessage;
import net.alan.jupiter.web.dto.TaskOverviewDto;
import net.alan.jupiter.web.mapper.TaskMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskHandler implements TcpHandler {

    private final QueryTaskService queryTaskService;
    private final QueryFloorService queryFloorService;
    private final TaskMapper taskMapper;
    private final TcpAuthHelper tcpAuthHelper;
    private final AccessGuard accessGuard;
    private final ObjectMapper mapper;

    @Override
    public String type() {
        return "GET_TASKS";
    }

    @Override
    public TcpMessage handle(String requestId, JsonNode payload) {
        try {
            String userId = requireUserId(payload, requestId);
            if (userId == null) return authError(requestId);

            int page = payload.has("page") ? payload.get("page").asInt(0) : 0;
            int size = payload.has("size") ? payload.get("size").asInt(50) : 50;
            boolean isAdmin = accessGuard.isAdmin(userId);
            Page<Task> taskPage = queryTaskService.listVisibleTasks(
                    userId, isAdmin, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)));

            ArrayNode records = mapper.createArrayNode();
            taskPage.getContent().forEach(t -> {
                records.add(mapper.valueToTree(taskMapper.toDto(t)));
            });

            ObjectNode data = mapper.createObjectNode();
            data.set("records", records);
            data.put("total", taskPage.getTotalElements());
            data.put("page", taskPage.getNumber());
            data.put("size", taskPage.getSize());
            data.put("totalPages", taskPage.getTotalPages());

            return TcpMessage.response(requestId, 200, "", data);
        } catch (BizException e) {
            log.warn("TCP获取任务列表失败(业务异常): {}", e.getMessage());
            return TcpMessage.response(requestId, 400, e.getMessage(), null);
        } catch (Exception e) {
            log.error("TCP获取任务列表失败", e);
            return TcpMessage.response(requestId, 500, "系统异常，请稍后再试", null);
        }
    }

    public TcpMessage handleOverview(String requestId, JsonNode payload, String taskId) {
        try {
            String userId = requireUserId(payload, requestId);
            if (userId == null) return authError(requestId);

            Task task = accessGuard.requireTaskAccess(userId, taskId);
            if (task.getFloor() == null) {
                return TcpMessage.response(requestId, 400, "任务未关联楼层", null);
            }

            ObjectNode taskNode = mapper.valueToTree(taskMapper.toDto(task));
            QueryFloorService.FloorDetail floorDetail =
                    queryFloorService.getFloorDetail(taskId, task.getFloor().getId());

            ArrayNode roomInfos = mapper.createArrayNode();
            floorDetail.getRooms().forEach(r -> {
                ObjectNode roomNode = mapper.createObjectNode();
                roomNode.put("roomId", r.getRoomId());
                roomNode.put("roomNumber", r.getRoomNumber());
                roomNode.put("studentCount", r.getStudentCount());
                roomNode.put("checked", r.isChecked());
                roomInfos.add(roomNode);
            });

            ObjectNode floorNode = mapper.createObjectNode();
            floorNode.put("floorId", floorDetail.getFloorId());
            floorNode.put("floorNumber", floorDetail.getFloorNumber());
            floorNode.put("floorName", floorDetail.getFloorName());
            floorNode.put("roomCount", floorDetail.getRoomCount());
            floorNode.put("checkedRoomCount", floorDetail.getCheckedRoomCount());
            floorNode.set("rooms", roomInfos);

            ObjectNode data = mapper.createObjectNode();
            data.set("task", taskNode);
            data.set("floor", floorNode);

            return TcpMessage.response(requestId, 200, "", data);
        } catch (BizException e) {
            log.warn("TCP获取任务概览失败(业务异常): {}", e.getMessage());
            return TcpMessage.response(requestId, 400, e.getMessage(), null);
        } catch (Exception e) {
            log.error("TCP获取任务概览失败", e);
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