package com.quwan.im.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quwan.im.model.IMMessage;
import com.quwan.im.model.ProtocolMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * 二进制消息编码器：将ProtocolMessage编码为二进制ByteBuf
 * 协议格式：[魔数(4)][版本(1)][类型(1)][数据长度(4)][数据体(n)]
 * 数据体格式：[消息类型(1)][消息ID长度(2)][消息ID][发送者长度(2)][发送者][接收者长度(2)][接收者][内容长度(4)][内容][时间戳(8)]
 */
public class BinaryMessageEncoder extends MessageToByteEncoder<ProtocolMessage> {
    private static final Logger logger = LoggerFactory.getLogger(BinaryMessageEncoder.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

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

            // 3. 写入消息类型（1字节）
            byte type = msg.getType();
            out.writeByte(type);
            logger.debug("编码消息类型: {}", type);

            // 4. 处理数据并计算长度
            byte[] dataBytes = encodeDataToBinary(msg);
            int dataLength = dataBytes.length;

            // 5. 写入数据长度（4字节）
            out.writeInt(dataLength);
            logger.debug("编码数据长度: {} bytes", dataLength);

            // 6. 写入二进制数据内容
            out.writeBytes(dataBytes);
            logger.debug("编码二进制数据完成，长度: {} bytes", dataLength);

        } catch (Exception e) {
            logger.error("二进制消息编码失败", e);
            throw e;
        }
    }

    /**
     * 将消息数据编码为二进制格式
     */
    private byte[] encodeDataToBinary(ProtocolMessage msg) throws Exception {
        String data = msg.getData();
        if (data == null || data.isEmpty()) {
            return new byte[0];
        }

        // 尝试解析为IMMessage对象
        try {
            IMMessage imMessage = objectMapper.readValue(data, IMMessage.class);
            return encodeIMMessageToBinary(imMessage);
        } catch (Exception e) {
            // 如果不是IMMessage，则按普通字符串处理
            logger.debug("数据不是IMMessage格式，按字符串处理: {}", data);
            return data.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * 将IMMessage编码为二进制格式
     * 格式：
     * [msgType(1)]
     * [idLen(2)+id]
     * [fromLen(2)+from]
     * [toLen(2)+to]
     * [groupIdLen(2)+groupId]
     * [contentLen(2)+content]
     * [extraLen(2)+extra]
     * [timestamp(8)]
     */
    private byte[] encodeIMMessageToBinary(IMMessage imMessage) {
        ByteBuf buf = Unpooled.buffer();
        try {
            // 消息类型（1字节）
            buf.writeByte(imMessage.getType().getCode());
            
            // 消息ID（长度+内容）
            writeString(buf, imMessage.getId());
            
            // 发送者（长度+内容）
            writeString(buf, imMessage.getFrom());
            
            // 接收者（长度+内容）
            writeString(buf, imMessage.getTo());
            
            // 群组ID（长度+内容，可选）
            writeString(buf, imMessage.getGroupId());
            
            // 消息内容（长度+内容）
            writeString(buf, imMessage.getContent());
            
            // 额外字段（长度+内容），用于承载状态等轻量信息
            writeString(buf, imMessage.getExtra());
            
            // 时间戳（8字节）
            buf.writeLong(imMessage.getTimestamp());
            
            // 转换为字节数组
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
            
        } finally {
            buf.release();
        }
    }

    /**
     * 写入字符串（长度+内容）
     */
    private void writeString(ByteBuf buf, String str) {
        if (str == null) {
            buf.writeShort(0); // 长度为0
        } else {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            buf.writeShort(bytes.length); // 长度（2字节）
            buf.writeBytes(bytes); // 内容
        }
    }
}
