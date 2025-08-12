package com.quwan.im.netty;


import com.quwan.im.model.IMMessage;
import com.quwan.im.model.MessageType;
import com.quwan.im.model.ProtocolMessage;
import com.quwan.im.protocol.MessageDecoder;
import com.quwan.im.protocol.MessageEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * IM客户端主类
 * 负责与服务器建立连接、发送消息、处理重连和心跳
 */
public class IMClient {
    private static final Logger logger = LoggerFactory.getLogger(IMClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // 服务器配置
    private final String serverHost;
    private final int serverPort;

    // 用户信息
    private final String username;
    private final String password;
    private String userId; // 登录后由服务器分配

    // 连接状态
    private Channel channel;
    private NioEventLoopGroup workerGroup;
    private boolean isConnected = false;
    private boolean isLoginSuccess = false;
    private boolean isShutdown = false;

    // 重连配置
    private final ReconnectConfig    reconnectConfig = new ReconnectConfig();
    private       ScheduledFuture<?> reconnectFuture;

    public IMClient(String serverHost, int serverPort, String username, String password) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.username = username;
        this.password = password;
    }

    /**
     * 启动客户端
     */
    public void start() {
        logger.info("启动IM客户端...");
        this.isShutdown = false;
        connect();
        startConsoleInput(); // 启动控制台输入
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
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        scheduler.shutdown();
        logger.info("IM客户端已关闭");
    }

