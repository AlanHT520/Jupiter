package net.alan.jupiter.application.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccessGuardTest {

    @Test
    @DisplayName("从 {taskId}_{studentId} 解析任务ID")
    void shouldParseTaskIdFromCheckRecordKey() {
        assertEquals("task-1", AccessGuard.taskIdOfCheckRecordKey("task-1_student-9"));
        assertEquals("aaa", AccessGuard.taskIdOfCheckRecordKey("aaa_bbb_ccc"));
    }

    @Test
    @DisplayName("非法格式返回 null")
    void shouldReturnNullForInvalidKey() {
        assertNull(AccessGuard.taskIdOfCheckRecordKey(null));
        assertNull(AccessGuard.taskIdOfCheckRecordKey(""));
        assertNull(AccessGuard.taskIdOfCheckRecordKey("_student"));
        assertNull(AccessGuard.taskIdOfCheckRecordKey("nodash"));
    }
}
