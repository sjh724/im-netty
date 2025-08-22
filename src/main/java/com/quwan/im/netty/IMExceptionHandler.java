package com.quwan.im.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quwan.im.exception.IMBusinessException;
import com.quwan.im.exception.IMNettyException;
import com.quwan.im.model.*;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.net.ConnectException;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

/**
 * IM系统全局异常处理器
 * 统一捕获和处理系统中所有异常，返回标准化错误响应
 */
@ControllerAdvice
@ChannelHandler.Sharable
public class IMExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(IMExceptionHandler.class);

    /**
     * 处理自定义业务异常
     */
    @ExceptionHandler(IMBusinessException.class)
    @ResponseBody
    public Result handleBusinessException(IMBusinessException e) {
        logger.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理Netty通信异常
     */
    @ExceptionHandler(IMNettyException.class)
    @ResponseBody
    public Result handleNettyException(IMNettyException e) {
        logger.error("Netty通信异常", e);
        return Result.fail(ResultCode.NETWORK_ERROR, "网络通信异常: " + e.getMessage());
    }

    /**
     * 处理数据库访问异常
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseBody
    public Result handleDataAccessException(DataAccessException e) {
        logger.error("数据库访问异常", e);
        return Result.fail(ResultCode.DB_ERROR, "数据操作失败，请稍后重试");
    }

    /**
     * 处理SQL异常
     */
    @ExceptionHandler(SQLException.class)
    @ResponseBody
    public Result handleSQLException(SQLException e) {
        logger.error("SQL执行异常", e);
        return Result.fail(ResultCode.DB_ERROR, "数据库操作异常");
    }

    /**
     * 处理IO异常
     */
    @ExceptionHandler(IOException.class)
    @ResponseBody
    public Result handleIOException(IOException e) {
        logger.error("IO操作异常", e);
        return Result.fail(ResultCode.IO_ERROR, "文件操作异常");
    }

    /**
     * 处理网络连接异常
     */
    @ExceptionHandler(ConnectException.class)
    @ResponseBody
    public Result handleConnectException(ConnectException e) {
        logger.error("网络连接异常", e);
        return Result.fail(ResultCode.CONNECT_ERROR, "无法连接到服务器");
    }

    /**
     * 处理超时异常
     */
    @ExceptionHandler(TimeoutException.class)
    @ResponseBody
    public Result handleTimeoutException(TimeoutException e) {
        logger.warn("操作超时: {}", e.getMessage());
        return Result.fail(ResultCode.TIMEOUT_ERROR, "操作超时，请重试");
    }

    /**
     * 处理未捕获的通用异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Result handleGeneralException(Exception e) {
        logger.error("未捕获的系统异常", e);
        return Result.fail(ResultCode.SYSTEM_ERROR, "系统繁忙，请稍后再试");
    }

    /**
     * Netty通道中的异常处理（专门用于消息处理器中的异常）
     */
    public void handleChannelException(ChannelHandlerContext ctx, Throwable e) {
        String userId = ctx.channel().attr(IMMessageHandler.USER_ID_ATTRIBUTE).get();
        logger.error("用户[{}]的通道处理异常", userId, e);

        try {
            // 向客户端发送错误消息
            if (ctx.channel().isActive()) {
                String errorMsg = e instanceof IMBusinessException
                        ? e.getMessage()
                        : "服务器处理消息异常";

                IMMessage errorMessage = new IMMessage();
                errorMessage.setId(java.util.UUID.randomUUID().toString());
                errorMessage.setType(MessageType.ERROR_RESPONSE.getCode());
                errorMessage.setFrom("system");
                errorMessage.setContent(errorMsg);
                ProtocolMessage protocolMessage = new ProtocolMessage(
                        MessageType.ERROR_RESPONSE.getCode(),
                        new ObjectMapper().writeValueAsString(errorMessage)
                );
                ctx.writeAndFlush(protocolMessage);
            }
        } catch (Exception ex) {
            logger.error("发送错误响应失败", ex);
        } finally {
            // 关闭通道
            ctx.close();
        }
    }
}
