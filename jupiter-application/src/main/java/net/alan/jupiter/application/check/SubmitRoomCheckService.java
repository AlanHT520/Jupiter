package net.alan.jupiter.application.check;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.enums.TaskStatus;
import net.alan.jupiter.domain.model.*;
import net.alan.jupiter.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmitRoomCheckService {

    private final CheckRecordRepository checkRecordRepository;
    private final RoomRepository roomRepository;
    private final StudentRepository studentRepository;
    private final TaskRepository taskRepository;
    private final CheckRecordModifyLogRepository modifyLogRepository;

    @Transactional
    public void submitRoomCheck(String taskId, String roomId, List<StudentCheckItem> checkItems,
                                 String submittedBy) {
        Task task = doSubmitRoomCheck(taskId, roomId, checkItems, submittedBy);
        updateTaskCompletionStatus(task);
    }

    /**
     * 执行房间查寝提交的核心逻辑（不含任务完成状态更新），
     * 供 submitRoomCheck 和 batchSubmitRoomCheck 共用。
     */
    private Task doSubmitRoomCheck(String taskId, String roomId, List<StudentCheckItem> checkItems,
                                    String submittedBy) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.TASK_NOT_FOUND));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BizException(ErrorCode.ROOM_NOT_FOUND));

        if (task.getStatus() == TaskStatus.COMPLETED) {
            log.info("任务已完成但允许修改查寝结果: taskId={}, roomId={}", taskId, roomId);
        }
        if (task.getStatus() == TaskStatus.CANCELLED) {
            throw new BizException(ErrorCode.TASK_STATUS_ERROR, "任务已取消，无法提交查寝结果");
        }
        if (task.getStatus() == TaskStatus.PENDING) {
            task.setStatus(TaskStatus.IN_PROGRESS);
            task.setStartTime(LocalDateTime.now());
        }

        if (room.getFloor() == null || !room.getFloor().getBuilding().getId()
                .equals(task.getBuilding().getId())) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "房间 " + room.getRoomNumber() + " 不属于任务所在楼栋");
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
        Map<String, Student> studentCache = new HashMap<>();
        for (StudentCheckItem item : checkItems) {
            if (item.getCheckStatus() == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "查寝状态不能为空");
            }

            if (!submittedStudentIds.add(item.getStudentId())) {
                throw new BizException(ErrorCode.BAD_REQUEST,
                        "学生ID重复提交: " + item.getStudentId());
            }

            Student student = studentRepository.findById(item.getStudentId())
                    .orElseThrow(() -> new BizException(ErrorCode.STUDENT_NOT_FOUND,
                            "学生ID: " + item.getStudentId()));
            studentCache.put(item.getStudentId(), student);

            if (student.getRoom() == null || !student.getRoom().getId().equals(roomId)) {
                throw new BizException(ErrorCode.BAD_REQUEST,
                        "学生 " + student.getName() + " 不属于房间 " + room.getRoomNumber());
            }
        }

        // Upsert: 查找已有记录，存在则更新，不存在则新建
        List<String> studentIdList = new ArrayList<>(submittedStudentIds);
        List<CheckRecord> existingRecords = checkRecordRepository
                .findByTaskIdAndStudentIdIn(taskId, studentIdList);
        Map<String, CheckRecord> existingMap = existingRecords.stream()
                .collect(Collectors.toMap(
                        r -> r.getStudent().getId(),
                        r -> r,
                        (a, b) -> a));

        List<CheckRecord> toSave = new ArrayList<>();
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

            boolean isNew = !existingMap.containsKey(item.getStudentId());
            CheckStatus oldStatus = record.getCheckStatus();
            String oldRemark = record.getRemark();
            String oldProof = record.getProofFile();

            record.setCheckStatus(item.getCheckStatus());
            record.setPresetStatus(item.getPresetStatus());
            record.setPresetOverridden(item.getPresetOverridden() != null
                    ? item.getPresetOverridden() : false);
            record.setStatusSource(statusSource);
            record.setRemark(item.getRemark());
            record.setProofFile(item.getProofFile());
            record.setSubmittedAt(LocalDateTime.now());

            toSave.add(record);

            logChanges(record.getId(), student.getName(), isNew,
                    oldStatus, item.getCheckStatus(),
                    oldRemark, item.getRemark(),
                    oldProof, item.getProofFile(),
                    submittedBy);
        }

        checkRecordRepository.saveAll(toSave);

        // 更新任务统计，但不检查完成状态（由调用方统一处理）
        int checked = (int) checkRecordRepository.countDistinctRoomByTaskId(taskId);
        task.setCheckedRooms(checked);
        taskRepository.save(task);

        return task;
    }

    /**
     * 根据已完成的房间数更新任务完成状态。
     */
    private void updateTaskCompletionStatus(Task task) {
        Integer totalRooms = task.getTotalRooms();
        Integer checkedRooms = task.getCheckedRooms();
        if (totalRooms != null && checkedRooms != null && checkedRooms >= totalRooms) {
            task.setStatus(TaskStatus.COMPLETED);
            task.setFinishTime(LocalDateTime.now());
            taskRepository.save(task);
        }
    }

    private void logChanges(String recordId, String studentName, boolean isNew,
                            CheckStatus oldStatus, CheckStatus newStatus,
                            String oldRemark, String newRemark,
                            String oldProof, String newProof,
                            String submittedBy) {
        LocalDateTime now = LocalDateTime.now();

        if (isNew) {
            writeLog(recordId, "checkStatus", "(无)", newStatus.name(), submittedBy, now, "创建查寝记录 - " + studentName);
        } else if (oldStatus != newStatus) {
            writeLog(recordId, "checkStatus",
                    oldStatus != null ? oldStatus.name() : "(无)",
                    newStatus.name(), submittedBy, now, "提交更新 - " + studentName);
        }

        if (!isNew) {
            String oldR = oldRemark != null ? oldRemark : "(无)";
            String newR = newRemark != null ? newRemark : "(无)";
            if (!oldR.equals(newR)) {
                writeLog(recordId, "备注", oldR, newR, submittedBy, now, "提交更新 - " + studentName);
            }

            String oldP = (oldProof != null && !oldProof.isBlank()) ? "有证明" : "(无)";
            String newP = (newProof != null && !newProof.isBlank()) ? "有证明" : "(无)";
            if (!oldP.equals(newP)) {
                writeLog(recordId, "证明照片", oldP, newP, submittedBy, now, "提交更新 - " + studentName);
            }
        }
    }

    private void writeLog(String checkRecordId, String fieldName,
                          String oldValue, String newValue,
                          String modifiedBy, LocalDateTime modifiedAt, String reason) {
        CheckRecordModifyLog logEntry = CheckRecordModifyLog.builder()
                .checkRecordId(checkRecordId)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .modifiedBy(modifiedBy)
                .modifiedAt(modifiedAt)
                .reason(reason)
                .build();
        modifyLogRepository.save(logEntry);
    }

    @Transactional
    public void batchSubmitRoomCheck(List<BatchRoomSubmission> submissions, String submittedBy) {
        if (submissions == null || submissions.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "提交数据不能为空");
        }
        Task task = null;
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
            ).collect(java.util.stream.Collectors.toList());

            task = doSubmitRoomCheck(sub.getTaskId(), sub.getRoomId(), items, submittedBy);
        }
        // 所有房间提交完成后，统一检查任务是否完成
        if (task != null) {
            updateTaskCompletionStatus(task);
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