package com.quwan.im.protocol;

import com.quwan.im.model.MessageType;
import com.quwan.im.netty.IMMessageHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 二进制消息处理器
 * 处理BinaryProtocolMessage，委托给原有的IMMessageHandler
 */
@Component
@ChannelHandler.Sharable
public class BinaryMessageHandler extends SimpleChannelInboundHandler<BinaryProtocolMessage> {
    
    private static final Logger logger = LoggerFactory.getLogger(BinaryMessageHandler.class);
    
    @Autowired
    private IMMessageHandler imMessageHandler;
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BinaryProtocolMessage msg) throws Exception {
        try {
            // 获取消息类型
            byte type = msg.getType();
            BinaryMessage data = msg.getData();
            
            logger.debug("收到二进制消息，类型: {}, 数据: {}", type, data);
            
            // 根据消息类型处理
            switch (type) {
                case 0: // LOGIN
                    handleLogin(ctx, data);
                    break;
                case 10: // SINGLE_CHAT
                    handleSingleChat(ctx, data);
                    break;
                case 20: // GROUP_CHAT
                    handleGroupChat(ctx, data);
                    break;
                case 4: // PING
                    handlePing(ctx);
                    break;
                default:
                    logger.warn("未处理的二进制消息类型: {}", type);
            }
            
        } catch (Exception e) {
            logger.error("处理二进制消息失败", e);
        }
    }
    
    /**
     * 处理登录消息
     */
    private void handleLogin(ChannelHandlerContext ctx, BinaryMessage data) {
        // 这里需要根据具体的登录消息格式处理
        logger.info("处理二进制登录消息");
        // 委托给原有的登录处理逻辑
    }
    
    /**
     * 处理单聊消息
     */
    private void handleSingleChat(ChannelHandlerContext ctx, BinaryMessage data) {
        if (data instanceof BinaryIMMessage) {
            BinaryIMMessage imMsg = (BinaryIMMessage) data;
            logger.info("处理二进制单聊消息: {} -> {}", imMsg.getFrom(), imMsg.getTo());
            // 委托给原有的单聊处理逻辑
        }
    }
    
    /**
     * 处理群聊消息
     */
    private void handleGroupChat(ChannelHandlerContext ctx, BinaryMessage data) {
        if (data instanceof BinaryIMMessage) {
            BinaryIMMessage imMsg = (BinaryIMMessage) data;
            logger.info("处理二进制群聊消息: {} -> {}", imMsg.getFrom(), imMsg.getGroupId());
            // 委托给原有的群聊处理逻辑
        }
    }
    
    /**
     * 处理心跳消息
     */
    private void handlePing(ChannelHandlerContext ctx) {
        logger.debug("处理二进制心跳消息");
        // 发送PONG响应
        BinaryProtocolMessage pongMsg = new BinaryProtocolMessage((byte) 5, null); // PONG
        ctx.writeAndFlush(pongMsg);
    }
}
