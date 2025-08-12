package com.quwan.im.netty;


import com.quwan.im.protocol.MessageDecoder;
import com.quwan.im.protocol.MessageEncoder;
import com.quwan.im.model.IMMessage;
import com.quwan.im.model.MessageType;
import com.quwan.im.model.ProtocolMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * IM客户端测试类
 * 完全适配服务器端IMChannelInitializer配置，包含心跳检测和自动重连机制
 */

public class IMClient {
    private static final Logger logger = LoggerFactory.getLogger(IMClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // 服务器配置
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    // 客户端状态
    private Channel channel;
    private EventLoopGroup group;
    private boolean isConnected = false;
    private boolean isLoginSuccess = false;
    private boolean isShutdown = false;
    private String userId;

    // 重连配置
    private int reconnectDelay = 1; // 初始重连延迟（秒）
    private static final int MAX_RECONNECT_DELAY = 60; // 最大重连延迟
    private ScheduledFuture<?> reconnectFuture;

    public IMClient(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    /**
     * 启动客户端
     */
    public void start() {
        logger.info("启动IM客户端，连接到 {}:{}", host, port);
        this.isShutdown = false;
        connect();
        startConsoleInput(); // 启动控制台输入测试
    }

    /**
     * 关闭客户端
     */
    public void shutdown() {
        logger.info("关闭IM客户端...");
        this.isShutdown = true;
        this.isConnected = false;
        this.isLoginSuccess = false;

        // 取消重连任务
        if (reconnectFuture != null && !reconnectFuture.isCancelled()) {
            reconnectFuture.cancel(true);
        }

        // 关闭通道
        if (channel != null && channel.isActive()) {
            channel.close().addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }

        // 释放事件循环组
        if (group != null) {
            group.shutdownGracefully();
        }

        scheduler.shutdown();
        logger.info("IM客户端已关闭");
    }

    /**
     * 建立与服务器的连接
     */
    private void connect() {
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

                            // 1. 粘包拆包处理器（与服务器端完全一致）
                            pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
                                    10 * 1024 * 1024, // 最大帧长度
                                    0, // 长度字段偏移量
                                    4, // 长度字段占用字节数
                                    0, // 长度调整值
                                    4  // 跳过的初始字节数
                            ));
                            pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));

                            // 2. 字符串编解码器（与服务器端一致）
                            pipeline.addLast("stringDecoder", new StringDecoder(CharsetUtil.UTF_8));
                            pipeline.addLast("stringEncoder", new StringEncoder(CharsetUtil.UTF_8));

                            // 3. 自定义消息编解码器
                            pipeline.addLast("messageDecoder", new MessageDecoder());
                            pipeline.addLast("messageEncoder", new MessageEncoder());

                            // 4. 心跳检测（客户端20秒未发送消息则发送心跳，小于服务器30秒超时）
                            pipeline.addLast("idleStateHandler", new IdleStateHandler(
                                    0, 20, 0, TimeUnit.SECONDS
                            ));

                            // 5. 客户端消息处理器
                            pipeline.addLast("clientHandler", new ClientMessageHandler());
                        }
                    });

            // 发起连接
            logger.info("尝试连接服务器...");
            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();
            isConnected = true;
            logger.info("已建立连接: {}", channel.remoteAddress());

            // 连接关闭监听
            channel.closeFuture().addListener((ChannelFutureListener) f -> {
                logger.warn("连接已关闭");
                handleDisconnect();
            });

            // 发送登录请求
            sendLoginRequest();

        } catch (Exception e) {
            logger.error("连接失败", e);
            handleConnectFailure();
        }
    }

    /**
     * 处理连接断开
     */
    private void handleDisconnect() {
        isConnected = false;
        isLoginSuccess = false;

        if (!isShutdown) {
            scheduleReconnect();
        }
    }

    /**
     * 处理连接失败
     */
    private void handleConnectFailure() {
        isConnected = false;
        isLoginSuccess = false;

        // 释放资源
        if (group != null) {
            group.shutdownGracefully();
        }

        if (!isShutdown) {
            scheduleReconnect();
        }
    }

    /**
     * 安排重连任务（指数退避策略）
     */
    private void scheduleReconnect() {
        if (isShutdown) return;

        // 取消现有重连任务
        if (reconnectFuture != null && !reconnectFuture.isCancelled()) {
            reconnectFuture.cancel(true);
        }

        // 计算下次重连延迟
        int delay = reconnectDelay;
        reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY);

        logger.info("{}秒后尝试重连...", delay);
        reconnectFuture = scheduler.schedule(() -> {
            logger.info("开始重连...");
            connect();
        }, delay, TimeUnit.SECONDS);
    }

    /**
     * 发送登录请求
     */
    private void sendLoginRequest() {
        if (!isConnected) {
            logger.error("未建立连接，无法发送登录请求");
            return;
        }

        try {
            Map<String, String> loginData = new HashMap<>();
            loginData.put("username", username);
            loginData.put("password", password);

            ProtocolMessage loginMsg = new ProtocolMessage(
                    MessageType.LOGIN.getCode(),
                    objectMapper.writeValueAsString(loginData)
            );

            channel.writeAndFlush(loginMsg).addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    logger.error("发送登录请求失败", f.cause());
                    channel.close(); // 发送失败关闭连接，触发重连
                }
            });
        } catch (Exception e) {
            logger.error("构建登录请求失败", e);
        }
    }

    /**
     * 发送单聊消息
     */
    public void sendChatMessage(String toUserId, String content) {
        if (!isLoginSuccess) {
            logger.error("发送失败：未登录");
            return;
        }

        try {
            IMMessage message = new IMMessage();
            message.setId(UUID.randomUUID().toString());
            message.setFrom(userId);
            message.setTo(toUserId);
            message.setContent(content);
            message.setType(MessageType.SINGLE_CHAT);

            ProtocolMessage protocolMsg = new ProtocolMessage(
                    MessageType.SINGLE_CHAT.getCode(),
                    objectMapper.writeValueAsString(message)
            );

            channel.writeAndFlush(protocolMsg).addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    logger.error("发送消息失败", f.cause());
                }
            });
        } catch (Exception e) {
            logger.error("构建消息失败", e);
        }
    }

    /**
     * 发送群聊消息
     */
    public void sendGroupMessage(String groupId, String content) {
        if (!isLoginSuccess) {
            logger.error("发送失败：未登录");
            return;
        }

        try {
            IMMessage message = new IMMessage();
            message.setId(UUID.randomUUID().toString());
            message.setFrom(userId);
            message.setGroupId(groupId);
            message.setContent(content);
            message.setType(MessageType.GROUP_CHAT);

            ProtocolMessage protocolMsg = new ProtocolMessage(
                    MessageType.GROUP_CHAT.getCode(),
                    objectMapper.writeValueAsString(message)
            );

            channel.writeAndFlush(protocolMsg).addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    logger.error("发送群消息失败", f.cause());
                }
            });
        } catch (Exception e) {
            logger.error("构建群消息失败", e);
        }
    }

    /**
     * 启动控制台输入
     */
    private void startConsoleInput() {
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            logger.info("控制台输入已启动，支持以下命令：");
            logger.info("1. 发送单聊消息：chat [目标用户ID] [消息内容]");
            logger.info("2. 发送群聊消息：group [群组ID] [消息内容]");
            logger.info("3. 退出客户端：exit");

            while (!isShutdown) {
                try {
                    if (scanner.hasNextLine()) {
                        String input = scanner.nextLine().trim();
                        if (input.isEmpty()) continue;

                        String[] parts = input.split(" ", 3);
                        if (parts.length < 3) {
                            if ("exit".equals(parts[0])) {
                                shutdown();
                                break;
                            } else {
                                logger.warn("命令格式错误，请重新输入");
                                continue;
                            }
                        }

                        String cmd = parts[0];
                        String target = parts[1];
                        String content = parts[2];

                        switch (cmd) {
                            case "chat":
                                sendChatMessage(target, content);
                                break;
                            case "group":
                                sendGroupMessage(target, content);
                                break;
                            default:
                                logger.warn("未知命令：{}", cmd);
                        }
                    }
                } catch (Exception e) {
                    logger.error("处理输入异常", e);
                }
            }
            scanner.close();
        }, "Console-Input-Thread").start();
    }

    /**
     * 客户端消息处理器
     */
    private class ClientMessageHandler extends ChannelInboundHandlerAdapter {

        /**
         * 处理接收到的消息
         */
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ProtocolMessage) {
                ProtocolMessage protocolMsg = (ProtocolMessage) msg;
                try {
                    handleProtocolMessage(ctx, protocolMsg);
                } catch (Exception e) {
                    logger.error("处理消息异常", e);
                }
            }
        }

        /**
         * 处理协议消息
         */
        private void handleProtocolMessage(ChannelHandlerContext ctx, ProtocolMessage msg) throws Exception {
            MessageType type = MessageType.fromCode(msg.getType());
            String data = msg.getData();

            switch (type) {
                case LOGIN_RESPONSE:
                    handleLoginResponse(ctx, data);
                    break;
                case SINGLE_CHAT:
                    handleSingleChat(data);
                    break;
                case GROUP_CHAT:
                    handleGroupChat(data);
                    break;
                case PONG:
                    logger.debug("收到服务器心跳响应");
                    break;
                case SYSTEM_NOTIFY:
                    handleSystemNotify(data);
                    break;
                case ERROR_RESPONSE:
                    handleErrorResponse(data);
                    break;
                default:
                    logger.info("收到未知类型消息：{}，内容：{}", type, data);
            }
        }

        /**
         * 处理登录响应
         */
        private void handleLoginResponse(ChannelHandlerContext ctx, String data) throws Exception {
            Map<String, String> response = objectMapper.readValue(data, Map.class);
            String status = response.get("extra");

            if ("success".equals(status)) {
                userId = response.get("content");
                isLoginSuccess = true;
                reconnectDelay = 1; // 重置重连延迟
                logger.info("登录成功，用户ID：{}", userId);
            } else {
                String errorMsg = response.get("content");
                logger.error("登录失败：{}", errorMsg);
                // 登录失败后延迟关闭连接，确保错误消息发送完成
                ctx.channel().close().addListener(f ->
                        logger.info("登录失败，连接已关闭")
                );
            }
        }

        /**
         * 处理单聊消息
         */
        private void handleSingleChat(String data) throws Exception {
            IMMessage message = objectMapper.readValue(data, IMMessage.class);
            logger.info("\n收到来自[{}]的消息：{}", message.getFrom(), message.getContent());
        }

        /**
         * 处理群聊消息
         */
        private void handleGroupChat(String data) throws Exception {
            IMMessage message = objectMapper.readValue(data, IMMessage.class);
            logger.info("\n收到群组[{}]中[{}]的消息：{}",
                    message.getGroupId(), message.getFrom(), message.getContent());
        }

        /**
         * 处理系统通知
         */
        private void handleSystemNotify(String data) throws Exception {
            IMMessage message = objectMapper.readValue(data, IMMessage.class);
            logger.info("\n系统通知：{}", message.getContent());
        }

        /**
         * 处理错误响应
         */
        private void handleErrorResponse(String data) throws Exception {
            IMMessage message = objectMapper.readValue(data, IMMessage.class);
            logger.error("\n错误消息：{}", message.getContent());
        }

        /**
         * 处理空闲事件（发送心跳）
         */
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state() == IdleState.WRITER_IDLE) {
                    // 发送心跳包
                    try {
                        ProtocolMessage ping = new ProtocolMessage(
                                MessageType.PING.getCode(),
                                "ping"
                        );
                        ctx.writeAndFlush(ping).addListener(future -> {
                            if (!future.isSuccess()) {
                                logger.error("发送心跳失败，准备重连", future.cause());
                                ctx.close();
                            } else {
                                logger.debug("已发送心跳包");
                            }
                        });
                    } catch (Exception e) {
                        logger.error("构建心跳消息失败", e);
                    }
                }
            }
        }

        /**
         * 处理异常
         */
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (cause instanceof java.io.IOException && cause.getMessage().contains("Connection reset by peer")) {
                logger.error("服务器主动关闭连接：可能是认证失败、超时或协议不匹配");
            } else {
                logger.error("客户端处理异常", cause);
            }
            ctx.close(); // 关闭连接触发重连
        }
    }

    /**
     * 主方法：启动客户端
     */
    public static void main(String[] args) {
        // 服务器地址和端口（根据实际情况修改）
        String host = "127.0.0.1";
        int port = 8888;

        // 登录账号密码（根据实际情况修改）
        String username = "Tom";
        String password = "123456";
        String encryptedPassword = DigestUtils.md5DigestAsHex(
                password.getBytes(StandardCharsets.UTF_8));
        IMClient client = new IMClient(host, port, username, encryptedPassword);
        client.start();

        // 注册JVM关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(client::shutdown));
    }
}
