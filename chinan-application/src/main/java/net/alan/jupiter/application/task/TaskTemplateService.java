package net.alan.jupiter.application.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.domain.model.TaskTemplate;
import net.alan.jupiter.domain.repository.TaskRepository;
import net.alan.jupiter.domain.repository.TaskTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务模板：把一批任务的分配方案保存下来，换个日期一键重新分配。
 * 内容用「一行一个任务、字段以 || 分隔」的轻量格式保存，避免额外序列化依赖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskTemplateService {

    private static final String FIELD_SEP = "||";
    private static final String ROW_SEP = "\n";

    public record TemplateItem(String title, String buildingId, String buildingName,
                               String floorId, String floorNumber,
                               String roomNumbers, String counselorId, String counselorName) {
    }

    private final TaskTemplateRepository templateRepository;
    private final TaskRepository taskRepository;
    private final AssignTaskService assignTaskService;

    public List<TaskTemplate> list() {
        return templateRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public TaskTemplate saveFromTasks(String name, List<String> taskIds) {
        List<Task> tasks = taskRepository.findAllById(taskIds);
        if (tasks.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请先选择要保存为模板的任务");
        }
        StringBuilder sb = new StringBuilder();
        for (Task task : tasks) {
            if (sb.length() > 0) {
                sb.append(ROW_SEP);
            }
            sb.append(safe(task.getTitle())).append(FIELD_SEP)
                    .append(safe(task.getBuilding() != null ? task.getBuilding().getId() : null)).append(FIELD_SEP)
                    .append(safe(task.getBuildingName())).append(FIELD_SEP)
                    .append(safe(task.getFloor() != null ? task.getFloor().getId() : null)).append(FIELD_SEP)
                    .append(safe(task.getFloorNumber())).append(FIELD_SEP)
                    .append(safe(task.getRoomNumbers())).append(FIELD_SEP)
                    .append(safe(task.getAssignedCounselor() != null
                            ? task.getAssignedCounselor().getId() : null)).append(FIELD_SEP)
                    .append(safe(task.getAssignedCounselorName()));
        }
        TaskTemplate template = TaskTemplate.builder()
                .name(name)
                .itemsJson(sb.toString())
                .itemCount(tasks.size())
                .build();
        return templateRepository.save(template);
    }

    @Transactional
    public int apply(String templateId, LocalDate taskDate) {
        TaskTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "任务模板不存在"));
        List<TemplateItem> items = parse(template.getItemsJson());
        int created = 0;
        for (TemplateItem item : items) {
            assignTaskService.assignTask(item.title(), taskDate,
                    item.buildingId(), item.floorId(), item.roomNumbers(),
                    item.counselorId(), null);
            created++;
        }
        log.info("按模板「{}」分配 {} 个任务，日期 {}", template.getName(), created, taskDate);
        return created;
    }

    @Transactional
    public void delete(String templateId) {
        templateRepository.deleteById(templateId);
    }

    private List<TemplateItem> parse(String text) {
        List<TemplateItem> items = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return items;
        }
        for (String line : text.split(ROW_SEP)) {
            String[] parts = line.split("\\|\\|", -1);
            if (parts.length < 8) {
                continue;
            }
            items.add(new TemplateItem(parts[0], parts[1], parts[2], parts[3],
                    parts[4], parts[5], parts[6], parts[7]));
        }
        return items;
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("|", "/").replace("\n", " ");
    }
}
