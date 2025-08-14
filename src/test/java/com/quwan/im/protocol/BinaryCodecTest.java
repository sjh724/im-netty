package com.quwan.im.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quwan.im.model.IMMessage;
import com.quwan.im.model.MessageType;
import com.quwan.im.model.ProtocolMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 二进制编解码器测试类
 */
public class BinaryCodecTest {
    private static final Logger logger = LoggerFactory.getLogger(BinaryCodecTest.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private EmbeddedChannel channel;
    private BinaryMessageEncoder encoder;
    private BinaryMessageDecoder decoder;

    @BeforeEach
    void setUp() {
        encoder = new BinaryMessageEncoder();
        decoder = new BinaryMessageDecoder();
        channel = new EmbeddedChannel(decoder, encoder);
    }

    @Test
    void testEncodeDecodeSimpleMessage() throws Exception {
        // 创建简单的ProtocolMessage
        ProtocolMessage originalMessage = new ProtocolMessage();
        originalMessage.setVersion((byte) 1);
        originalMessage.setType((byte) 10); // SINGLE_CHAT
        originalMessage.setData("Hello, World!");

        // 编码
        ByteBuf encoded = Unpooled.buffer();
        encoder.encode(null, originalMessage, encoded);

        logger.info("编码后字节数: {}", encoded.readableBytes());
        logByteBuf("编码结果", encoded);

        // 解码
        ByteBuf copy = encoded.copy();
        ProtocolMessage decodedMessage = (ProtocolMessage) decoder.decode(null, copy);

        // 验证
        assertNotNull(decodedMessage);
        assertEquals(originalMessage.getVersion(), decodedMessage.getVersion());
        assertEquals(originalMessage.getType(), decodedMessage.getType());
        assertEquals(originalMessage.getData(), decodedMessage.getData());

        logger.info("解码成功: {}", decodedMessage);
    }

    @Test
    void testEncodeDecodeIMMessage() throws Exception {
        // 创建IMMessage
        IMMessage imMessage = new IMMessage();
        imMessage.setId("msg_123456");
        imMessage.setType(MessageType.SINGLE_CHAT);
        imMessage.setFrom("user1");
        imMessage.setTo("user2");
        imMessage.setContent("Hello, this is a test message!");
        imMessage.setTimestamp(System.currentTimeMillis());

        // 转换为JSON字符串
        String jsonData = objectMapper.writeValueAsString(imMessage);

        // 创建ProtocolMessage
        ProtocolMessage originalMessage = new ProtocolMessage();
        originalMessage.setVersion((byte) 1);
        originalMessage.setType((byte) 10); // SINGLE_CHAT
        originalMessage.setData(jsonData);

        // 编码
        ByteBuf encoded = Unpooled.buffer();
        encoder.encode(null, originalMessage, encoded);

        logger.info("IMMessage编码后字节数: {}", encoded.readableBytes());
        logByteBuf("IMMessage编码结果", encoded);

        // 解码
        ByteBuf copy = encoded.copy();
        ProtocolMessage decodedMessage = (ProtocolMessage) decoder.decode(null, copy);

        // 验证
        assertNotNull(decodedMessage);
        assertEquals(originalMessage.getVersion(), decodedMessage.getVersion());
        assertEquals(originalMessage.getType(), decodedMessage.getType());

        // 验证IMMessage内容
        IMMessage decodedImMessage = objectMapper.readValue(decodedMessage.getData(), IMMessage.class);
        assertEquals(imMessage.getId(), decodedImMessage.getId());
        assertEquals(imMessage.getFrom(), decodedImMessage.getFrom());
        assertEquals(imMessage.getTo(), decodedImMessage.getTo());
        assertEquals(imMessage.getContent(), decodedImMessage.getContent());
        assertEquals(imMessage.getTimestamp(), decodedImMessage.getTimestamp());

        logger.info("IMMessage解码成功: {}", decodedImMessage);
    }

    @Test
    void testEncodeDecodeGroupMessage() throws Exception {
        // 创建群聊消息
        IMMessage groupMessage = new IMMessage();
        groupMessage.setId("group_msg_789");
        groupMessage.setType(MessageType.GROUP_CHAT);
        groupMessage.setFrom("user1");
        groupMessage.setGroupId("group_001");
        groupMessage.setContent("Hello everyone in the group!");
        groupMessage.setTimestamp(System.currentTimeMillis());

        String jsonData = objectMapper.writeValueAsString(groupMessage);

        ProtocolMessage originalMessage = new ProtocolMessage();
        originalMessage.setVersion((byte) 1);
        originalMessage.setType((byte) 20); // GROUP_CHAT
        originalMessage.setData(jsonData);

        // 编码
        ByteBuf encoded = Unpooled.buffer();
        encoder.encode(null, originalMessage, encoded);

        logger.info("群聊消息编码后字节数: {}", encoded.readableBytes());
        logByteBuf("群聊消息编码结果", encoded);

        // 解码
        ByteBuf copy = encoded.copy();
        ProtocolMessage decodedMessage = (ProtocolMessage) decoder.decode(null, copy);

        // 验证
        assertNotNull(decodedMessage);
        IMMessage decodedGroupMessage = objectMapper.readValue(decodedMessage.getData(), IMMessage.class);
        assertEquals(groupMessage.getGroupId(), decodedGroupMessage.getGroupId());
        assertEquals(groupMessage.getType(), decodedGroupMessage.getType());

        logger.info("群聊消息解码成功: {}", decodedGroupMessage);
    }

    @Test
    void testEncodeDecodeEmptyMessage() throws Exception {
        // 测试空消息
        ProtocolMessage originalMessage = new ProtocolMessage();
        originalMessage.setVersion((byte) 1);
        originalMessage.setType((byte) 4); // PING
        originalMessage.setData("");

        // 编码
        ByteBuf encoded = Unpooled.buffer();
        encoder.encode(null, originalMessage, encoded);

        logger.info("空消息编码后字节数: {}", encoded.readableBytes());
        logByteBuf("空消息编码结果", encoded);

        // 解码
        ByteBuf copy = encoded.copy();
        ProtocolMessage decodedMessage = (ProtocolMessage) decoder.decode(null, copy);

        // 验证
        assertNotNull(decodedMessage);
        assertEquals(originalMessage.getData(), decodedMessage.getData());

        logger.info("空消息解码成功");
    }

    @Test
    void testEncodeDecodeLargeMessage() throws Exception {
        // 创建大消息
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("This is a large message content for testing binary encoding and decoding. ");
        }

        IMMessage largeMessage = new IMMessage();
        largeMessage.setId("large_msg_" + System.currentTimeMillis());
        largeMessage.setType(MessageType.SINGLE_CHAT);
        largeMessage.setFrom("user1");
        largeMessage.setTo("user2");
        largeMessage.setContent(largeContent.toString());
        largeMessage.setTimestamp(System.currentTimeMillis());

        String jsonData = objectMapper.writeValueAsString(largeMessage);

        ProtocolMessage originalMessage = new ProtocolMessage();
        originalMessage.setVersion((byte) 1);
        originalMessage.setType((byte) 10);
        originalMessage.setData(jsonData);

        // 编码
        ByteBuf encoded = Unpooled.buffer();
        encoder.encode(null, originalMessage, encoded);

        logger.info("大消息编码后字节数: {}", encoded.readableBytes());

        // 解码
        ByteBuf copy = encoded.copy();
        ProtocolMessage decodedMessage = (ProtocolMessage) decoder.decode(null, copy);

        // 验证
        assertNotNull(decodedMessage);
        IMMessage decodedLargeMessage = objectMapper.readValue(decodedMessage.getData(), IMMessage.class);
        assertEquals(largeMessage.getContent(), decodedLargeMessage.getContent());

        logger.info("大消息解码成功，内容长度: {}", decodedLargeMessage.getContent().length());
    }

