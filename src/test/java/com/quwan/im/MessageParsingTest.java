package com.quwan.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quwan.im.model.MessageType;
import com.quwan.im.model.ProtocolMessage;
import com.quwan.im.protocol.BinaryMessageDecoder;
import com.quwan.im.protocol.BinaryMessageEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 消息解析测试
 * 验证客户端消息解析异常的修复
 */
public class MessageParsingTest {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageParsingTest.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private EmbeddedChannel serverChannel;
    private EmbeddedChannel clientChannel;

    @BeforeEach
    void setUp() {
        // 模拟服务端通道（包含解码器）
        serverChannel = new EmbeddedChannel(new BinaryMessageDecoder());
        
        // 模拟客户端通道（包含编码器）
        clientChannel = new EmbeddedChannel(new BinaryMessageEncoder());
    }

    @Test
    void testLoginMessageParsing() {
        logger.info("开始测试登录消息解析...");
        
        // 创建登录数据（JSON格式）
        Map<String, String> loginData = new HashMap<>();
        loginData.put("username", "Tom");
        loginData.put("password", "123456");
        
        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(loginData);
        } catch (Exception e) {
            fail("JSON序列化失败: " + e.getMessage());
            return;
        }
        
        logger.info("登录数据JSON: {}", jsonData);
        
        // 创建登录消息
        ProtocolMessage loginMessage = new ProtocolMessage(MessageType.LOGIN.getCode(), jsonData);
        loginMessage.setVersion((byte) 1);
        
        logger.info("登录消息: type={}, data={}", loginMessage.getType(), loginMessage.getData());
        
        // 客户端编码登录消息
        assertTrue(clientChannel.writeOutbound(loginMessage));
        ByteBuf encodedMessage = clientChannel.readOutbound();
        
