package com.quwan.im.protocol;

import com.quwan.im.model.ProtocolMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * 消息编码器
 * 将ProtocolMessage对象编码为字节流
 */
public class MessageEncoder extends MessageToByteEncoder<ProtocolMessage> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ProtocolMessage msg, ByteBuf out) throws Exception {
        // 写入魔数
        out.writeInt(ProtocolMessage.MAGIC_NUMBER);

        // 写入版本
        out.writeByte(msg.getVersion());

        // 写入消息类型
        out.writeByte(msg.getType());

        // 写入数据长度
        out.writeInt(msg.getDataLength());

        // 写入数据内容
        out.writeBytes(msg.getData().getBytes("UTF-8"));
    }
}
