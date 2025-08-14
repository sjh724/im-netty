package com.quwan.im.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quwan.im.model.IMMessage;
import com.quwan.im.model.MessageType;
import com.quwan.im.model.ProtocolMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * 简单的编解码测试程序
 * 可以直接运行验证二进制编解码功能
 */
public class SimpleCodecTest {
    
    public static void main(String[] args) {
        try {
            System.out.println("=== 开始二进制编解码测试 ===");
            
            // 创建测试消息
            IMMessage imMessage = new IMMessage();
            imMessage.setId("test_msg_001");
            imMessage.setType(MessageType.SINGLE_CHAT);
            imMessage.setFrom("user1");
            imMessage.setTo("user2");
            imMessage.setContent("Hello, this is a test message for binary encoding!");
            imMessage.setTimestamp(System.currentTimeMillis());
            
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonData = objectMapper.writeValueAsString(imMessage);
            
            // 创建ProtocolMessage
            ProtocolMessage originalMessage = new ProtocolMessage();
            originalMessage.setVersion((byte) 1);
            originalMessage.setType((byte) 10); // SINGLE_CHAT
            originalMessage.setData(jsonData);
            
            System.out.println("原始消息: " + originalMessage);
            System.out.println("JSON数据: " + jsonData);
            
            // 创建编解码器
            BinaryMessageEncoder encoder = new BinaryMessageEncoder();
            BinaryMessageDecoder decoder = new BinaryMessageDecoder();
            
            // 编码
            ByteBuf encoded = Unpooled.buffer();
            encoder.encode(null, originalMessage, encoded);
            
            System.out.println("编码后字节数: " + encoded.readableBytes());
            printByteBuf("编码结果", encoded);
            
            // 解码
            ByteBuf copy = encoded.copy();
            ProtocolMessage decodedMessage = (ProtocolMessage) decoder.decode(null, copy);
            
            if (decodedMessage != null) {
                System.out.println("解码成功: " + decodedMessage);
                
                // 验证数据
                if (originalMessage.getVersion() == decodedMessage.getVersion() &&
                    originalMessage.getType() == decodedMessage.getType() &&
                    originalMessage.getData().equals(decodedMessage.getData())) {
                    System.out.println("✅ 编解码验证成功！");
                } else {
                    System.out.println("❌ 编解码验证失败！");
                }
                
                // 验证IMMessage内容
                IMMessage decodedImMessage = objectMapper.readValue(decodedMessage.getData(), IMMessage.class);
                System.out.println("解码后的IMMessage: " + decodedImMessage);
                
                if (imMessage.getId().equals(decodedImMessage.getId()) &&
                    imMessage.getFrom().equals(decodedImMessage.getFrom()) &&
                    imMessage.getTo().equals(decodedImMessage.getTo()) &&
                    imMessage.getContent().equals(decodedImMessage.getContent())) {
                    System.out.println("✅ IMMessage内容验证成功！");
                } else {
                    System.out.println("❌ IMMessage内容验证失败！");
                }
                
            } else {
                System.out.println("❌ 解码失败！");
            }
            
            // 测试群聊消息
            System.out.println("\n=== 测试群聊消息 ===");
            testGroupMessage();
            
            // 测试空消息
            System.out.println("\n=== 测试空消息 ===");
            testEmptyMessage();
            
            System.out.println("\n=== 二进制编解码测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("测试过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testGroupMessage() throws Exception {
        IMMessage groupMessage = new IMMessage();
        groupMessage.setId("group_msg_001");
        groupMessage.setType(MessageType.GROUP_CHAT);
        groupMessage.setFrom("user1");
        groupMessage.setGroupId("group_001");
        groupMessage.setContent("Hello everyone in the group!");
        groupMessage.setTimestamp(System.currentTimeMillis());
        
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonData = objectMapper.writeValueAsString(groupMessage);
        
        ProtocolMessage message = new ProtocolMessage();
        message.setVersion((byte) 1);
        message.setType((byte) 20); // GROUP_CHAT
        message.setData(jsonData);
        
        BinaryMessageEncoder encoder = new BinaryMessageEncoder();
        BinaryMessageDecoder decoder = new BinaryMessageDecoder();
        
        ByteBuf encoded = Unpooled.buffer();
        encoder.encode(null, message, encoded);
        
        ByteBuf copy = encoded.copy();
        ProtocolMessage decoded = (ProtocolMessage) decoder.decode(null, copy);
        
        if (decoded != null) {
            IMMessage decodedGroup = objectMapper.readValue(decoded.getData(), IMMessage.class);
            System.out.println("群聊消息编解码成功: " + decodedGroup.getGroupId());
        }
    }
    
    private static void testEmptyMessage() throws Exception {
        ProtocolMessage message = new ProtocolMessage();
        message.setVersion((byte) 1);
        message.setType((byte) 4); // PING
        message.setData("");
        
        BinaryMessageEncoder encoder = new BinaryMessageEncoder();
        BinaryMessageDecoder decoder = new BinaryMessageDecoder();
        
        ByteBuf encoded = Unpooled.buffer();
        encoder.encode(null, message, encoded);
        
        ByteBuf copy = encoded.copy();
        ProtocolMessage decoded = (ProtocolMessage) decoder.decode(null, copy);
        
        if (decoded != null && decoded.getData().equals("")) {
            System.out.println("空消息编解码成功");
        }
    }
    
    private static void printByteBuf(String prefix, ByteBuf buf) {
        byte[] bytes = new byte[Math.min(buf.readableBytes(), 50)];
        buf.getBytes(buf.readerIndex(), bytes);
        
        StringBuilder hex = new StringBuilder();
        StringBuilder ascii = new StringBuilder();
        
        for (int i = 0; i < bytes.length; i++) {
            hex.append(String.format("%02X ", bytes[i]));
            ascii.append(bytes[i] >= 32 && bytes[i] < 127 ? (char) bytes[i] : '.');
            
            if ((i + 1) % 16 == 0) {
                hex.append(" ");
                ascii.append(" ");
            }
        }
        
        System.out.println(prefix + " - Hex: " + hex.toString());
        System.out.println(prefix + " - ASCII: " + ascii.toString());
        
        if (buf.readableBytes() > 50) {
            System.out.println(prefix + " - ... (truncated, total " + buf.readableBytes() + " bytes)");
        }
    }
}
