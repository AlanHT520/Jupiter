package net.alan.jupiter.application.security;

import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.domain.model.Counselor;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.domain.repository.CounselorRepository;
import net.alan.jupiter.domain.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessGuardTaskAccessTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CounselorRepository counselorRepository;

    private AccessGuard accessGuard;

    @BeforeEach
    void setUp() {
        accessGuard = new AccessGuard(taskRepository, counselorRepository);
    }

    private Task taskOf(String ownerId) {
        Counselor owner = Counselor.builder().id(ownerId).name("查宿员").build();
        return Task.builder().id("t1").title("任务").assignedCounselor(owner).build();
    }

    @Test
    @DisplayName("管理员可访问任意任务")
    void adminCanAccessAnyTask() {
        when(taskRepository.findById("t1")).thenReturn(Optional.of(taskOf("owner-1")));
        when(counselorRepository.findById("admin-1"))
                .thenReturn(Optional.of(Counselor.builder().id("admin-1").role("ADMIN").build()));

        assertDoesNotThrow(() -> accessGuard.requireTaskAccess("admin-1", "t1"));
    }

    @Test
    @DisplayName("任务归属人可访问")
    void ownerCanAccessOwnTask() {
        when(taskRepository.findById("t1")).thenReturn(Optional.of(taskOf("owner-1")));
        when(counselorRepository.findById("owner-1"))
                .thenReturn(Optional.of(Counselor.builder().id("owner-1").role("COUNSELOR").build()));

        assertDoesNotThrow(() -> accessGuard.requireTaskAccess("owner-1", "t1"));
    }

    @Test
    @DisplayName("其他查宿员访问任务被拒绝")
    void otherCounselorIsForbidden() {
        when(taskRepository.findById("t1")).thenReturn(Optional.of(taskOf("owner-1")));
        when(counselorRepository.findById("other-1"))
                .thenReturn(Optional.of(Counselor.builder().id("other-1").role("COUNSELOR").build()));

        assertThrows(BizException.class, () -> accessGuard.requireTaskAccess("other-1", "t1"));
    }

    @Test
    @DisplayName("任务不存在时抛出业务异常")
    void missingTaskThrows() {
        when(taskRepository.findById("t1")).thenReturn(Optional.empty());
        assertThrows(BizException.class, () -> accessGuard.requireTaskAccess("any", "t1"));
    }
}
