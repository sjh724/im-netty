package com.quwan.im;

import com.quwan.im.model.MessageType;
import com.quwan.im.model.ProtocolMessage;
import com.quwan.im.protocol.MessageDecoder;
import com.quwan.im.protocol.MessageEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * 简单的协议测试类
 * 可以直接运行验证编解码器是否工作正常
 */
public class SimpleProtocolTest {

    public static void main(String[] args) {
        System.out.println("开始测试协议编解码器...");
        
        try {
            // 创建测试消息
            ProtocolMessage originalMessage = new ProtocolMessage();
            originalMessage.setVersion((byte) 1);
            originalMessage.setType((byte) MessageType.LOGIN.getCode());
            originalMessage.setData("{\"username\":\"test\",\"password\":\"123456\"}");

            System.out.println("原始消息: " + originalMessage);
            System.out.println("消息类型: " + MessageType.fromCode(originalMessage.getType()).name());

            // 创建编码器和解码器
            MessageEncoder encoder = new MessageEncoder();
            MessageDecoder decoder = new MessageDecoder();

            // 创建嵌入式通道进行测试
            EmbeddedChannel channel = new EmbeddedChannel(encoder, decoder);

            // 编码消息
            System.out.println("开始编码消息...");
            channel.writeOutbound(originalMessage);
            ByteBuf encoded = channel.readOutbound();
            
            if (encoded != null) {
                System.out.println("编码成功，编码后字节数: " + encoded.readableBytes());
                
                // 打印编码后的字节内容
                byte[] bytes = new byte[encoded.readableBytes()];
                encoded.getBytes(0, bytes);
                System.out.print("编码后的字节内容: ");
                for (byte b : bytes) {
                    System.out.printf("%02X ", b);
                }
                System.out.println();
                
                // 重置ByteBuf以便解码
                encoded.resetReaderIndex();
                
                // 解码消息
                System.out.println("开始解码消息...");
                channel.writeInbound(encoded);
                ProtocolMessage decodedMessage = channel.readInbound();

                if (decodedMessage != null) {
                    System.out.println("解码成功！");
                    System.out.println("解码消息: " + decodedMessage);
                    
                    // 验证解码结果
                    boolean success = true;
                    if (originalMessage.getVersion() != decodedMessage.getVersion()) {
                        System.out.println("版本不匹配: 原始=" + originalMessage.getVersion() + ", 解码=" + decodedMessage.getVersion());
                        success = false;
                    }
                    if (originalMessage.getType() != decodedMessage.getType()) {
                        System.out.println("类型不匹配: 原始=" + originalMessage.getType() + ", 解码=" + decodedMessage.getType());
                        success = false;
                    }
                    if (!originalMessage.getData().equals(decodedMessage.getData())) {
                        System.out.println("数据不匹配: 原始=" + originalMessage.getData() + ", 解码=" + decodedMessage.getData());
                        success = false;
                    }
                    
                    if (success) {
                        System.out.println("✅ 编解码测试通过！");
                    } else {
                        System.out.println("❌ 编解码测试失败！");
                    }
                } else {
                    System.out.println("❌ 解码失败，解码结果为null");
                }
            } else {
                System.out.println("❌ 编码失败，编码结果为null");
            }

        } catch (Exception e) {
            System.out.println("❌ 测试过程中发生异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
