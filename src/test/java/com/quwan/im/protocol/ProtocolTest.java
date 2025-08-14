package com.quwan.im.protocol;

import com.quwan.im.model.MessageType;
import com.quwan.im.model.ProtocolMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 协议编解码器测试类
 * 验证服务端和客户端的编解码器是否一致
 */
public class ProtocolTest {

    @Test
    public void testMessageEncoderDecoder() {
        // 创建测试消息
        ProtocolMessage originalMessage = new ProtocolMessage();
        originalMessage.setVersion((byte) 1);
        originalMessage.setType((byte) MessageType.LOGIN.getCode());
        originalMessage.setData("{\"username\":\"test\",\"password\":\"123456\"}");

        // 创建编码器和解码器
        MessageEncoder encoder = new MessageEncoder();
        MessageDecoder decoder = new MessageDecoder();

        // 创建嵌入式通道进行测试
        EmbeddedChannel channel = new EmbeddedChannel(encoder, decoder);

        // 编码消息
        assertTrue(channel.writeOutbound(originalMessage));
        ByteBuf encoded = channel.readOutbound();

        // 解码消息
        assertTrue(channel.writeInbound(encoded));
        ProtocolMessage decodedMessage = channel.readInbound();

        // 验证解码结果
        assertNotNull(decodedMessage);
        assertEquals(originalMessage.getVersion(), decodedMessage.getVersion());
        assertEquals(originalMessage.getType(), decodedMessage.getType());
        assertEquals(originalMessage.getData(), decodedMessage.getData());
        assertEquals(originalMessage.getDataLength(), decodedMessage.getDataLength());

        System.out.println("编码解码测试通过！");
        System.out.println("原始消息: " + originalMessage);
        System.out.println("解码消息: " + decodedMessage);
    }

    @Test
    public void testProtocolFormat() {
        // 测试协议格式是否正确
        ProtocolMessage message = new ProtocolMessage();
        message.setVersion((byte) 1);
        message.setType((byte) MessageType.PING.getCode());
        message.setData("ping");

        MessageEncoder encoder = new MessageEncoder();
        EmbeddedChannel channel = new EmbeddedChannel(encoder);

        // 编码消息
        channel.writeOutbound(message);
        ByteBuf encoded = channel.readOutbound();

        // 验证协议格式
        assertEquals(ProtocolMessage.MAGIC_NUMBER, encoded.readInt()); // 魔数4字节
        assertEquals(1, encoded.readByte()); // 版本1字节
        assertEquals(MessageType.PING.getCode(), encoded.readByte()); // 类型1字节
        assertEquals(4, encoded.readInt()); // 数据长度4字节
        assertEquals("ping", encoded.readBytes(4).toString(java.nio.charset.StandardCharsets.UTF_8)); // 数据体

        System.out.println("协议格式测试通过！");
        System.out.println("消息总长度: " + (encoded.readableBytes() + 14) + " 字节");
    }

    @Test
    public void testClientServerCompatibility() {
        // 模拟客户端发送登录消息
        String loginData = "{\"username\":\"testuser\",\"password\":\"password123\"}";
        ProtocolMessage clientMessage = new ProtocolMessage(MessageType.LOGIN.getCode(), loginData);

        // 使用服务端的编解码器
        MessageEncoder serverEncoder = new MessageEncoder();
        MessageDecoder serverDecoder = new MessageDecoder();

        EmbeddedChannel serverChannel = new EmbeddedChannel(serverEncoder, serverDecoder);

        // 客户端编码，服务端解码
        assertTrue(serverChannel.writeOutbound(clientMessage));
        ByteBuf encoded = serverChannel.readOutbound();

        assertTrue(serverChannel.writeInbound(encoded));
        ProtocolMessage serverDecoded = serverChannel.readInbound();

        // 验证服务端能正确解码客户端消息
        assertNotNull(serverDecoded);
        assertEquals(MessageType.LOGIN.getCode(), serverDecoded.getType());
        assertEquals(loginData, serverDecoded.getData());

        System.out.println("客户端服务端兼容性测试通过！");
    }
}
