package com.quwan.im.netty;

import com.quwan.im.model.MessageType;
import com.quwan.im.model.ProtocolMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 心跳处理器
 * 处理通道空闲事件，发送心跳包或关闭不活跃连接
 */
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatHandler.class);

    /**
     * 处理空闲事件
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;

            // 读空闲（客户端长时间未发送消息）
            if (event.state() == IdleState.READER_IDLE) {
                String userId = IMMessageHandler.getUserIdFromChannel(ctx.channel());
                logger.warn("用户[{}]长时间未发送消息，关闭连接", userId);
                // 关闭连接
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * 发送心跳响应
     */
    public void sendPong(ChannelHandlerContext ctx) {
        try {
            ProtocolMessage pong = new ProtocolMessage(
                    MessageType.PONG.getCode(),
                    "pong"
            );
            ctx.writeAndFlush(pong);
        } catch (Exception e) {
            logger.error("发送心跳响应失败", e);
        }
    }
}
