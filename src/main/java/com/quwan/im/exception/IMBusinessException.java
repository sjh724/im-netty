package com.quwan.im.exception;

import com.quwan.im.model.ResultCode;

/**
 * IM系统业务异常
 * 用于表示业务逻辑层面的错误（如用户不存在、权限不足等）
 */
public class IMBusinessException extends RuntimeException {
    private final int code; // 错误代码

    public IMBusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public IMBusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public IMBusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public int getCode() {
        return code;
    }
}
