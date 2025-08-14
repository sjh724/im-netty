package com.quwan.im.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quwan.im.model.MessageType;
import com.quwan.im.model.ProtocolMessage;
import com.quwan.im.protocol.MessageDecoder;
import com.quwan.im.protocol.MessageEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 简化的客户端测试类
 * 用于验证修复后的编解码器是否工作正常
 */
public class SimpleClientTest {
    private static final Logger logger = LoggerFactory.getLogger(SimpleClientTest.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        SimpleClientTest client = new SimpleClientTest();
        client.start();
    }

    public void start() {
        EventLoopGroup group = new NioEventLoopGroup();
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

                            // 使用与服务端完全一致的编解码器配置
                            pipeline.addLast("messageDecoder", new MessageDecoder());
                            pipeline.addLast("messageEncoder", new MessageEncoder());

                            // 客户端消息处理器
                            pipeline.addLast("clientHandler", new SimpleChannelInboundHandler<ProtocolMessage>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, ProtocolMessage msg) {
                                    logger.info("收到服务端响应: 类型={}, 数据={}", 
                                        MessageType.fromCode(msg.getType()).name(), msg.getData());
                                }

                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    logger.info("连接建立成功，发送登录请求...");
                                    
                                    // 发送登录请求
                                    try {
                                        Map<String, String> loginData = new HashMap<>();
                                        loginData.put("username", "testuser");
                                        loginData.put("password", "password123");
                                        
                                        String jsonData = objectMapper.writeValueAsString(loginData);
                                        ProtocolMessage loginMessage = new ProtocolMessage(MessageType.LOGIN.getCode(), jsonData);
                                        
                                        ctx.writeAndFlush(loginMessage);
                                        logger.info("登录请求已发送: {}", jsonData);
                                        
                                    } catch (Exception e) {
                                        logger.error("发送登录请求失败", e);
                                    }
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    logger.error("客户端异常", cause);
                                    ctx.close();
                                }
                            });
                        }
                    });

            // 连接服务器
            logger.info("正在连接服务器 localhost:8888...");
            ChannelFuture future = bootstrap.connect("localhost", 8888).sync();
            Channel channel = future.channel();
            
            // 等待一段时间观察响应
            channel.eventLoop().schedule(() -> {
                logger.info("测试完成，关闭连接");
                channel.close();
            }, 10, TimeUnit.SECONDS);

            // 等待连接关闭
            channel.closeFuture().sync();

        } catch (Exception e) {
            logger.error("客户端运行失败", e);
        } finally {
            group.shutdownGracefully();
        }
    }
}
