package com.quwan.im.netty;


import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IMNettyServer {
    private final int port;
    private final int bossThreads;
    private final int workerThreads;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    @Autowired
    private IMChannelInitializer imChannelInitializer;
    
    public IMNettyServer(@Value("${im.server.port:8888}") int port,
                        @Value("${im.server.boss-threads:1}") int bossThreads,
                        @Value("${im.server.worker-threads:16}") int workerThreads) {
        this.port = port;
        this.bossThreads = bossThreads;
        this.workerThreads = workerThreads;
    }

    // 启动服务器
    public void start() throws Exception {
        // 优化线程池配置
        bossGroup = new NioEventLoopGroup(bossThreads);
        workerGroup = new NioEventLoopGroup(workerThreads);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(imChannelInitializer)
                // 连接队列大小，提高并发连接数
                .option(ChannelOption.SO_BACKLOG, 1024)
                // 地址重用，快速重启
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                // TCP_NODELAY禁用Nagle算法，减少延迟
                .childOption(ChannelOption.TCP_NODELAY, true)
                // 设置接收缓冲区大小
                .childOption(ChannelOption.SO_RCVBUF, 32 * 1024)
                // 设置发送缓冲区大小
                .childOption(ChannelOption.SO_SNDBUF, 32 * 1024)
                // 禁用Nagle算法
                .childOption(ChannelOption.TCP_NODELAY, true)
                // 设置连接超时
                .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000);

        // 绑定端口，开始接收进来的连接
        channel = bootstrap.bind(port).sync().channel();
        System.out.println("TCP IM服务器启动成功，端口：" + port + 
                          "，Boss线程数：" + bossThreads + 
                          "，Worker线程数：" + workerThreads);
    }

    // 停止服务器
    public void stop() throws Exception {
        if (channel != null) {
            channel.close();
        }
        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
        System.out.println("TCP IM服务器已停止");
    }
}
