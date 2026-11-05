package net.alan.jupiter.web.advice;

import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.common.result.ApiResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * 统一异常出口：把异常映射为语义正确的 HTTP 状态码。
 * <p>约定：HTTP 状态码表达传输层语义，响应体 {@code code} 保留业务错误码，
 * 便于前端同时按状态码与业务码分支。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ------------------------------------------------------------------
    // 业务异常：按业务码映射 HTTP 状态
    // ------------------------------------------------------------------

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResult<Void>> handleBizException(BizException e) {
        HttpStatus status = statusOf(e.getCode());
        // 4xx 属于可预期的用户输入问题，只记录 warn；5xx 才需要完整堆栈
        if (status.is5xxServerError()) {
            log.error("业务异常(服务端): code={}, message={}", e.getCode(), e.getMessage(), e);
        } else {
            log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        }
        return ResponseEntity.status(status).body(ApiResult.error(e.getCode(), e.getMessage()));
    }

    static HttpStatus statusOf(int code) {
        ErrorCode errorCode = ErrorCode.fromCode(code);
        if (errorCode == null) {
            return HttpStatus.BAD_REQUEST;
        }
        return switch (errorCode) {
            case UNAUTHORIZED, TOKEN_EXPIRED, TOKEN_INVALID -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND, USER_NOT_FOUND, STUDENT_NOT_FOUND, ROOM_NOT_FOUND,
                 FLOOR_NOT_FOUND, BUILDING_NOT_FOUND, TASK_NOT_FOUND,
                 CHECK_RECORD_NOT_FOUND, FILE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT, USERNAME_DUPLICATE, STUDENT_DUPLICATE, CHECK_ALREADY_SUBMITTED ->
                    HttpStatus.CONFLICT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    // ------------------------------------------------------------------
    // 鉴权 / 授权
    // ------------------------------------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("无权限访问: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResult.error(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResult<Void>> handleAuthentication(AuthenticationException e) {
        log.warn("未认证访问: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResult.error(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage()));
    }

    // ------------------------------------------------------------------
    // 请求层错误
    // ------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数验证失败: {}", message);
        return ResponseEntity.badRequest().body(ApiResult.error(400, message));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MissingPathVariableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResult<Void>> handleMissingOrMismatch(Exception e) {
        log.warn("请求参数缺失或类型错误: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResult.error(400, "请求参数缺失或格式不正确"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResult.error(400, "请求体格式不正确"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResult<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数错误: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResult.error(400, "请求参数无效"));
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ApiResult<Void>> handleDateTimeParse(DateTimeParseException e) {
        log.warn("日期格式错误: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResult.error(400, "日期格式无效"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的请求方法: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResult.error(405, "不支持的请求方法"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleMediaType(HttpMediaTypeNotSupportedException e) {
        log.warn("不支持的媒体类型: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResult.error(415, "不支持的内容类型"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResult<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResult.error(413, "上传文件过大，请压缩后重试"));
    }

    // ------------------------------------------------------------------
    // 数据与资源
    // ------------------------------------------------------------------

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResult<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性冲突: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResult.error(ErrorCode.CONFLICT.getCode(), "数据冲突，请刷新后重试"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("静态资源不存在: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResult.error(404, "资源不存在"));
    }

    /** 路径穿越等安全校验失败（如文件路径越出上传根目录）。 */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResult<Void>> handleSecurity(SecurityException e) {
        log.warn("安全校验失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResult.error(ErrorCode.FORBIDDEN.getCode(), "非法的文件路径"));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResult<Void>> handleNoSuchElement(NoSuchElementException e) {
        log.warn("资源未找到: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResult.error(404, "资源不存在"));
    }

    // ------------------------------------------------------------------
    // 兜底
    // ------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleException(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.error(500, "服务器内部错误"));
    }
}
