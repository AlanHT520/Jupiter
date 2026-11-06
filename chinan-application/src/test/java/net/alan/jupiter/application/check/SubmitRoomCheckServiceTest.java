package net.alan.jupiter.application.check;

import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.enums.TaskStatus;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.domain.model.Counselor;
import net.alan.jupiter.domain.model.DormBuilding;
import net.alan.jupiter.domain.model.Floor;
import net.alan.jupiter.domain.model.Room;
import net.alan.jupiter.domain.model.Student;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.domain.repository.CheckRecordModifyLogRepository;
import net.alan.jupiter.domain.repository.CheckRecordRepository;
import net.alan.jupiter.domain.repository.CounselorRepository;
import net.alan.jupiter.domain.repository.RoomRepository;
import net.alan.jupiter.domain.repository.StudentRepository;
import net.alan.jupiter.domain.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 提交查寝的服务端幂等（clientOpId）与迟到标记（late）测试。
 * 使用 Mockito 直接驱动 Service，不启动 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubmitRoomCheckServiceTest {

    @Mock
    private CheckRecordRepository checkRecordRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private CounselorRepository counselorRepository;
    @Mock
    private CheckRecordModifyLogRepository modifyLogRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SubmitRoomCheckService service;

    private DormBuilding building;
    private Room room;
    private Student student;
    private Student secondStudent;
    private Task task;
    private Counselor counselor;

    @BeforeEach
    void setUp() {
        service = new SubmitRoomCheckService(checkRecordRepository, roomRepository, studentRepository,
                taskRepository, counselorRepository, modifyLogRepository, eventPublisher);

        building = DormBuilding.builder().id("building-1").name("1号楼").build();
        Floor floor = Floor.builder().id("floor-1").building(building).floorNumber(1).build();
        room = Room.builder().id("room-1").roomNumber("101").floor(floor).building(building).build();
        student = Student.builder().id("stu-1").studentNo("2024001").name("张三")
                .room(room).roomNumber("101").build();
        secondStudent = Student.builder().id("stu-2").studentNo("2024002").name("李四")
                .room(room).roomNumber("101").build();
        counselor = Counselor.builder().id("user-1").name("王老师").role("COUNSELOR").build();
        task = Task.builder()
                .id("task-1")
                .title("11月查寝")
                .status(TaskStatus.PENDING)
                .taskDate(LocalDate.now().minusDays(2))
                .building(building)
                .assignedCounselor(counselor)
                .totalRooms(1)
                .checkedRooms(0)
                .build();
    }

    @Test
    @DisplayName("同一 clientOpId 提交两次：只落一条记录、只发一次事件，第二次返回首次结果")
    void shouldReturnFirstResultWhenSameClientOpIdSubmittedTwice() {
        stubHappyPath();

        List<CheckRecord> first = service.submitRoomCheck(
                "task-1", "room-1", items(), "user-1", "op-1");

        assertEquals(1, first.size());
        CheckRecord firstRecord = first.get(0);
        assertEquals("op-1", firstRecord.getClientOpId());
        assertNotNull(firstRecord.getSubmittedAt());
        assertNotNull(firstRecord.getId());

        // 模拟首次提交已落库：同一 clientOpId 再次提交（离线重试）
        when(checkRecordRepository.findByClientOpId("op-1")).thenReturn(first);
        List<CheckRecord> second = service.submitRoomCheck(
                "task-1", "room-1", items(), "user-1", "op-1");

        assertSame(firstRecord, second.get(0), "幂等命中应直接返回首次落库的实体");
        verify(checkRecordRepository, times(1)).saveAll(anyList());
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
        verify(taskRepository, times(1)).incrementCheckedRooms("task-1");
        verify(checkRecordRepository, times(2)).findByClientOpId("op-1");
    }

    @Test
    @DisplayName("taskDate 早于提交当天：标记 late=true，submittedAt 为服务器接收时间")
    void shouldMarkLateWhenTaskDateBeforeToday() {
        stubHappyPath();
        task.setTaskDate(LocalDate.now().minusDays(3));
        LocalDateTime before = LocalDateTime.now();

        List<CheckRecord> result = service.submitRoomCheck(
                "task-1", "room-1", items(), "user-1", "op-late");

        LocalDateTime after = LocalDateTime.now();
        assertTrue(result.get(0).getLate(), "补交历史任务应标记迟到");
        LocalDateTime submittedAt = result.get(0).getSubmittedAt();
        assertFalse(submittedAt.isBefore(before), "submittedAt 不能早于服务端处理时间");
        assertFalse(submittedAt.isAfter(after), "submittedAt 不能晚于服务端处理时间");
        assertEquals(LocalDate.now(), submittedAt.toLocalDate());
    }

    @Test
    @DisplayName("taskDate 等于提交当天：标记 late=false")
    void shouldNotMarkLateWhenTaskDateIsToday() {
        stubHappyPath();
        task.setTaskDate(LocalDate.now());

        List<CheckRecord> result = service.submitRoomCheck(
                "task-1", "room-1", items(), "user-1", "op-today");

        assertFalse(result.get(0).getLate(), "当天提交不算迟到");
    }

    @Test
    @DisplayName("taskDate 为空：不标记迟到")
    void shouldNotMarkLateWhenTaskDateMissing() {
        stubHappyPath();
        task.setTaskDate(null);

        List<CheckRecord> result = service.submitRoomCheck(
                "task-1", "room-1", items(), "user-1", "op-null-date");

        assertFalse(result.get(0).getLate());
    }

    @Test
    @DisplayName("旧签名（不带 clientOpId）仍然可用：clientOpId 为 null 且不查幂等表")
    void shouldKeepLegacySignatureWorking() {
        stubHappyPath();

        List<CheckRecord> result = service.submitRoomCheck(
                "task-1", "room-1", items(), "user-1");

        assertEquals(1, result.size());
        assertNull(result.get(0).getClientOpId());
        assertNotNull(result.get(0).getSubmittedAt());
        assertTrue(result.get(0).getLate(), "旧客户端提交同样按 taskDate 判定迟到");
        verify(checkRecordRepository, never()).findByClientOpId(anyString());
        verify(checkRecordRepository, times(1)).saveAll(anyList());
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("空白 clientOpId 视为未携带，不触发幂等查询")
    void shouldTreatBlankClientOpIdAsAbsent() {
        stubHappyPath();

        List<CheckRecord> result = service.submitRoomCheck(
                "task-1", "room-1", items(), "user-1", "   ");

        assertNull(result.get(0).getClientOpId());
        verify(checkRecordRepository, never()).findByClientOpId(anyString());
        verify(checkRecordRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("clientOpId 超过 64 字符：直接拒绝，不写库")
    void shouldRejectTooLongClientOpId() {
        stubHappyPath();
        String tooLong = "x".repeat(65);

        BizException ex = assertThrows(BizException.class, () ->
                service.submitRoomCheck("task-1", "room-1", items(), "user-1", tooLong));

        assertTrue(ex.getMessage().contains("64"));
        verify(checkRecordRepository, never()).saveAll(anyList());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("同一房间换 clientOpId：仍是覆盖 + 审计，不新增记录")
    void shouldOverwriteWithAuditWhenClientOpIdChanges() {
        CheckRecord existing = CheckRecord.builder()
                .id("record-old")
                .task(task)
                .room(room)
                .roomNumber("101")
                .student(student)
                .studentNo(student.getStudentNo())
                .studentName(student.getName())
                .checkStatus(CheckStatus.ABSENT)
                .submittedBy("user-1")
                .build();
        stubHappyPath();
        when(checkRecordRepository.findByTaskIdAndStudentIdIn(eq("task-1"), anyList()))
                .thenReturn(List.of(existing));
        when(checkRecordRepository.existsByTaskIdAndRoomId("task-1", "room-1")).thenReturn(true);

        List<CheckRecord> result = service.submitRoomCheck(
                "task-1", "room-1", items(), "user-1", "op-2");

        assertEquals(1, result.size());
        assertSame(existing, result.get(0), "覆盖语义：复用同一行，不新增记录");
        assertEquals(CheckStatus.PRESENT, existing.getCheckStatus());
        assertEquals("op-2", existing.getClientOpId(), "覆盖后幂等键更新为本次提交");
        verify(checkRecordRepository, times(1)).saveAll(anyList());
        verify(taskRepository, never()).incrementCheckedRooms(anyString());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        CheckRecordSubmittedEvent event = (CheckRecordSubmittedEvent) eventCaptor.getValue();
        assertEquals(1, event.getRecords().size());
        assertEquals(CheckStatus.ABSENT,
                event.getBeforeSnapshots().get("stu-1").checkStatus(),
                "审计需要拿到覆盖前的旧值快照");
    }

    @Test
    @DisplayName("一个房间多个学生共享同一 clientOpId：每个学生一行，均带该幂等键")
    void shouldShareClientOpIdAcrossStudentsOfSameSubmission() {
        stubHappyPath();
        when(studentRepository.findAllById(anyList())).thenReturn(List.of(student, secondStudent));

        List<SubmitRoomCheckService.StudentCheckItem> items = List.of(
                SubmitRoomCheckService.StudentCheckItem.builder()
                        .studentId("stu-1").checkStatus(CheckStatus.PRESENT).build(),
                SubmitRoomCheckService.StudentCheckItem.builder()
                        .studentId("stu-2").checkStatus(CheckStatus.ABSENT).build());

        List<CheckRecord> result = service.submitRoomCheck(
                "task-1", "room-1", items, "user-1", "op-room");

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> "op-room".equals(r.getClientOpId())));
        assertTrue(result.stream().allMatch(r -> Boolean.TRUE.equals(r.getLate())));
    }

    @Test
    @DisplayName("幂等键被其他房间占用：拒绝重放，避免读到他房数据")
    void shouldRejectReplayWhenClientOpIdBelongsToAnotherRoom() {
        Room otherRoom = Room.builder().id("room-2").roomNumber("102")
                .floor(room.getFloor()).building(building).build();
        CheckRecord otherRoomRecord = CheckRecord.builder()
                .id("record-other")
                .task(task)
                .room(otherRoom)
                .student(student)
                .checkStatus(CheckStatus.PRESENT)
                .build();
        when(checkRecordRepository.findByClientOpId("op-shared"))
                .thenReturn(List.of(otherRoomRecord));

        BizException ex = assertThrows(BizException.class, () ->
                service.submitRoomCheck("task-1", "room-1", items(), "user-1", "op-shared"));

        assertTrue(ex.getMessage().contains("clientOpId"));
        verify(checkRecordRepository, never()).saveAll(anyList());
    }

    private void stubHappyPath() {
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(counselorRepository.findById("user-1")).thenReturn(Optional.of(counselor));
        when(studentRepository.findAllById(anyList())).thenReturn(List.of(student));
        when(checkRecordRepository.findByTaskIdAndStudentIdIn(eq("task-1"), anyList()))
                .thenReturn(List.of());
        when(checkRecordRepository.existsByTaskIdAndRoomId("task-1", "room-1")).thenReturn(false);
        when(checkRecordRepository.findByClientOpId(anyString())).thenReturn(List.of());
        when(checkRecordRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<CheckRecord> toSave = invocation.getArgument(0);
            for (int i = 0; i < toSave.size(); i++) {
                toSave.get(i).setId("record-" + (i + 1));
            }
            return toSave;
        });
    }

    private List<SubmitRoomCheckService.StudentCheckItem> items() {
        return List.of(SubmitRoomCheckService.StudentCheckItem.builder()
                .studentId("stu-1")
                .checkStatus(CheckStatus.PRESENT)
                .build());
    }
}
