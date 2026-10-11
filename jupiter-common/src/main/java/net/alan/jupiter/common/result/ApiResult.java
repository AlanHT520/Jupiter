package net.alan.jupiter.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.alan.jupiter.common.exception.ErrorCode;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResult<T> {

    private int code;
    private String message;
    private T data;

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(ErrorCode.SUCCESS.getCode(), "success", data);
    }

    public static <T> ApiResult<T> success() {
        return new ApiResult<>(ErrorCode.SUCCESS.getCode(), "success", null);
    }

    public static <T> ApiResult<T> error(int code, String message) {
        return new ApiResult<>(code, message, null);
    }

    public static <T> ApiResult<T> error(String message) {
        return new ApiResult<>(ErrorCode.INTERNAL_ERROR.getCode(), message, null);
    }

    public boolean isSuccess() {
        return code == ErrorCode.SUCCESS.getCode();
    }
}