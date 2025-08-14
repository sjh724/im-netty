package com.quwan.im.protocol;

import com.quwan.im.model.MessageType;
import io.netty.buffer.ByteBuf;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 二进制IM消息
 * 优化后的消息格式，减少序列化开销
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BinaryIMMessage extends BinaryMessage {
    
    // 消息字段
    private String id;
    private String from;
    private String to;
    private String content;
    private byte type;  // 使用byte而不是MessageType枚举
    private long timestamp;
    private String groupId;
    private String extra;
    
    public BinaryIMMessage() {}
    
    public BinaryIMMessage(String id, String from, String to, String content, 
                          MessageType type, long timestamp, String groupId, String extra) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.content = content;
        this.type = type.getCode();
        this.timestamp = timestamp;
        this.groupId = groupId;
        this.extra = extra;
    }
    
    @Override
    public void encode(ByteBuf out) {
        // 按照固定顺序写入字段，提高解析效率
        writeString(out, id);           // 4字节长度 + 内容
        writeString(out, from);         // 4字节长度 + 内容
        writeString(out, to);           // 4字节长度 + 内容
        writeLongString(out, content);  // 2字节长度 + 内容（优化大文本）
        out.writeByte(type);            // 1字节类型
        out.writeLong(timestamp);       // 8字节时间戳
        writeString(out, groupId);      // 4字节长度 + 内容
        writeString(out, extra);        // 4字节长度 + 内容
    }
    
    @Override
    public void decode(ByteBuf in) {
        id = readString(in);
        from = readString(in);
        to = readString(in);
        content = readLongString(in);
        type = in.readByte();
        timestamp = in.readLong();
        groupId = readString(in);
        extra = readString(in);
    }
    
    @Override
    public byte getMessageType() {
        return type;
    }
    
    /**
     * 获取MessageType枚举
     */
    public MessageType getMessageTypeEnum() {
        return MessageType.fromCode(type);
    }
    
    /**
     * 设置MessageType枚举
     */
    public void setMessageType(MessageType messageType) {
        this.type = messageType.getCode();
    }
}
