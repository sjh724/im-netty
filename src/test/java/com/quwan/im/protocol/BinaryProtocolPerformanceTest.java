package com.quwan.im.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quwan.im.model.IMMessage;
import com.quwan.im.model.MessageType;
import com.quwan.im.model.ProtocolMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * 二进制协议性能测试
 * 对比JSON和二进制传输的效率
 */
public class BinaryProtocolPerformanceTest {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int TEST_ITERATIONS = 100000;
    
    @Test
    public void testSerializationPerformance() throws Exception {
        // 准备测试数据
        IMMessage jsonMessage = createTestMessage();
        BinaryIMMessage binaryMessage = createTestBinaryMessage();
        
        System.out.println("=== 序列化性能测试 ===");
        System.out.println("测试迭代次数: " + TEST_ITERATIONS);
        
        // JSON序列化测试
        long jsonStartTime = System.currentTimeMillis();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            String jsonString = objectMapper.writeValueAsString(jsonMessage);
            byte[] jsonBytes = jsonString.getBytes("UTF-8");
        }
        long jsonEndTime = System.currentTimeMillis();
        long jsonDuration = jsonEndTime - jsonStartTime;
        
        // 二进制序列化测试
        long binaryStartTime = System.currentTimeMillis();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            ByteBuf buffer = Unpooled.buffer();
            binaryMessage.encode(buffer);
            byte[] binaryBytes = new byte[buffer.readableBytes()];
            buffer.readBytes(binaryBytes);
            buffer.release();
        }
        long binaryEndTime = System.currentTimeMillis();
        long binaryDuration = binaryEndTime - binaryStartTime;
        
        // 输出结果
        System.out.println("JSON序列化耗时: " + jsonDuration + "ms");
        System.out.println("二进制序列化耗时: " + binaryDuration + "ms");
        System.out.println("性能提升: " + String.format("%.2f", (double) jsonDuration / binaryDuration) + "倍");
        
        // 数据大小对比
        String jsonString = objectMapper.writeValueAsString(jsonMessage);
        byte[] jsonBytes = jsonString.getBytes("UTF-8");
        
        ByteBuf buffer = Unpooled.buffer();
        binaryMessage.encode(buffer);
        byte[] binaryBytes = new byte[buffer.readableBytes()];
        buffer.readBytes(binaryBytes);
        buffer.release();
        
        System.out.println("\n=== 数据大小对比 ===");
        System.out.println("JSON数据大小: " + jsonBytes.length + " bytes");
        System.out.println("二进制数据大小: " + binaryBytes.length + " bytes");
        System.out.println("大小减少: " + String.format("%.2f", (double) jsonBytes.length / binaryBytes.length) + "倍");
        System.out.println("节省空间: " + String.format("%.1f", (1 - (double) binaryBytes.length / jsonBytes.length) * 100) + "%");
    }
    
    @Test
    public void testDeserializationPerformance() throws Exception {
        // 准备测试数据
        IMMessage jsonMessage = createTestMessage();
        BinaryIMMessage binaryMessage = createTestBinaryMessage();
        
        // 序列化数据
        String jsonString = objectMapper.writeValueAsString(jsonMessage);
        byte[] jsonBytes = jsonString.getBytes("UTF-8");
        
        ByteBuf buffer = Unpooled.buffer();
        binaryMessage.encode(buffer);
        byte[] binaryBytes = new byte[buffer.readableBytes()];
        buffer.readBytes(binaryBytes);
        buffer.release();
        
        System.out.println("=== 反序列化性能测试 ===");
        System.out.println("测试迭代次数: " + TEST_ITERATIONS);
        
        // JSON反序列化测试
        long jsonStartTime = System.currentTimeMillis();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            IMMessage decodedMessage = objectMapper.readValue(jsonString, IMMessage.class);
        }
        long jsonEndTime = System.currentTimeMillis();
        long jsonDuration = jsonEndTime - jsonStartTime;
        
        // 二进制反序列化测试
        long binaryStartTime = System.currentTimeMillis();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            ByteBuf testBuffer = Unpooled.wrappedBuffer(binaryBytes);
            BinaryIMMessage decodedMessage = new BinaryIMMessage();
            decodedMessage.decode(testBuffer);
            testBuffer.release();
        }
        long binaryEndTime = System.currentTimeMillis();
        long binaryDuration = binaryEndTime - binaryStartTime;
        
        // 输出结果
        System.out.println("JSON反序列化耗时: " + jsonDuration + "ms");
        System.out.println("二进制反序列化耗时: " + binaryDuration + "ms");
        System.out.println("性能提升: " + String.format("%.2f", (double) jsonDuration / binaryDuration) + "倍");
    }
    
    @Test
    public void testProtocolMessagePerformance() throws Exception {
        // 准备测试数据
        IMMessage jsonMessage = createTestMessage();
        BinaryIMMessage binaryMessage = createTestBinaryMessage();
        
        System.out.println("=== 协议消息性能测试 ===");
        System.out.println("测试迭代次数: " + TEST_ITERATIONS);
        
        // JSON协议消息测试
        long jsonStartTime = System.currentTimeMillis();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            String jsonString = objectMapper.writeValueAsString(jsonMessage);
            ProtocolMessage protocolMessage = new ProtocolMessage(MessageType.SINGLE_CHAT.getCode(), jsonString);
            // 模拟编码过程
            byte[] data = protocolMessage.getData().getBytes("UTF-8");
        }
        long jsonEndTime = System.currentTimeMillis();
        long jsonDuration = jsonEndTime - jsonStartTime;
        
        // 二进制协议消息测试
        long binaryStartTime = System.currentTimeMillis();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            BinaryProtocolMessage protocolMessage = new BinaryProtocolMessage((byte) 10, binaryMessage);
            ByteBuf buffer = Unpooled.buffer();
            protocolMessage.encode(buffer);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            buffer.release();
        }
        long binaryEndTime = System.currentTimeMillis();
        long binaryDuration = binaryEndTime - binaryStartTime;
        
        // 输出结果
        System.out.println("JSON协议消息耗时: " + jsonDuration + "ms");
        System.out.println("二进制协议消息耗时: " + binaryDuration + "ms");
        System.out.println("性能提升: " + String.format("%.2f", (double) jsonDuration / binaryDuration) + "倍");
    }
    
    private IMMessage createTestMessage() {
        IMMessage message = new IMMessage();
        message.setId(UUID.randomUUID().toString());
        message.setFrom("user123");
        message.setTo("user456");
        message.setContent("这是一条测试消息，包含中文字符和English characters，用于测试序列化性能。");
        message.setType(MessageType.SINGLE_CHAT);
        message.setTimestamp(System.currentTimeMillis());
        message.setGroupId(null);
        message.setExtra("额外信息");
        return message;
    }
    
    private BinaryIMMessage createTestBinaryMessage() {
        return new BinaryIMMessage(
            UUID.randomUUID().toString(),
            "user123",
            "user456",
            "这是一条测试消息，包含中文字符和English characters，用于测试序列化性能。",
            MessageType.SINGLE_CHAT,
            System.currentTimeMillis(),
            null,
            "额外信息"
        );
    }
}