    @Test
    void testChannelPipeline() throws Exception {
        // 测试完整的Channel流水线
        IMMessage imMessage = new IMMessage();
        imMessage.setId("pipeline_test");
        imMessage.setType(MessageType.SINGLE_CHAT);
        imMessage.setFrom("user1");
        imMessage.setTo("user2");
        imMessage.setContent("Pipeline test message");
        imMessage.setTimestamp(System.currentTimeMillis());

        String jsonData = objectMapper.writeValueAsString(imMessage);

        ProtocolMessage originalMessage = new ProtocolMessage();
        originalMessage.setVersion((byte) 1);
        originalMessage.setType((byte) 10);
        originalMessage.setData(jsonData);

        // 通过Channel发送
        assertTrue(channel.writeOutbound(originalMessage));

        // 获取编码后的数据
        ByteBuf encoded = channel.readOutbound();
        assertNotNull(encoded);

        logger.info("Channel编码后字节数: {}", encoded.readableBytes());

        // 通过Channel解码
        assertTrue(channel.writeInbound(encoded));
        ProtocolMessage decodedMessage = channel.readInbound();

        // 验证
        assertNotNull(decodedMessage);
        assertEquals(originalMessage.getVersion(), decodedMessage.getVersion());
        assertEquals(originalMessage.getType(), decodedMessage.getType());
        assertEquals(originalMessage.getData(), decodedMessage.getData());

        logger.info("Channel流水线测试成功");
    }

    /**
     * 打印ByteBuf内容（用于调试）
     */
    private void logByteBuf(String prefix, ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), bytes);
        
        StringBuilder hex = new StringBuilder();
        StringBuilder ascii = new StringBuilder();
        
        for (int i = 0; i < Math.min(bytes.length, 100); i++) {
            hex.append(String.format("%02X ", bytes[i]));
            ascii.append(bytes[i] >= 32 && bytes[i] < 127 ? (char) bytes[i] : '.');
            
            if ((i + 1) % 16 == 0) {
                hex.append(" ");
                ascii.append(" ");
            }
        }
        
        logger.info("{} - Hex: {}", prefix, hex.toString());
        logger.info("{} - ASCII: {}", prefix, ascii.toString());
        
        if (bytes.length > 100) {
            logger.info("{} - ... (truncated, total {} bytes)", prefix, bytes.length);
        }
    }
}
