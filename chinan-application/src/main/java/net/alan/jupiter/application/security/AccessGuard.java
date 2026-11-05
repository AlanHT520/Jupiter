package net.alan.jupiter.application.security;

import lombok.RequiredArgsConstructor;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.model.Counselor;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.domain.repository.CounselorRepository;
import net.alan.jupiter.domain.repository.TaskRepository;
import org.springframework.stereotype.Component;

/**
 * 任务级访问控制：写路径与读路径共用同一套归属判断。
 * <p>规则：管理员可访问全部任务；查宿员只能访问分配给自己的任务。</p>
 */
@Component
@RequiredArgsConstructor
public class AccessGuard {

    private final TaskRepository taskRepository;
    private final CounselorRepository counselorRepository;

    public Task requireTaskAccess(String userId, String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.TASK_NOT_FOUND));
        if (!isAdmin(userId) && !isOwner(task, userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该任务");
        }
        return task;
    }

    public boolean isAdmin(String userId) {
        return counselorRepository.findById(userId)
                .map(Counselor::getRole)
                .map(role -> "ADMIN".equalsIgnoreCase(role))
                .orElse(false);
    }

    public boolean isOwner(Task task, String userId) {
        return task.getAssignedCounselor() != null
                && task.getAssignedCounselor().getId().equals(userId);
    }

    /**
     * 从 {@code {taskId}_{studentId}} 形式的记录标识中解析任务 ID。
     */
    public static String taskIdOfCheckRecordKey(String checkRecordKey) {
        if (checkRecordKey == null) {
            return null;
        }
        int idx = checkRecordKey.indexOf('_');
        return idx > 0 ? checkRecordKey.substring(0, idx) : null;
    }
}
