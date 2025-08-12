package com.quwan.im.protocol;


import com.quwan.im.model.ProtocolMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * 消息解码器
 * 基于长度字段的帧解码器，解决TCP粘包/拆包问题
 */
public class MessageDecoder extends LengthFieldBasedFrameDecoder {
    // 最大帧长度
    private static final int MAX_FRAME_LENGTH = 1024 * 1024; // 1MB

    // 长度字段偏移量：魔数(4) + 版本(1) + 类型(1) = 6字节
    private static final int LENGTH_FIELD_OFFSET = 6;

    // 长度字段长度：4字节
    private static final int LENGTH_FIELD_LENGTH = 4;

    // 长度字段调整值
    private static final int LENGTH_ADJUSTMENT = 0;

    // 跳过的初始字节数
    private static final int INITIAL_BYTES_TO_STRIP = 0;

    public MessageDecoder() {
        super(MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH, LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        // 先调用父类方法获取整帧数据
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }

        // 验证魔数
        int magic = frame.readInt();
        if (magic != ProtocolMessage.MAGIC_NUMBER) {
            ctx.channel().close();
            throw new IllegalArgumentException("无效的魔数，可能是非法连接");
        }

        // 解析协议版本
        byte version = frame.readByte();

        // 解析消息类型
        byte type = frame.readByte();

        // 解析数据长度
        int dataLength = frame.readInt();

        // 解析数据内容
        byte[] dataBytes = new byte[dataLength];
        frame.readBytes(dataBytes);
        String data = new String(dataBytes, "UTF-8");

        // 构建协议消息对象
        ProtocolMessage message = new ProtocolMessage();
        message.setVersion(version);
        message.setType(type);
        message.setDataLength(dataLength);
        message.setData(data);

        return message;
    }
}
