package com.quwan.im.exception;

/**
 * IM系统Netty通信异常
 * 用于表示网络通信过程中的错误（如连接断开、消息发送失败等）
 */
public class IMNettyException extends RuntimeException {
    public IMNettyException(String message) {
        super(message);
    }

    public IMNettyException(String message, Throwable cause) {
        super(message, cause);
    }
}