    /**
     * 建立与服务器的连接
     */
    private void connect() {
        workerGroup = new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            // 空闲检测：15秒未发送消息则发送心跳
                            pipeline.addLast(new IdleStateHandler(0, 15, 0, TimeUnit.SECONDS));

                            // 编解码器（必须与服务器端一致）
                            pipeline.addLast(new MessageDecoder());
                            pipeline.addLast(new MessageEncoder());

                            // 客户端消息处理器
                            pipeline.addLast(new ClientMessageHandler());
                        }
                    });

            logger.info("尝试连接服务器: {}:{}", serverHost, serverPort);
            ChannelFuture future = bootstrap.connect(serverHost, serverPort).sync();

            // 连接成功
            channel = future.channel();
            isConnected = true;
            logger.info("已连接到服务器: {}", channel.remoteAddress());

            // 发送登录请求
            sendLoginRequest();

            // 监听连接关闭
            channel.closeFuture().addListener((ChannelFutureListener) f -> {
                logger.warn("与服务器的连接已关闭");
                handleDisconnect();
            });

        } catch (Exception e) {
            logger.error("连接服务器失败", e);
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
            scheduleReconnect(); // 非主动关闭则重连
        }
    }

    /**
     * 处理连接失败
     */
    private void handleConnectFailure() {
        isConnected = false;
        isLoginSuccess = false;

        // 释放资源
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        if (!isShutdown) {
            scheduleReconnect(); // 非主动关闭则重连
        }
    }

    /**
     * 安排重连任务
     */
    private void scheduleReconnect() {
        if (isShutdown) return;

        // 取消现有重连任务
        if (reconnectFuture != null && !reconnectFuture.isCancelled()) {
            reconnectFuture.cancel(true);
        }

        // 计算重连延迟（指数退避）
        long delay = reconnectConfig.getNextDelay();
        logger.info("{}秒后尝试重连...", delay);

        reconnectFuture = scheduler.schedule(() -> {
            logger.info("开始重连服务器...");
            connect();
        }, delay, TimeUnit.SECONDS);
    }

    /**
     * 发送登录请求
     */
    private void sendLoginRequest() {
        try {
            Map<String, String> loginData = new HashMap<>();
            loginData.put("username", username);
            loginData.put("password", password);

            ProtocolMessage message = new ProtocolMessage(
                    MessageType.LOGIN,
                    objectMapper.writeValueAsString(loginData)
            );

            channel.writeAndFlush(message).addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    logger.error("发送登录请求失败", f.cause());
                    channel.close(); // 发送失败则关闭连接，触发重连
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

            ProtocolMessage protocolMessage = new ProtocolMessage(
                    MessageType.SINGLE_CHAT,
                    objectMapper.writeValueAsString(message)
            );

            channel.writeAndFlush(protocolMessage).addListener((ChannelFutureListener) f -> {
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

            ProtocolMessage protocolMessage = new ProtocolMessage(
                    MessageType.GROUP_CHAT,
                    objectMapper.writeValueAsString(message)
            );

            channel.writeAndFlush(protocolMessage).addListener((ChannelFutureListener) f -> {
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
            logger.info("请输入消息（格式: [type] [target] [content]，例如: chat user123 你好）");

            while (!isShutdown) {
                try {
                    String input = scanner.nextLine().trim();
                    if (input.isEmpty()) continue;

                    String[] parts = input.split(" ", 3);
                    if (parts.length < 3) {
                        logger.info("格式错误，请重新输入");
                        continue;
                    }

                    String type = parts[0];
                    String target = parts[1];
                    String content = parts[2];

                    switch (type) {
                        case "chat":
                            sendChatMessage(target, content);
                            break;
                        case "group":
                            sendGroupMessage(target, content);
                            break;
                        case "exit":
                            shutdown();
                            break;
                        default:
                            logger.info("未知消息类型: {}", type);
                    }
                } catch (Exception e) {
                    logger.error("处理输入失败", e);
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
         * 处理收到的消息
         */
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ProtocolMessage) {
                ProtocolMessage protocolMessage = (ProtocolMessage) msg;
                try {
                    handleProtocolMessage(ctx,protocolMessage);
                } catch (Exception e) {
                    logger.error("处理消息失败", e);
                }
            }
        }

        /**
         * 处理协议消息
         */
        private void handleProtocolMessage(ChannelHandlerContext ctx,ProtocolMessage message) throws Exception {
            MessageType type = MessageType.fromCode(message.getType());
            String data = message.getData();

            switch (type) {
                case LOGIN_RESPONSE:
                    handleLoginResponse(ctx,data);
                    break;
                case SINGLE_CHAT:
                    handleChatMessage(data);
                    break;
                case GROUP_CHAT:
                    handleGroupMessage(data);
                    break;
                case SYSTEM_NOTIFY:
                    handleSystemNotify(data);
                    break;
                case ERROR_RESPONSE:
                    handleErrorResponse(data);
                    break;
                default:
                    logger.info("收到未知类型消息: {}", type);
            }
        }

        /**
         * 处理登录响应
         */
        private void handleLoginResponse(ChannelHandlerContext ctx,String data) {
            try {
                Map<String, String> response = objectMapper.readValue(data, Map.class);
                String status = response.get("extra");

                if ("success".equals(status)) {
                    userId = response.get("content");
                    isLoginSuccess = true;
                    reconnectConfig.reset(); // 登录成功重置重连配置
                    logger.info("登录成功，用户ID: {}", userId);
                } else {
                    logger.error("登录失败: {}", response.get("content"));
                    // 登录失败关闭连接，触发重连（但增加延迟）
                    ctx.close();
                }
            } catch (Exception e) {
                logger.error("解析登录响应失败", e);
            }
        }

        /**
         * 处理单聊消息
         */
        private void handleChatMessage(String data) throws Exception {
            IMMessage message = objectMapper.readValue(data, IMMessage.class);
            logger.info("\n收到来自[{}]的消息: {}", message.getFrom(), message.getContent());
        }

        /**
         * 处理群聊消息
         */
        private void handleGroupMessage(String data) throws Exception {
            IMMessage message = objectMapper.readValue(data, IMMessage.class);
            logger.info("\n收到来自群组[{}]中[{}]的消息: {}",
                    message.getGroupId(), message.getFrom(), message.getContent());
        }

        /**
         * 处理系统通知
         */
        private void handleSystemNotify(String data) throws Exception {
            IMMessage message = objectMapper.readValue(data, IMMessage.class);
            logger.info("\n系统通知: {}", message.getContent());
        }

        /**
         * 处理错误响应
         */
        private void handleErrorResponse(String data) throws Exception {
            IMMessage message = objectMapper.readValue(data, IMMessage.class);
            logger.error("\n错误消息: {}", message.getContent());
        }

        /**
         * 处理空闲事件（发送心跳）
         */
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state() == IdleState.WRITER_IDLE) {
                    // 发送心跳
                    try {
                        ProtocolMessage ping = new ProtocolMessage(
                                MessageType.PING,
                                "ping"
                        );
                        ctx.writeAndFlush(ping);
                    } catch (Exception e) {
                        logger.error("发送心跳失败", e);
                    }
                }
            }
        }

        /**
         * 处理异常
         */
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("客户端发生异常", cause);
            ctx.close(); // 发生异常关闭连接，触发重连
        }
    }

    /**
     * 重连配置（指数退避策略）
     */
    private static class ReconnectConfig {
        private static final int MIN_DELAY = 1; // 最小重连延迟（秒）
        private static final int MAX_DELAY = 60; // 最大重连延迟（秒）
        private int currentDelay = MIN_DELAY;

        /**
         * 获取下一次重连延迟（指数增长）
         */
        public int getNextDelay() {
            int delay = currentDelay;
            // 下次延迟翻倍，但不超过最大值
            currentDelay = Math.min(currentDelay * 2, MAX_DELAY);
            return delay;
        }

        /**
         * 重置重连延迟（登录成功后）
         */
        public void reset() {
            currentDelay = MIN_DELAY;
        }
    }

    /**
     * 客户端入口方法
     */
    public static void main(String[] args) {
        // 示例：连接到本地服务器
        String host = "127.0.0.1";
        int port = 8080;
        String username = "testUser";
        String password = "testPass123";

        IMClient client = new IMClient(host, port, username, password);
        client.start();

        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(client::shutdown));
    }
}
