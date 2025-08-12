package com.quwan.im.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionUtils {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionUtils.class);

    /**
     * 打印异常堆栈信息
     */
    public static String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try {
            throwable.printStackTrace(pw);
            return sw.toString();
        } finally {
            pw.close();
        }
    }

    /**
     * 安全关闭连接并记录日志
     */
    public static void safeClose(AutoCloseable closeable, String name) {
        if (closeable != null) {
            try {
                closeable.close();
                logger.info("成功关闭: {}", name);
            } catch (Exception e) {
                logger.error("关闭{}失败: {}", name, getStackTrace(e));
            }
        }
    }

    /**
     * 记录业务异常
     */
    public static void logBusinessError(String message, String userId) {
        logger.error("用户[{}]业务异常: {}", userId, message);
    }

    /**
     * 记录连接异常
     */
    public static void logConnectionError(String message, String remoteAddress) {
        logger.error("连接[{}]异常: {}", remoteAddress, message);
    }
}
