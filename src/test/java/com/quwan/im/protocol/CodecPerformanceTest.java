package com.quwan.im.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quwan.im.model.IMMessage;
import com.quwan.im.model.MessageType;
import com.quwan.im.model.ProtocolMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 编解码性能对比测试
 */
public class CodecPerformanceTest {
    private static final Logger logger = LoggerFactory.getLogger(CodecPerformanceTest.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final BinaryMessageEncoder binaryEncoder = new BinaryMessageEncoder();
    private final BinaryMessageDecoder binaryDecoder = new BinaryMessageDecoder();
    private final MessageEncoder jsonEncoder = new MessageEncoder();
    private final MessageDecoder jsonDecoder = new MessageDecoder();

    @Test
    void testPerformanceComparison() throws Exception {
        // 准备测试数据
        List<ProtocolMessage> testMessages = prepareTestMessages(1000);
        
        logger.info("开始性能测试，消息数量: {}", testMessages.size());

        // 测试二进制编解码性能
        long binaryEncodeTime = testBinaryEncode(testMessages);
        long binaryDecodeTime = testBinaryDecode(testMessages);
        
        // 测试JSON编解码性能
        long jsonEncodeTime = testJsonEncode(testMessages);
        long jsonDecodeTime = testJsonDecode(testMessages);

        // 输出结果
        logger.info("=== 性能测试结果 ===");
        logger.info("二进制编码时间: {} ms", binaryEncodeTime);
        logger.info("二进制解码时间: {} ms", binaryDecodeTime);
        logger.info("JSON编码时间: {} ms", jsonEncodeTime);
        logger.info("JSON解码时间: {} ms", jsonDecodeTime);
        
        logger.info("=== 性能对比 ===");
        logger.info("编码性能提升: {:.2f}%", 
            (double)(jsonEncodeTime - binaryEncodeTime) / jsonEncodeTime * 100);
        logger.info("解码性能提升: {:.2f}%", 
            (double)(jsonDecodeTime - binaryDecodeTime) / jsonDecodeTime * 100);
        
        // 测试数据大小对比
        testDataSizeComparison(testMessages);
    }

    @Test
    void testSingleMessagePerformance() throws Exception {
        // 创建单个测试消息
        IMMessage imMessage = createTestIMMessage();
        String jsonData = objectMapper.writeValueAsString(imMessage);
        
        ProtocolMessage message = new ProtocolMessage();
        message.setVersion((byte) 1);
        message.setType((byte) 10);
        message.setData(jsonData);

        logger.info("=== 单消息性能测试 ===");
        
        // 测试编码性能
        int iterations = 100000;
        
        // 二进制编码
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            ByteBuf encoded = Unpooled.buffer();
            binaryEncoder.encode(null, message, encoded);
            encoded.release();
        }
        long binaryEncodeTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        
        // JSON编码
        startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            ByteBuf encoded = Unpooled.buffer();
            jsonEncoder.encode(null, message, encoded);
            encoded.release();
        }
        long jsonEncodeTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        
        logger.info("单消息编码性能 ({}次迭代):", iterations);
        logger.info("二进制编码: {} ms", binaryEncodeTime);
        logger.info("JSON编码: {} ms", jsonEncodeTime);
        logger.info("性能提升: {:.2f}%", 
            (double)(jsonEncodeTime - binaryEncodeTime) / jsonEncodeTime * 100);
    }

    @Test
    void testMemoryUsage() throws Exception {
        // 测试内存使用情况
        List<ProtocolMessage> messages = prepareTestMessages(1000);
        
        // 强制GC
        System.gc();
        Thread.sleep(1000);
        
        long beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        // 二进制编码
        List<ByteBuf> binaryResults = new ArrayList<>();
        for (ProtocolMessage msg : messages) {
            ByteBuf encoded = Unpooled.buffer();
            binaryEncoder.encode(null, msg, encoded);
            binaryResults.add(encoded);
        }
        
        long afterBinaryMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        // 释放二进制结果
        for (ByteBuf buf : binaryResults) {
            buf.release();
        }
        binaryResults.clear();
        
        // JSON编码
        List<ByteBuf> jsonResults = new ArrayList<>();
        for (ProtocolMessage msg : messages) {
            ByteBuf encoded = Unpooled.buffer();
            jsonEncoder.encode(null, msg, encoded);
            jsonResults.add(encoded);
        }
        
        long afterJsonMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        // 释放JSON结果
        for (ByteBuf buf : jsonResults) {
            buf.release();
        }
        jsonResults.clear();
        
        logger.info("=== 内存使用对比 ===");
        logger.info("二进制编码内存使用: {} KB", (afterBinaryMemory - beforeMemory) / 1024);
        logger.info("JSON编码内存使用: {} KB", (afterJsonMemory - beforeMemory) / 1024);
        logger.info("内存节省: {:.2f}%", 
            (double)((afterJsonMemory - beforeMemory) - (afterBinaryMemory - beforeMemory)) / 
            (afterJsonMemory - beforeMemory) * 100);
    }

    private List<ProtocolMessage> prepareTestMessages(int count) throws Exception {
        List<ProtocolMessage> messages = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            IMMessage imMessage = createTestIMMessage();
            imMessage.setId("msg_" + i);
            imMessage.setContent("Test message content " + i + " with some additional text to make it longer.");
            
            String jsonData = objectMapper.writeValueAsString(imMessage);
            
            ProtocolMessage message = new ProtocolMessage();
            message.setVersion((byte) 1);
            message.setType((byte) (i % 2 == 0 ? 10 : 20)); // 交替使用单聊和群聊
            message.setData(jsonData);
            
            messages.add(message);
        }
        
        return messages;
    }

    private IMMessage createTestIMMessage() {
        IMMessage imMessage = new IMMessage();
        imMessage.setId("test_msg_" + System.currentTimeMillis());
        imMessage.setType(MessageType.SINGLE_CHAT);
        imMessage.setFrom("user1");
        imMessage.setTo("user2");
        imMessage.setContent("This is a test message for performance comparison between binary and JSON encoding.");
        imMessage.setTimestamp(System.currentTimeMillis());
        return imMessage;
    }

    private long testBinaryEncode(List<ProtocolMessage> messages) throws Exception {
        long startTime = System.currentTimeMillis();
        
        for (ProtocolMessage msg : messages) {
            ByteBuf encoded = Unpooled.buffer();
            binaryEncoder.encode(null, msg, encoded);
            encoded.release();
        }
        
        return System.currentTimeMillis() - startTime;
    }

    private long testBinaryDecode(List<ProtocolMessage> messages) throws Exception {
        // 先编码所有消息
        List<ByteBuf> encodedMessages = new ArrayList<>();
        for (ProtocolMessage msg : messages) {
            ByteBuf encoded = Unpooled.buffer();
            binaryEncoder.encode(null, msg, encoded);
            encodedMessages.add(encoded);
        }
        
        long startTime = System.currentTimeMillis();
        
        for (ByteBuf encoded : encodedMessages) {
            ByteBuf copy = encoded.copy();
            binaryDecoder.decode(null, copy);
            copy.release();
        }
        
        // 释放资源
        for (ByteBuf buf : encodedMessages) {
            buf.release();
        }
        
        return System.currentTimeMillis() - startTime;
    }

    private long testJsonEncode(List<ProtocolMessage> messages) throws Exception {
        long startTime = System.currentTimeMillis();
        
        for (ProtocolMessage msg : messages) {
            ByteBuf encoded = Unpooled.buffer();
            jsonEncoder.encode(null, msg, encoded);
            encoded.release();
        }
        
        return System.currentTimeMillis() - startTime;
    }

    private long testJsonDecode(List<ProtocolMessage> messages) throws Exception {
        // 先编码所有消息
        List<ByteBuf> encodedMessages = new ArrayList<>();
        for (ProtocolMessage msg : messages) {
            ByteBuf encoded = Unpooled.buffer();
            jsonEncoder.encode(null, msg, encoded);
            encodedMessages.add(encoded);
        }
        
        long startTime = System.currentTimeMillis();
        
        for (ByteBuf encoded : encodedMessages) {
            ByteBuf copy = encoded.copy();
            jsonDecoder.decode(null, copy);
            copy.release();
        }
        
        // 释放资源
        for (ByteBuf buf : encodedMessages) {
            buf.release();
        }
        
        return System.currentTimeMillis() - startTime;
    }

    private void testDataSizeComparison(List<ProtocolMessage> messages) throws Exception {
        long totalBinarySize = 0;
        long totalJsonSize = 0;
        
        for (ProtocolMessage msg : messages) {
            // 二进制编码
            ByteBuf binaryEncoded = Unpooled.buffer();
            binaryEncoder.encode(null, msg, binaryEncoded);
            totalBinarySize += binaryEncoded.readableBytes();
            binaryEncoded.release();
            
            // JSON编码
            ByteBuf jsonEncoded = Unpooled.buffer();
            jsonEncoder.encode(null, msg, jsonEncoded);
            totalJsonSize += jsonEncoded.readableBytes();
            jsonEncoded.release();
        }
        
        logger.info("=== 数据大小对比 ===");
        logger.info("二进制编码总大小: {} bytes", totalBinarySize);
        logger.info("JSON编码总大小: {} bytes", totalJsonSize);
        logger.info("数据压缩率: {:.2f}%", 
            (double)(totalJsonSize - totalBinarySize) / totalJsonSize * 100);
        logger.info("平均每条消息大小:");
        logger.info("  二进制: {} bytes", totalBinarySize / messages.size());
        logger.info("  JSON: {} bytes", totalJsonSize / messages.size());
    }
}
