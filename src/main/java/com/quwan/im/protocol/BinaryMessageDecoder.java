package com.quwan.im.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quwan.im.model.IMMessage;
import com.quwan.im.model.MessageType;
import com.quwan.im.model.ProtocolMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * 二进制消息解码器：将ByteBuf解码为ProtocolMessage
 * 协议格式：[魔数(4)][版本(1)][类型(1)][数据长度(4)][数据体(n)]
 * 数据体格式：[消息类型(1)][消息ID长度(2)][消息ID][发送者长度(2)][发送者][接收者长度(2)][接收者][内容长度(4)][内容][时间戳(8)]
 */
public class BinaryMessageDecoder extends LengthFieldBasedFrameDecoder {
    private static final Logger logger = LoggerFactory.getLogger(BinaryMessageDecoder.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
        
        // 调用父类方法获取完整帧（处理粘包拆包）
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            logger.debug("帧数据不完整，等待后续数据");
            return null;
        }

        try {
            // 1. 验证魔数（4字节）
            int magic = frame.readInt();
            if (magic != ProtocolMessage.MAGIC_NUMBER) {
                logger.error("无效魔数，预期: 0x{}, 实际: 0x{}，关闭连接",
                        Integer.toHexString(ProtocolMessage.MAGIC_NUMBER),
                        Integer.toHexString(magic));
                ctx.channel().close();
                return null;
            }

            // 2. 解析版本（1字节）
            byte version = frame.readByte();
            logger.debug("解析到协议版本: {}", version);

            // 3. 解析消息类型（1字节）
            byte type = frame.readByte();
            logger.debug("解析到消息类型: {}", type);

            // 4. 解析数据长度（4字节）
            int dataLength = frame.readInt();
            logger.debug("解析到数据长度: {} bytes", dataLength);

            // 5. 验证数据长度合理性
            if (dataLength < 0 || dataLength > (MAX_FRAME_LENGTH - LENGTH_FIELD_OFFSET - LENGTH_FIELD_LENGTH)) {
                logger.error("数据长度异常: {} bytes（超过最大限制），关闭连接", dataLength);
                ctx.channel().close();
                return null;
            }

            // 6. 校验剩余字节是否足够
            if (frame.readableBytes() < dataLength) {
                logger.error("数据长度不匹配：声明长度{}，实际可读{}，关闭连接", dataLength, frame.readableBytes());
                ctx.channel().close();
                return null;
            }

            // 7. 解析数据体
            String data = decodeBinaryData(frame, dataLength);

            // 8. 构建ProtocolMessage对象
            ProtocolMessage message = new ProtocolMessage();
            message.setVersion(version);
            message.setType(type);
            message.setDataLength(dataLength);
            message.setData(data);

            logger.debug("二进制消息解析成功，类型: {}，数据长度: {}", type, dataLength);
            return message;

        } catch (Exception e) {
            logger.error("二进制消息解码失败", e);
            ctx.channel().close();
            return null;
        } finally {
            frame.release();
        }
    }

    /**
     * 解码二进制数据
     */
    private String decodeBinaryData(ByteBuf frame, int dataLength) throws Exception {
        if (dataLength == 0) {
            return "";
        }

        // 先尝试按普通字符串处理（JSON格式）
        try {
            byte[] dataBytes = new byte[dataLength];
            frame.readBytes(dataBytes);
            String jsonString = new String(dataBytes, StandardCharsets.UTF_8);
            
            // 验证是否为有效的JSON格式
            try {
                objectMapper.readTree(jsonString);
                logger.debug("数据是JSON格式，按字符串处理: {}", jsonString);
                return jsonString;
            } catch (Exception e) {
                // 不是JSON格式，尝试解析为IMMessage格式
                logger.debug("数据不是JSON格式，尝试解析为IMMessage格式");
                // 重置ByteBuf位置，重新读取
                frame.resetReaderIndex();
                frame.skipBytes(frame.readableBytes() - dataLength);
                
                IMMessage imMessage = decodeBinaryToIMMessage(frame);
                return objectMapper.writeValueAsString(imMessage);
            }
        } catch (Exception e) {
            logger.error("二进制数据解码失败", e);
            throw e;
        }
    }

    /**
     * 将二进制数据解码为IMMessage
     */
    private IMMessage decodeBinaryToIMMessage(ByteBuf frame) throws Exception {
        IMMessage imMessage = new IMMessage();
        
        // 消息类型（1字节）
        byte messageTypeCode = frame.readByte();
        imMessage.setType(MessageType.fromCode(messageTypeCode));
        
        // 消息ID（长度+内容）
        imMessage.setId(readString(frame));
        
        // 发送者（长度+内容）
        imMessage.setFrom(readString(frame));
        
        // 接收者（长度+内容）
        imMessage.setTo(readString(frame));
        
        // 群组ID（长度+内容，可选）
        imMessage.setGroupId(readString(frame));
        
        // 消息内容（长度+内容）
        imMessage.setContent(readString(frame));
        
        // 时间戳（8字节）
        imMessage.setTimestamp(frame.readLong());
        
        return imMessage;
    }

    /**
     * 读取字符串（长度+内容）
     */
    private String readString(ByteBuf frame) {
        short length = frame.readShort();
        if (length == 0) {
            return null;
        }
        byte[] bytes = new byte[length];
        frame.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
