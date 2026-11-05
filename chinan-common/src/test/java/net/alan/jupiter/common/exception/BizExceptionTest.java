package net.alan.jupiter.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BizExceptionTest {

    @Test
    @DisplayName("通过错误码构造异常")
    void shouldCreateWithErrorCode() {
        BizException ex = new BizException(ErrorCode.STUDENT_NOT_FOUND);
        assertEquals(ErrorCode.STUDENT_NOT_FOUND.getCode(), ex.getCode());
        assertEquals(ErrorCode.STUDENT_NOT_FOUND.getMessage(), ex.getMessage());
    }

    @Test
    @DisplayName("通过错误码和详情构造异常")
    void shouldCreateWithErrorCodeAndDetail() {
        BizException ex = new BizException(ErrorCode.STUDENT_NOT_FOUND, "学号: 20240001");
        assertEquals(ErrorCode.STUDENT_NOT_FOUND.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("学号: 20240001"));
    }

    @Test
    @DisplayName("通过自定义码和消息构造异常")
    void shouldCreateWithCustomCodeAndMessage() {
        BizException ex = new BizException(9999, "自定义错误");
        assertEquals(9999, ex.getCode());
        assertEquals("自定义错误", ex.getMessage());
    }

    @Test
    @DisplayName("BizException 应继承 RuntimeException")
    void shouldExtendRuntimeException() {
        BizException ex = new BizException(ErrorCode.INTERNAL_ERROR);
        assertInstanceOf(RuntimeException.class, ex);
    }
}