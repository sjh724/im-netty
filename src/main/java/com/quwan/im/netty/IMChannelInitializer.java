package com.quwan.im.netty;


import com.quwan.im.protocol.MessageDecoder;
import com.quwan.im.protocol.MessageEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.PipedReader;
import java.util.concurrent.TimeUnit;

@Component
public class IMChannelInitializer extends ChannelInitializer<SocketChannel> {

    // 消息最大长度限制（10MB）
    private static final int MAX_FRAME_LENGTH = 10 * 1024 * 1024;
    // 长度字段偏移量
    private static final int LENGTH_FIELD_OFFSET = 6;
    // 长度字段占用字节数
    private static final int LENGTH_FIELD_LENGTH = 4;
    // 长度调整值
    private static final int LENGTH_ADJUSTMENT = 0;
    // 跳过的初始字节数
    private static final int INITIAL_BYTES_TO_STRIP = 4;

    @Autowired
    private IMMessageHandler imMessageHandler;

    @Autowired
    private IMExceptionHandler exceptionHandler;
    @Autowired
    private HeartbeatHandler heartbeatHandler;

    /**
     * 初始化通道，配置处理器流水线
     */
    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        // 1. 粘包拆包处理器
        // 基于长度字段的帧解码器，解决TCP粘包问题
        pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
                MAX_FRAME_LENGTH,
                LENGTH_FIELD_OFFSET,
                LENGTH_FIELD_LENGTH,
                LENGTH_ADJUSTMENT,
                INITIAL_BYTES_TO_STRIP
        ));

//        // 长度字段预处理器，在消息前添加长度字段
//        pipeline.addLast("frameEncoder", new LengthFieldPrepender(LENGTH_FIELD_LENGTH));

        // 2. 字符串编解码器（处理文本消息）
//        pipeline.addLast("stringDecoder", new StringDecoder(CharsetUtil.UTF_8));
//        pipeline.addLast("stringEncoder", new StringEncoder(CharsetUtil.UTF_8));

        // 3. 自定义消息编解码器（处理IM消息对象）
        pipeline.addLast("messageDecoder", new MessageDecoder());
        pipeline.addLast("messageEncoder", new MessageEncoder());

        // 4. 心跳检测处理器
        // 读空闲时间：30秒（客户端30秒未发送消息）
        // 写空闲时间：0（不检测写空闲）
        // 读写空闲时间：0（不检测读写空闲）
        pipeline.addLast("idleStateHandler", new IdleStateHandler(
                30, 10, 5, TimeUnit.SECONDS
        ));

        // 自定义心跳处理器（处理空闲事件）
        pipeline.addLast("heartbeatHandler", heartbeatHandler);

        // 5. 业务逻辑处理器
        pipeline.addLast("imMessageHandler", imMessageHandler);

        // 6. 异常处理器（最后添加，捕获前面所有处理器的异常）
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