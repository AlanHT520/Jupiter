package net.alan.jupiter.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    USER_NOT_FOUND(1001, "用户不存在"),
    PASSWORD_ERROR(1002, "密码错误"),
    TOKEN_EXPIRED(1003, "令牌已过期"),
    TOKEN_INVALID(1004, "令牌无效"),
    USERNAME_DUPLICATE(1005, "用户名已存在"),

    STUDENT_NOT_FOUND(2001, "学生不存在"),
    STUDENT_DUPLICATE(2002, "学生已存在"),
    ROOM_NOT_FOUND(2003, "房间不存在"),
    FLOOR_NOT_FOUND(2004, "楼层不存在"),
    BUILDING_NOT_FOUND(2005, "宿舍楼不存在"),

    TASK_NOT_FOUND(3001, "任务不存在"),
    TASK_ALREADY_ASSIGNED(3002, "任务已分配"),
    TASK_STATUS_ERROR(3003, "任务状态异常"),

    CHECK_RECORD_NOT_FOUND(4001, "查寝记录不存在"),
    CHECK_ALREADY_SUBMITTED(4002, "该房间已提交查寝结果"),

    IMPORT_ERROR(5001, "导入失败"),
    EXPORT_ERROR(5002, "导出失败"),
    FILE_UPLOAD_ERROR(5003, "文件上传失败"),
    FILE_NOT_FOUND(5004, "文件不存在"),
    EXCEL_FORMAT_ERROR(5005, "Excel格式错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}