package com.quwan.im.netty;


import com.quwan.im.protocol.BinaryMessageDecoder;
import com.quwan.im.protocol.BinaryMessageEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class IMChannelInitializer extends ChannelInitializer<SocketChannel> {

    // 旧的通用帧解码器配置已不再使用，改由 BinaryMessageDecoder 统一处理粘包/拆包

    @Autowired
    private IMMessageHandler imMessageHandler;

    @Autowired
    private IMExceptionHandler exceptionHandler;
    @Autowired
    private HeartbeatHandler   heartbeatHandler;

    /**
     * 初始化通道，配置处理器流水线
     */
    @Override
    protected void initChannel(SocketChannel ch) {

        ChannelPipeline pipeline = ch.pipeline();

        // 1. 自定义二进制消息编解码器（同时负责帧粘/拆包与消息体解析）
        pipeline.addLast("messageDecoder", new BinaryMessageDecoder());
        pipeline.addLast("messageEncoder", new BinaryMessageEncoder());

        // 2. 心跳检测处理器
        // 读空闲时间：30秒（客户端30秒未发送消息）
        // 写空闲时间：0（不检测写空闲）
        // 读写空闲时间：0（不检测读写空闲）
        pipeline.addLast("idleStateHandler", new IdleStateHandler(30, 10, 5, TimeUnit.SECONDS));

        // 3. 自定义心跳处理器（处理空闲事件）
        pipeline.addLast("heartbeatHandler", heartbeatHandler);

        // 4. 业务逻辑处理器
        pipeline.addLast("imMessageHandler", imMessageHandler);

        // 5. 异常处理器（最后添加，捕获前面所有处理器的异常）
        pipeline.addLast("exceptionHandler", new ChannelExceptionHandler(exceptionHandler));
    }

    /**
     * 通道异常处理器（内部类）
     * 用于捕获通道中的异常并委托给全局异常处理器
     */
    private static class ChannelExceptionHandler extends io.netty.channel.ChannelDuplexHandler {

        private final IMExceptionHandler exceptionHandler;

        public ChannelExceptionHandler(IMExceptionHandler exceptionHandler) {

            this.exceptionHandler = exceptionHandler;
        }

        @Override
        public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx, Throwable cause) {
            // 委托给全局异常处理器处理
            exceptionHandler.handleChannelException(ctx, cause);
        }
    }
}