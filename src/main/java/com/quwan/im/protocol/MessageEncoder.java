package com.quwan.im.protocol;

import com.quwan.im.model.ProtocolMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * 消息编码器：将ProtocolMessage编码为ByteBuf
 * 严格遵循协议格式：[魔数(4)][版本(1)][类型(4)][数据长度(4)][数据体(n)]
 */
public class MessageEncoder extends MessageToByteEncoder<ProtocolMessage> {
    private static final Logger logger = LoggerFactory.getLogger(MessageEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, ProtocolMessage msg, ByteBuf out) throws Exception {
        try {
            // 1. 写入魔数（4字节）
            out.writeInt(ProtocolMessage.MAGIC_NUMBER);
            logger.debug("编码魔数: 0x{}", Integer.toHexString(ProtocolMessage.MAGIC_NUMBER));

            // 2. 写入版本（1字节）
            byte version = msg.getVersion();
            out.writeByte(version);
            logger.debug("编码版本: {}", version);

            // 3. 写入消息类型（4字节，与int类型匹配）
            int type = msg.getType();
            out.writeInt(type);
            logger.debug("编码消息类型: {}", type);

            // 4. 处理数据并计算长度（避免null）
            String data = msg.getData();
            byte[] dataBytes = (data != null) ? data.getBytes(StandardCharsets.UTF_8) : new byte[0];
            int dataLength = dataBytes.length;

            // 5. 写入数据长度（4字节）
            out.writeInt(dataLength);
            logger.debug("编码数据长度: {} bytes", dataLength);

            // 6. 写入数据内容
            out.writeBytes(dataBytes);
            if (dataLength > 0) {
                String logData = dataLength > 100 ? data.substring(0, 100) + "..." : data;
                logger.debug("编码数据内容: {}", logData);
            }

        } catch (Exception e) {
            logger.error("消息编码失败", e);
            throw e; // 抛出异常由pipeline处理
        }
    }
}
