package com.quwan.im.protocol;


import com.quwan.im.model.ProtocolMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * 消息解码器：将ByteBuf解码为ProtocolMessage
 * 严格遵循协议格式：[魔数(4)][版本(1)][类型(1)][数据长度(4)][数据体(n)]
 */
public class MessageDecoder extends LengthFieldBasedFrameDecoder {
    private static final Logger logger = LoggerFactory.getLogger(MessageDecoder.class);

    // 最大帧长度（10MB，可根据业务调整）
    private static final int MAX_FRAME_LENGTH = 50 * 1024 * 1024;

    // 长度字段偏移量：魔数(4) + 版本(1) + 类型(1) = 6字节
    private static final int LENGTH_FIELD_OFFSET = 4 + 1 + 1;

    // 长度字段长度：4字节（int类型）
    private static final int LENGTH_FIELD_LENGTH = 4;

    // 长度调整值：0（长度字段仅表示数据体长度）
    private static final int LENGTH_ADJUSTMENT = 0;

    // 跳过的初始字节数：0（手动读取所有字段）
    private static final int INITIAL_BYTES_TO_STRIP = 0;

    public MessageDecoder() {
        super(MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH, LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        logger.debug("进入解码器，当前可读字节数: {}", in.readableBytes());
        System.out.println("进入解码器，当前可读字节数");
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

            // 3. 解析消息类型（4字节）
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

            // 6. 校验剩余字节是否足够（防止拆包导致的数组越界）
            if (frame.readableBytes() < dataLength) {
                logger.error("数据长度不匹配：声明长度{}，实际可读{}，关闭连接", dataLength, frame.readableBytes());
                ctx.channel().close();
                return null;
            }

            // 7. 解析数据体
            byte[] dataBytes = new byte[dataLength];
            frame.readBytes(dataBytes);
            String data = new String(dataBytes, StandardCharsets.UTF_8);

            // 7. 构建ProtocolMessage对象
            ProtocolMessage message = new ProtocolMessage();
            message.setVersion(version);
            message.setType(type);
            message.setDataLength(dataLength);
            message.setData(data);

            logger.debug("消息解析成功，类型: {}，数据长度: {}", type, dataLength);
            return message;

        } catch (Exception e) {
            logger.error("消息解码失败", e);
            ctx.channel().close(); // 解析失败关闭连接，避免资源泄漏
            return null;
        } finally {
            frame.release(); // 释放ByteBuf
        }
    }
}
