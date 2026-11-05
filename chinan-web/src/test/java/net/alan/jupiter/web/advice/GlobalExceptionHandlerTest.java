package net.alan.jupiter.web.advice;

import net.alan.jupiter.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    @Test
    @DisplayName("业务错误码映射为语义正确的 HTTP 状态")
    void shouldMapBusinessCodeToHttpStatus() {
        assertEquals(HttpStatus.FORBIDDEN, GlobalExceptionHandler.statusOf(ErrorCode.FORBIDDEN.getCode()));
        assertEquals(HttpStatus.UNAUTHORIZED,
                GlobalExceptionHandler.statusOf(ErrorCode.TOKEN_INVALID.getCode()));
        assertEquals(HttpStatus.NOT_FOUND,
                GlobalExceptionHandler.statusOf(ErrorCode.TASK_NOT_FOUND.getCode()));
        assertEquals(HttpStatus.CONFLICT,
                GlobalExceptionHandler.statusOf(ErrorCode.USERNAME_DUPLICATE.getCode()));
        assertEquals(HttpStatus.BAD_REQUEST,
                GlobalExceptionHandler.statusOf(ErrorCode.BAD_REQUEST.getCode()));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                GlobalExceptionHandler.statusOf(ErrorCode.INTERNAL_ERROR.getCode()));
    }

    @Test
    @DisplayName("未登记的自定义错误码回退为 400")
    void shouldFallbackToBadRequestForUnknownCode() {
        assertEquals(HttpStatus.BAD_REQUEST, GlobalExceptionHandler.statusOf(9999));
    }
}
