package com.quwan.im.model;


/**
 * 响应状态码枚举
 * 统一管理系统中所有响应状态码
 */
public enum ResultCode {
    // 成功
    SUCCESS(200, "操作成功"),

    // 系统错误
    SYSTEM_ERROR(500, "系统内部错误"),
    PARAM_ERROR(400, "参数错误"),
    AUTH_ERROR(401, "未授权"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),

    // 业务错误
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    PASSWORD_ERROR(1003, "密码错误"),
    FRIEND_ALREADY_EXISTS(1004, "已是好友"),
    FRIEND_REQUEST_EXISTS(1005, "好友请求已发送"),
    GROUP_NOT_FOUND(1006, "群组不存在"),
    NOT_GROUP_MEMBER(1007, "不是群成员"),
    NOT_GROUP_OWNER(1008, "不是群主"),

    // 网络错误
    NETWORK_ERROR(2001, "网络异常"),
    CONNECT_ERROR(2002, "连接失败"),
    TIMEOUT_ERROR(2003, "操作超时"),
    IO_ERROR(2004, "IO操作异常"),

    // 数据库错误
    DB_ERROR(3001, "数据库操作异常");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
