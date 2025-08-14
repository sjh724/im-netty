package com.quwan.im.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quwan.im.model.IMMessage;
import com.quwan.im.model.MessageType;
import com.quwan.im.model.ProtocolMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 二进制编解码客户端测试
 * 测试在实际网络环境中的二进制编解码功能
 */
public class BinaryClientTest {
    private static final Logger logger = LoggerFactory.getLogger(BinaryClientTest.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String host;
    private final int port;
    private Channel channel;
    private EventLoopGroup group;
    private CountDownLatch connectLatch = new CountDownLatch(1);
    private CountDownLatch messageLatch = new CountDownLatch(1);

    public BinaryClientTest(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws Exception {
        group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            // 1. 粘包拆包处理器
                            pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
                                    50 * 1024 * 1024, // 最大帧长度
                                    6, // 长度字段偏移量
                                    4, // 长度字段长度
                                    0, // 长度调整值
                                    0  // 跳过的初始字节数
                            ));

                            // 2. 二进制消息编解码器
                            pipeline.addLast("messageDecoder", new BinaryMessageDecoder());
                            pipeline.addLast("messageEncoder", new BinaryMessageEncoder());

                            // 3. 客户端处理器
                            pipeline.addLast("clientHandler", new BinaryClientHandler());
                        }
                    });

            // 连接服务器
            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();
            logger.info("已连接到服务器: {}:{}", host, port);

            // 等待连接建立
            connectLatch.await(5, TimeUnit.SECONDS);

            // 发送测试消息
            sendTestMessages();

            // 等待消息处理完成
            messageLatch.await(10, TimeUnit.SECONDS);

        } finally {
            if (channel != null) {
                channel.close();
            }
            group.shutdownGracefully();
        }
    }

    private void sendTestMessages() throws Exception {
        logger.info("开始发送测试消息...");

        // 1. 发送登录消息
        sendLoginMessage();

        // 等待一下
        Thread.sleep(1000);

        // 2. 发送单聊消息
        sendSingleChatMessage();

        // 等待一下
        Thread.sleep(1000);

        // 3. 发送群聊消息
        sendGroupChatMessage();

        // 等待一下
        Thread.sleep(1000);

        // 4. 发送心跳消息
        sendPingMessage();

        logger.info("测试消息发送完成");
    }

    private void sendLoginMessage() throws Exception {
        String loginData = "{\"username\":\"testuser\",\"password\":\"testpass\"}";
        ProtocolMessage message = new ProtocolMessage();
        message.setVersion((byte) 1);
        message.setType((byte) 0); // LOGIN
        message.setData(loginData);

        channel.writeAndFlush(message);
        logger.info("发送登录消息: {}", loginData);
    }

    private void sendSingleChatMessage() throws Exception {
        IMMessage imMessage = new IMMessage();
        imMessage.setId("client_msg_" + System.currentTimeMillis());
        imMessage.setType(MessageType.SINGLE_CHAT);
        imMessage.setFrom("testuser");
        imMessage.setTo("user2");
        imMessage.setContent("Hello from binary client!");
        imMessage.setTimestamp(System.currentTimeMillis());

        String jsonData = objectMapper.writeValueAsString(imMessage);
        ProtocolMessage message = new ProtocolMessage();
        message.setVersion((byte) 1);
        message.setType((byte) 10); // SINGLE_CHAT
        message.setData(jsonData);

        channel.writeAndFlush(message);
        logger.info("发送单聊消息: {}", imMessage.getContent());
    }

    private void sendGroupChatMessage() throws Exception {
        IMMessage imMessage = new IMMessage();
        imMessage.setId("group_msg_" + System.currentTimeMillis());
        imMessage.setType(MessageType.GROUP_CHAT);
        imMessage.setFrom("testuser");
        imMessage.setGroupId("test_group");
        imMessage.setContent("Hello everyone from binary client!");
        imMessage.setTimestamp(System.currentTimeMillis());

        String jsonData = objectMapper.writeValueAsString(imMessage);
        ProtocolMessage message = new ProtocolMessage();
        message.setVersion((byte) 1);
        message.setType((byte) 20); // GROUP_CHAT
        message.setData(jsonData);

        channel.writeAndFlush(message);
        logger.info("发送群聊消息: {}", imMessage.getContent());
    }

    private void sendPingMessage() throws Exception {
        ProtocolMessage message = new ProtocolMessage();
        message.setVersion((byte) 1);
        message.setType((byte) 4); // PING
        message.setData("");

        channel.writeAndFlush(message);
        logger.info("发送心跳消息");
    }

    /**
     * 客户端消息处理器
     */
    private class BinaryClientHandler extends SimpleChannelInboundHandler<ProtocolMessage> {

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            logger.info("客户端连接已激活");
            connectLatch.countDown();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ProtocolMessage msg) {
            logger.info("收到服务器响应 - 类型: {}, 数据: {}", msg.getType(), msg.getData());
            
            // 根据消息类型处理
            switch (msg.getType()) {
                case 1: // LOGIN_RESPONSE
                    logger.info("登录响应: {}", msg.getData());
                    break;
                case 5: // PONG
                    logger.info("收到心跳响应");
                    break;
                case 11: // SINGLE_CHAT_ACK
                    logger.info("单聊消息确认: {}", msg.getData());
                    break;
                case 21: // GROUP_CHAT_ACK
                    logger.info("群聊消息确认: {}", msg.getData());
                    break;
                default:
                    logger.info("其他消息: {}", msg.getData());
            }
            
            messageLatch.countDown();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("客户端异常", cause);
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            logger.info("客户端连接已断开");
        }
    }

    public static void main(String[] args) {
        try {
            // 启动服务器（如果还没有启动）
            logger.info("请确保IM服务器已经启动在8888端口");
            
            // 创建客户端测试
            BinaryClientTest client = new BinaryClientTest("localhost", 8888);
            client.start();
            
        } catch (Exception e) {
            logger.error("客户端测试失败", e);
        }
    }
}
