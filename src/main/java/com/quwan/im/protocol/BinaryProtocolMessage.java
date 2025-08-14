package com.quwan.im.protocol;

import io.netty.buffer.ByteBuf;
import lombok.Data;

/**
 * 二进制协议消息
 * 优化后的协议格式，减少协议头开销
 */
@Data
public class BinaryProtocolMessage {
    
    // 协议常量
    public static final int MAGIC_NUMBER = 0x12345678;
    public static final byte PROTOCOL_VERSION = 2; // 二进制版本
    
    // 协议头字段
    private byte version = PROTOCOL_VERSION;
    private byte type;
    private int dataLength;
    private BinaryMessage data;
    
    public BinaryProtocolMessage() {}
    
    public BinaryProtocolMessage(byte type, BinaryMessage data) {
        this.type = type;
        this.data = data;
    }
    
    /**
     * 序列化为ByteBuf
     */
    public void encode(ByteBuf out) {
        // 写入魔数（4字节）
        out.writeInt(MAGIC_NUMBER);
        
        // 写入版本（1字节）
        out.writeByte(version);
        
        // 写入消息类型（1字节）
        out.writeByte(type);
        
        // 计算数据长度
        int dataStartIndex = out.writerIndex();
        
        // 写入数据长度占位符（4字节）
        out.writeInt(0);
        
        // 写入数据内容
        if (data != null) {
            data.encode(out);
            dataLength = out.writerIndex() - dataStartIndex - 4;
        } else {
            dataLength = 0;
        }
        
        // 回写实际数据长度
        out.setInt(dataStartIndex, dataLength);
    }
    
    /**
     * 从ByteBuf反序列化
     */
    public void decode(ByteBuf in) {
        // 验证魔数
        int magic = in.readInt();
        if (magic != MAGIC_NUMBER) {
            throw new IllegalArgumentException("无效魔数: " + Integer.toHexString(magic));
        }
        
        // 读取版本
        version = in.readByte();
        if (version != PROTOCOL_VERSION) {
            throw new IllegalArgumentException("不支持的协议版本: " + version);
        }
        
        // 读取消息类型
        type = in.readByte();
        
        // 读取数据长度
        dataLength = in.readInt();
        
        // 根据消息类型创建对应的数据对象
        data = createMessageByType(type);
        if (data != null) {
            data.decode(in);
        }
    }
    
    /**
     * 根据消息类型创建对应的消息对象
     */
    private BinaryMessage createMessageByType(byte type) {
        switch (type) {
            case 10: // SINGLE_CHAT
            case 20: // GROUP_CHAT
                return new BinaryIMMessage();
            // 可以添加其他消息类型
            default:
                return null;
        }
    }
    
    /**
     * 获取数据长度（字节）
     */
    public int getDataLengthBytes() {
        return dataLength;
    }
    
    /**
     * 获取完整消息长度（字节）
     */
    public int getTotalLengthBytes() {
        return 10 + dataLength; // 魔数(4) + 版本(1) + 类型(1) + 长度(4) + 数据
    }
}
