package com.quwan.im.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quwan.im.model.IMMessage;
import com.quwan.im.model.MessageType;
import com.quwan.im.model.ProtocolMessage;
import com.quwan.im.netty.IMClient;
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

    private static final Logger       logger       = LoggerFactory.getLogger(BinaryMessageDecoder.class);
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

        logger.debug("进入二进制解码器，可读字节: {}", in.readableBytes());

        // 获取完整帧（已按长度字段切分）
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }

        try {
            int startReaderIndex = frame.readerIndex();

            // 1) 魔数
            int magic = frame.readInt();
            if (magic != ProtocolMessage.MAGIC_NUMBER) {
                logger.error("[Decode] 无效魔数, expected=0x{}, actual=0x{}; 关闭连接", Integer.toHexString(ProtocolMessage.MAGIC_NUMBER), Integer.toHexString(magic));
                ctx.channel().close();
                return null;
            }

            // 2) 版本
            byte version = frame.readByte();
            // 3) 类型
            byte type = frame.readByte();
            // 4) 负载长度
            int dataLength = frame.readInt();

            if (dataLength < 0 || dataLength > (MAX_FRAME_LENGTH - LENGTH_FIELD_OFFSET - LENGTH_FIELD_LENGTH)) {
                logger.error("[Decode] 非法负载长度: {} (readerIndex={}, readable={})", dataLength, frame.readerIndex(), frame.readableBytes());
                ctx.channel().close();
                return null;
            }

            if (frame.readableBytes() < dataLength) {
                logger.error("[Decode] 长度不匹配: 声明={} 实际可读={} (headerReadAt={})", dataLength, frame.readableBytes(), startReaderIndex);
                ctx.channel().close();
                return null;
            }

            // 5) 解析数据体
            String data = decodeBinaryData(frame, dataLength);

            ProtocolMessage message = new ProtocolMessage();
            message.setVersion(version);
            message.setType(type);
            message.setDataLength(dataLength);
            message.setData(data);

            logger.debug("[Decode] OK type={}, version={}, len={}", type, version, dataLength);
            return message;
        } catch (Exception e) {
            // 打印数据体前最多64字节用于排障
            int previewLen = Math.min(64, in.readableBytes());
            byte[] preview = new byte[previewLen];
            int idx = in.readerIndex();
            in.getBytes(idx, preview);
            logger.error("[Decode] 解码异常，预览前{}字节: {}", previewLen, bytesToHex(preview), e);
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

        // 标记负载起始位置
        frame.markReaderIndex();

        // 尝试按 JSON（字符串）解析
        byte[] dataBytes = new byte[dataLength];
        frame.readBytes(dataBytes);
        String jsonString = new String(dataBytes, StandardCharsets.UTF_8);
        try {
            objectMapper.readTree(jsonString); // 有效 JSON
            logger.debug("[Decode] 负载为JSON字符串");
            return jsonString;
        } catch (Exception notJson) {
            // 回退到负载起始，按 IMMessage 二进制解析
            frame.resetReaderIndex();
            IMMessage imMessage = decodeBinaryToIMMessage(frame);
            logger.debug("[Decode] 负载为IMMessage二进制");
            return objectMapper.writeValueAsString(imMessage);
        }
    }

    private static String bytesToHex(byte[] bytes) {

        if (bytes == null || bytes.length == 0)
            return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
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

        // 额外字段（长度+内容）
        imMessage.setExtra(readString(frame));

        // 时间戳（8字节）
        imMessage.setTimestamp(frame.readLong());

        logger.debug("after decode msg:{}", imMessage);
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
