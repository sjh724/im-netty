package com.quwan.im.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 二进制消息编码器
 * 将BinaryProtocolMessage编码为ByteBuf
 */
public class BinaryMessageEncoder extends MessageToByteEncoder<BinaryProtocolMessage> {
    private static final Logger logger = LoggerFactory.getLogger(BinaryMessageEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, BinaryProtocolMessage msg, ByteBuf out) throws Exception {
        try {
            // 记录编码开始位置
            int startIndex = out.writerIndex();
            
            // 编码消息
            msg.encode(out);
            
            // 记录编码后的长度
            int encodedLength = out.writerIndex() - startIndex;
            
            logger.debug("二进制消息编码完成，类型: {}, 长度: {} bytes", 
                        msg.getType(), encodedLength);
                        
        } catch (Exception e) {
            logger.error("二进制消息编码失败", e);
            throw e;
        }
    }
}
