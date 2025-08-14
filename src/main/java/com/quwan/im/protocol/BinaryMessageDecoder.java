package com.quwan.im.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 二进制消息解码器
 * 将ByteBuf解码为BinaryProtocolMessage
 */
public class BinaryMessageDecoder extends LengthFieldBasedFrameDecoder {
    private static final Logger logger = LoggerFactory.getLogger(BinaryMessageDecoder.class);

    // 最大帧长度（50MB）
    private static final int MAX_FRAME_LENGTH = 50 * 1024 * 1024;

    // 长度字段偏移量：魔数(4) + 版本(1) + 类型(1) = 6字节
    private static final int LENGTH_FIELD_OFFSET = 6;

    // 长度字段长度：4字节
    private static final int LENGTH_FIELD_LENGTH = 4;

    // 长度调整值：0
    private static final int LENGTH_ADJUSTMENT = 0;

    // 跳过的初始字节数：0（手动读取所有字段）
    private static final int INITIAL_BYTES_TO_STRIP = 0;

    public BinaryMessageDecoder() {
        super(MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH, LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        logger.debug("进入二进制解码器，当前可读字节数: {}", in.readableBytes());

        // 调用父类方法获取完整帧
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            logger.debug("帧数据不完整，等待后续数据");
            return null;
        }

        try {
            // 创建协议消息对象
            BinaryProtocolMessage message = new BinaryProtocolMessage();
            
            // 解码消息
            message.decode(frame);
            
            logger.debug("二进制消息解码成功，类型: {}, 数据长度: {} bytes", 
                        message.getType(), message.getDataLengthBytes());
            
            return message;

        } catch (Exception e) {
            logger.error("二进制消息解码失败", e);
            ctx.channel().close();
            return null;
        } finally {
            frame.release();
        }
    }
}
