package com.quwan.im.protocol;

import io.netty.buffer.ByteBuf;
import lombok.Data;

import java.nio.charset.StandardCharsets;

/**
 * 二进制消息基类
 * 提供通用的二进制序列化和反序列化方法
 */
@Data
public abstract class BinaryMessage {
    
    /**
     * 将消息序列化为ByteBuf
     */
    public abstract void encode(ByteBuf out);
    
    /**
     * 从ByteBuf反序列化消息
     */
    public abstract void decode(ByteBuf in);
    
    /**
     * 获取消息类型
     */
    public abstract byte getMessageType();
    
    /**
     * 写入字符串（长度+内容）
     */
    protected void writeString(ByteBuf out, String value) {
        if (value == null) {
            out.writeInt(0);
        } else {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            out.writeInt(bytes.length);
            out.writeBytes(bytes);
        }
    }
    
    /**
     * 读取字符串（长度+内容）
     */
    protected String readString(ByteBuf in) {
        int length = in.readInt();
        if (length == 0) {
            return null;
        }
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    
    /**
     * 写入长字符串（用于大文本内容）
     */
    protected void writeLongString(ByteBuf out, String value) {
        if (value == null) {
            out.writeShort(0);
        } else {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 65535) {
                throw new IllegalArgumentException("字符串长度超过65535字节");
            }
            out.writeShort(bytes.length);
            out.writeBytes(bytes);
        }
    }
    
    /**
     * 读取长字符串
     */
    protected String readLongString(ByteBuf in) {
        int length = in.readShort() & 0xFFFF;
        if (length == 0) {
            return null;
        }
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