        if (encodedMessage != null) {
            logger.info("编码后登录消息长度: {} bytes", encodedMessage.readableBytes());
            
            // 重置ByteBuf位置
            encodedMessage.resetReaderIndex();
            
            // 服务端解码登录消息
            assertTrue(serverChannel.writeInbound(encodedMessage));
            ProtocolMessage decodedMessage = serverChannel.readInbound();
            
            if (decodedMessage != null) {
                logger.info("解码成功: type={}, data={}", decodedMessage.getType(), decodedMessage.getData());
                
                // 验证解码结果
                assertEquals(loginMessage.getVersion(), decodedMessage.getVersion(), "版本不匹配");
                assertEquals(loginMessage.getType(), decodedMessage.getType(), "消息类型不匹配");
                assertEquals(loginMessage.getData(), decodedMessage.getData(), "数据内容不匹配");
                
                // 验证登录数据内容
                try {
                    Map<String, String> decodedLoginData = objectMapper.readValue(decodedMessage.getData(), Map.class);
                    assertEquals("Tom", decodedLoginData.get("username"), "用户名不匹配");
                    assertEquals("123456", decodedLoginData.get("password"), "密码不匹配");
                    
                    logger.info("✅ 登录数据验证成功:");
                    logger.info("  用户名: {}", decodedLoginData.get("username"));
                    logger.info("  密码: {}", decodedLoginData.get("password"));
                    
                } catch (Exception e) {
                    fail("JSON反序列化失败: " + e.getMessage());
                }
                
                logger.info("✅ 登录消息解析测试通过");
            } else {
                logger.error("❌ 解码失败，无法解析登录消息");
                fail("登录消息解码失败");
            }
        } else {
            logger.error("❌ 编码失败，无法生成登录消息二进制数据");
            fail("登录消息编码失败");
        }
    }

    @Test
    void testSimpleStringMessageParsing() {
        logger.info("开始测试简单字符串消息解析...");
        
        // 创建简单字符串消息
        String simpleData = "Hello World";
        ProtocolMessage simpleMessage = new ProtocolMessage(MessageType.PING.getCode(), simpleData);
        simpleMessage.setVersion((byte) 1);
        
        logger.info("简单消息: type={}, data={}", simpleMessage.getType(), simpleMessage.getData());
        
        // 客户端编码消息
        assertTrue(clientChannel.writeOutbound(simpleMessage));
        ByteBuf encodedMessage = clientChannel.readOutbound();
        
        if (encodedMessage != null) {
            // 重置ByteBuf位置
            encodedMessage.resetReaderIndex();
            
            // 服务端解码消息
            assertTrue(serverChannel.writeInbound(encodedMessage));
            ProtocolMessage decodedMessage = serverChannel.readInbound();
            
            if (decodedMessage != null) {
                logger.info("解码成功: type={}, data={}", decodedMessage.getType(), decodedMessage.getData());
                
                // 验证解码结果
                assertEquals(simpleMessage.getVersion(), decodedMessage.getVersion(), "版本不匹配");
                assertEquals(simpleMessage.getType(), decodedMessage.getType(), "消息类型不匹配");
                assertEquals(simpleMessage.getData(), decodedMessage.getData(), "数据内容不匹配");
                
                logger.info("✅ 简单字符串消息解析测试通过");
            } else {
                logger.error("❌ 解码失败，无法解析简单字符串消息");
                fail("简单字符串消息解码失败");
            }
        } else {
            logger.error("❌ 编码失败，无法生成简单字符串消息二进制数据");
            fail("简单字符串消息编码失败");
        }
    }

    @Test
    void testEmptyMessageParsing() {
        logger.info("开始测试空消息解析...");
        
        // 创建空消息
        ProtocolMessage emptyMessage = new ProtocolMessage(MessageType.PING.getCode(), "");
        emptyMessage.setVersion((byte) 1);
        
        logger.info("空消息: type={}, data={}", emptyMessage.getType(), emptyMessage.getData());
        
        // 客户端编码消息
        assertTrue(clientChannel.writeOutbound(emptyMessage));
        ByteBuf encodedMessage = clientChannel.readOutbound();
        
        if (encodedMessage != null) {
            // 重置ByteBuf位置
            encodedMessage.resetReaderIndex();
            
            // 服务端解码消息
            assertTrue(serverChannel.writeInbound(encodedMessage));
            ProtocolMessage decodedMessage = serverChannel.readInbound();
            
            if (decodedMessage != null) {
                logger.info("解码成功: type={}, data={}", decodedMessage.getType(), decodedMessage.getData());
                
                // 验证解码结果
                assertEquals(emptyMessage.getVersion(), decodedMessage.getVersion(), "版本不匹配");
                assertEquals(emptyMessage.getType(), decodedMessage.getType(), "消息类型不匹配");
                assertEquals(emptyMessage.getData(), decodedMessage.getData(), "数据内容不匹配");
                
                logger.info("✅ 空消息解析测试通过");
            } else {
                logger.error("❌ 解码失败，无法解析空消息");
                fail("空消息解码失败");
            }
        } else {
            logger.error("❌ 编码失败，无法生成空消息二进制数据");
            fail("空消息编码失败");
        }
    }

    @Test
    void testComplexJsonMessageParsing() {
        logger.info("开始测试复杂JSON消息解析...");
        
        // 创建复杂JSON数据
        Map<String, Object> complexData = new HashMap<>();
        complexData.put("action", "login");
        complexData.put("timestamp", System.currentTimeMillis());
//        complexData.put("user", Map.of("username", "Tom", "password", "123456"));
//        complexData.put("metadata", Map.of("version", "1.0", "platform", "web"));
        
        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(complexData);
        } catch (Exception e) {
            fail("JSON序列化失败: " + e.getMessage());
            return;
        }
        
        logger.info("复杂JSON数据: {}", jsonData);
        
        // 创建复杂JSON消息
        ProtocolMessage complexMessage = new ProtocolMessage(MessageType.LOGIN.getCode(), jsonData);
        complexMessage.setVersion((byte) 1);
        
        logger.info("复杂JSON消息: type={}, data={}", complexMessage.getType(), complexMessage.getData());
        
        // 客户端编码消息
        assertTrue(clientChannel.writeOutbound(complexMessage));
        ByteBuf encodedMessage = clientChannel.readOutbound();
        
        if (encodedMessage != null) {
            // 重置ByteBuf位置
            encodedMessage.resetReaderIndex();
            
            // 服务端解码消息
            assertTrue(serverChannel.writeInbound(encodedMessage));
            ProtocolMessage decodedMessage = serverChannel.readInbound();
            
            if (decodedMessage != null) {
                logger.info("解码成功: type={}, data={}", decodedMessage.getType(), decodedMessage.getData());
                
                // 验证解码结果
                assertEquals(complexMessage.getVersion(), decodedMessage.getVersion(), "版本不匹配");
                assertEquals(complexMessage.getType(), decodedMessage.getType(), "消息类型不匹配");
                assertEquals(complexMessage.getData(), decodedMessage.getData(), "数据内容不匹配");
                
                // 验证复杂JSON数据
                try {
                    Map<String, Object> decodedComplexData = objectMapper.readValue(decodedMessage.getData(), Map.class);
                    assertEquals("login", decodedComplexData.get("action"), "action字段不匹配");
                    assertNotNull(decodedComplexData.get("timestamp"), "timestamp字段应该存在");
                    assertNotNull(decodedComplexData.get("user"), "user字段应该存在");
                    assertNotNull(decodedComplexData.get("metadata"), "metadata字段应该存在");
                    
                    logger.info("✅ 复杂JSON数据验证成功");
                    
                } catch (Exception e) {
                    fail("复杂JSON反序列化失败: " + e.getMessage());
                }
                
                logger.info("✅ 复杂JSON消息解析测试通过");
            } else {
                logger.error("❌ 解码失败，无法解析复杂JSON消息");
                fail("复杂JSON消息解码失败");
            }
        } else {
            logger.error("❌ 编码失败，无法生成复杂JSON消息二进制数据");
            fail("复杂JSON消息编码失败");
        }
    }
}
