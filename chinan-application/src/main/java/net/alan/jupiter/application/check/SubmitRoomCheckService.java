package net.alan.jupiter.application.check;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.enums.TaskStatus;
import net.alan.jupiter.domain.model.*;
import net.alan.jupiter.domain.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmitRoomCheckService {

    /** check_record.client_op_id 的列宽，与迁移脚本保持一致。 */
    private static final int CLIENT_OP_ID_MAX_LENGTH = 64;

    private final CheckRecordRepository checkRecordRepository;
    private final RoomRepository roomRepository;
    private final StudentRepository studentRepository;
    private final TaskRepository taskRepository;
    private final CounselorRepository counselorRepository;
    private final CheckRecordModifyLogRepository modifyLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    @Lazy
    private SubmitRoomCheckService self;

    /**
     * 提交单个房间的查寝结果。
     * 验证后在一个写事务中完成持久化，避免长时间占用连接。
     * 不带客户端幂等键的旧签名，行为与历史版本完全一致。
     *
     * @return 本次落库（或幂等命中）的查寝记录
     */
    @Transactional
    public List<CheckRecord> submitRoomCheck(String taskId, String roomId,
                                             List<StudentCheckItem> checkItems,
                                             String submittedBy) {
        return submitRoomCheck(taskId, roomId, checkItems, submittedBy, null);
    }

    /**
     * 提交单个房间的查寝结果（带客户端幂等键）。
     * <p>
     * {@code clientOpId} 为空时与旧签名完全相同；非空且服务端已存在同 clientOpId 的记录时，
     * 直接返回首次落库的记录，不重复写入、不再发布审计事件（离线补交重试幂等）。
     * 同一房间用<em>不同</em> clientOpId 提交仍是覆盖语义，审计逻辑保持不变。
     */
    @Transactional
    public List<CheckRecord> submitRoomCheck(String taskId, String roomId,
                                             List<StudentCheckItem> checkItems,
                                             String submittedBy, String clientOpId) {
        String opId = normalizeClientOpId(clientOpId);
        if (opId != null) {
            List<CheckRecord> replayed = checkRecordRepository.findByClientOpId(opId);
            if (!replayed.isEmpty()) {
                assertSameTarget(replayed, taskId, roomId);
                log.info("查寝提交幂等命中，返回首次结果: clientOpId={}, taskId={}, roomId={}, records={}",
                        opId, taskId, roomId, replayed.size());
                return replayed;
            }
        }

        SubmitContext ctx = validateAndPrepare(taskId, roomId, checkItems, submittedBy, opId);
        persistRoomCheck(ctx);
        updateTaskCompletionStatus(ctx.taskId);
        return ctx.toSave;
    }

    /**
     * 幂等键归一化：空白视为未携带（兼容旧客户端），超长直接拒绝，
     * 避免被数据库列宽截断后产生两个不同的 op 命中同一行。
     */
    private String normalizeClientOpId(String clientOpId) {
        if (clientOpId == null) {
            return null;
        }
        String trimmed = clientOpId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > CLIENT_OP_ID_MAX_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "clientOpId 长度不能超过 " + CLIENT_OP_ID_MAX_LENGTH + " 个字符");
        }
        return trimmed;
    }

    /**
     * 幂等重放时校验首次提交与本次请求指向同一个任务/房间，
     * 防止错误复用 clientOpId 读到其他任务的数据。只比较 ID，不触发懒加载。
     */
    private void assertSameTarget(List<CheckRecord> records, String taskId, String roomId) {
        for (CheckRecord record : records) {
            boolean sameTask = record.getTask() != null
                    && Objects.equals(taskId, record.getTask().getId());
            boolean sameRoom = record.getRoom() != null
                    && Objects.equals(roomId, record.getRoom().getId());
            if (!sameTask || !sameRoom) {
                throw new BizException(ErrorCode.CONFLICT,
                        "clientOpId 已被其他任务或房间的提交占用，请重新生成");
            }
        }
    }

    /**
     * 批量提交多个房间的查寝结果。每个房间使用独立事务，
     * 某个房间失败不影响已提交的房间。
     */
    public void batchSubmitRoomCheck(List<BatchRoomSubmission> submissions, String submittedBy) {
        if (submissions == null || submissions.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "提交数据不能为空");
        }
        for (BatchRoomSubmission sub : submissions) {
            List<StudentCheckItem> items = sub.getStudents().stream().map(s ->
                    StudentCheckItem.builder()
                            .studentId(s.getStudentId())
                            .checkStatus(s.getCheckStatus())
                            .presetStatus(s.getPresetStatus())
                            .presetOverridden(s.getPresetOverridden())
                            .statusSource(s.getStatusSource())
                            .remark(s.getRemark())
                            .proofFile(s.getProofFile())
                            .build()
            ).collect(Collectors.toList());

            self.submitRoomCheck(sub.getTaskId(), sub.getRoomId(), items, submittedBy);
        }
    }

    @Transactional(readOnly = true)
    public List<CheckRecord> getRoomCheckRecords(String taskId, String roomId) {
        return checkRecordRepository.findByTaskIdAndRoomId(taskId, roomId);
    }

    @Transactional(readOnly = true)
    public List<CheckRecord> getTaskCheckRecords(String taskId) {
        return checkRecordRepository.findByTaskId(taskId);
    }

    /**
     * 验证提交数据并准备待持久化的记录。
     * 不在大事务中执行，每个 repository 调用使用自身的短事务。
     */
    private SubmitContext validateAndPrepare(String taskId, String roomId,
                                              List<StudentCheckItem> checkItems,
                                              String submittedBy, String clientOpId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.TASK_NOT_FOUND));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BizException(ErrorCode.ROOM_NOT_FOUND));

        if (task.getStatus() == TaskStatus.CANCELLED) {
            throw new BizException(ErrorCode.TASK_STATUS_ERROR, "任务已取消，无法提交查寝结果");
        }

        boolean wasPending = task.getStatus() == TaskStatus.PENDING;

        if (room.getFloor() == null || !room.getFloor().getBuilding().getId()
                .equals(task.getBuilding().getId())) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "房间 " + room.getRoomNumber() + " 不属于任务所在楼栋");
        }

        Counselor user = counselorRepository.findById(submittedBy)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "用户不存在"));
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole());
        boolean isOwner = task.getAssignedCounselor() != null
                && task.getAssignedCounselor().getId().equals(submittedBy);
        if (!isAdmin && !isOwner) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权提交该任务的查寝结果");
        }
        if (task.getStatus() == TaskStatus.COMPLETED && !isAdmin) {
            // 与 CheckRecordModifyService 保持一致：任务完成后查宿员不能再改，管理员可修改但不留旧值会由审计日志记录
            throw new BizException(ErrorCode.TASK_STATUS_ERROR,
                    "任务已完成，如需修改查寝结果请联系管理员");
        }

        if (task.getRoomNumbers() != null && !task.getRoomNumbers().isBlank()) {
            Set<String> allowedRooms = new HashSet<>(
                    Arrays.asList(task.getRoomNumbers().split(",")));
            if (!allowedRooms.contains(room.getRoomNumber())) {
                throw new BizException(ErrorCode.BAD_REQUEST,
                        "房间 " + room.getRoomNumber() + " 不在任务范围内");
            }
        }

        if (checkItems == null || checkItems.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "提交数据不能为空");
        }

        Set<String> submittedStudentIds = new HashSet<>();
        List<String> studentIdList = new ArrayList<>();
        for (StudentCheckItem item : checkItems) {
            if (item.getCheckStatus() == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "查寝状态不能为空");
            }
            if (!submittedStudentIds.add(item.getStudentId())) {
                throw new BizException(ErrorCode.BAD_REQUEST,
                        "学生ID重复提交: " + item.getStudentId());
            }
            studentIdList.add(item.getStudentId());
        }

        Map<String, Student> studentCache = new HashMap<>();
        List<Student> students = studentRepository.findAllById(studentIdList);
        for (Student student : students) {
            studentCache.put(student.getId(), student);
        }

        for (String studentId : studentIdList) {
            Student student = studentCache.get(studentId);
            if (student == null) {
                throw new BizException(ErrorCode.STUDENT_NOT_FOUND,
                        "学生ID: " + studentId);
            }
            if (student.getRoom() == null || !student.getRoom().getId().equals(roomId)) {
                throw new BizException(ErrorCode.BAD_REQUEST,
                        "学生 " + student.getName() + " 不属于房间 " + room.getRoomNumber());
            }
        }

        List<CheckRecord> existingRecords = checkRecordRepository
                .findByTaskIdAndStudentIdIn(taskId, studentIdList);
        Map<String, CheckRecord> existingMap = existingRecords.stream()
                .collect(Collectors.toMap(
                        r -> r.getStudent().getId(),
                        r -> r,
                        (a, b) -> a));

        // 先快照旧值，再修改实体；否则审计日志拿到的"旧值"与"新值"是同一个对象
        Map<String, CheckRecordSubmittedEvent.RecordSnapshot> beforeSnapshots = new HashMap<>();
        for (Map.Entry<String, CheckRecord> entry : existingMap.entrySet()) {
            CheckRecord old = entry.getValue();
            beforeSnapshots.put(entry.getKey(), new CheckRecordSubmittedEvent.RecordSnapshot(
                    old.getCheckStatus(), old.getRemark(), old.getProofFile()));
        }

        List<CheckRecord> toSave = new ArrayList<>();
        // submittedAt 是服务器接收时间（本次提交统一取一个时刻），客户端不可伪造
        LocalDateTime submittedAt = LocalDateTime.now();
        // 迟到判定：taskDate 不可变，只与服务器接收日期比较；taskDate 缺失时不标记迟到
        boolean late = task.getTaskDate() != null
                && task.getTaskDate().isBefore(submittedAt.toLocalDate());
        for (StudentCheckItem item : checkItems) {
            Student student = studentCache.get(item.getStudentId());
            String statusSource = item.getStatusSource() != null
                    ? item.getStatusSource() : "MANUAL";

            CheckRecord record = existingMap.getOrDefault(item.getStudentId(),
                    CheckRecord.builder()
                            .task(task)
                            .room(room)
                            .roomNumber(room.getRoomNumber())
                            .student(student)
                            .studentNo(student.getStudentNo())
                            .studentName(student.getName())
                            .submittedBy(submittedBy)
                            .build());

            record.setCheckStatus(item.getCheckStatus());
            record.setPresetStatus(item.getPresetStatus());
            record.setPresetOverridden(item.getPresetOverridden() != null
                    ? item.getPresetOverridden() : false);
            record.setStatusSource(statusSource);
            record.setRemark(item.getRemark());
            record.setProofFile(normalizeProofFile(item.getProofFile(), taskId, item.getStudentId()));
            record.setSubmittedAt(submittedAt);
            if (clientOpId != null) {
                // 覆盖提交时更新为本次的幂等键；旧客户端不带 opId 时保留原值，避免丢失去重依据
                record.setClientOpId(clientOpId);
            }
            record.setLate(late);

            toSave.add(record);
        }

        boolean hadAnyBefore = checkRecordRepository.existsByTaskIdAndRoomId(taskId, roomId);

        return SubmitContext.builder()
                .taskId(taskId)
                .roomId(roomId)
                .wasPending(wasPending)
                .hadAnyBefore(hadAnyBefore)
                .toSave(toSave)
                .beforeSnapshots(beforeSnapshots)
                .submittedBy(submittedBy)
                .build();
    }

    /**
     * 证明文件路径只接受服务端上传接口返回的 {@code proofs/{taskId}_{studentId}/...}，
     * 避免客户端写入任意路径（后续导出会把该路径当文件读取）。
     */
    private String normalizeProofFile(String proofFile, String taskId, String studentId) {
        if (proofFile == null || proofFile.isBlank()) {
            return null;
        }
        String prefix = "proofs/" + taskId + "_" + studentId + "/";
        if (!proofFile.startsWith(prefix) || proofFile.contains("..")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "证明文件路径不合法");
        }
        return proofFile;
    }

    /**
     * 持久化查寝记录并更新任务状态。
     */
    private void persistRoomCheck(SubmitContext ctx) {
        if (ctx.wasPending) {
            Task task = taskRepository.findById(ctx.taskId).orElse(null);
            if (task != null && task.getStatus() == TaskStatus.PENDING) {
                task.setStatus(TaskStatus.IN_PROGRESS);
                task.setStartTime(LocalDateTime.now());
                taskRepository.save(task);
            }
        }

        checkRecordRepository.saveAll(ctx.toSave);

        eventPublisher.publishEvent(new CheckRecordSubmittedEvent(
                ctx.taskId, ctx.roomId, ctx.toSave, ctx.beforeSnapshots, ctx.submittedBy));

        if (!ctx.hadAnyBefore) {
            taskRepository.incrementCheckedRooms(ctx.taskId);
        }
    }

    /**
     * 根据已完成的房间数更新任务完成状态。
     */
    private void updateTaskCompletionStatus(String taskId) {
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        Integer totalRooms = task.getTotalRooms();
        Integer checkedRooms = task.getCheckedRooms();
        if (totalRooms != null && checkedRooms != null && checkedRooms >= totalRooms) {
            task.setStatus(TaskStatus.COMPLETED);
            task.setFinishTime(LocalDateTime.now());
            taskRepository.save(task);
        }
    }

    @lombok.Data
    @lombok.Builder
    private static class SubmitContext {
        private String taskId;
        private String roomId;
        private boolean wasPending;
        private boolean hadAnyBefore;
        private List<CheckRecord> toSave;
        private Map<String, CheckRecordSubmittedEvent.RecordSnapshot> beforeSnapshots;
        private String submittedBy;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StudentCheckItem {
        private String studentId;
        private CheckStatus checkStatus;
        private CheckStatus presetStatus;
        private Boolean presetOverridden;
        private String statusSource;
        private String remark;
        private String proofFile;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BatchRoomSubmission {
        private String taskId;
        private String roomId;
        private List<StudentCheckItem> students;
    }
}