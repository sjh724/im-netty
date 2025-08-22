package com.quwan.im.model;

import lombok.Data;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * 自定义协议消息
 * 格式：魔数(4字节) + 版本(1字节) + 消息类型(1字节) + 数据长度(4字节) + 数据内容
 */
@Data
public class ProtocolMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    // 魔数：用于验证消息合法性
    public static final int MAGIC_NUMBER = 0x12345678;

    // 协议版本
    private byte version = 1;

    // 消息类型
    private byte type;

    // 数据内容（JSON格式的IMMessage）
    private String data;

    // 数据长度（用于解码）
    private int dataLength;

    public ProtocolMessage() {}

    public ProtocolMessage(byte type, String data) {
        this.type = type;
        this.data = data;
        this.dataLength = data != null ? data.getBytes(StandardCharsets.UTF_8).length : 0;
    }

}
