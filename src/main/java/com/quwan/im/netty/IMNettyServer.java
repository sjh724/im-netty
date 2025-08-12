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
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    @Autowired
    private IMChannelInitializer imChannelInitializer;
    public IMNettyServer(@Value("${im.server.port:8888}") int port) {
        this.port = port;
    }

    // 启动服务器
    public void start() throws Exception {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(imChannelInitializer)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                // TCP_NODELAY禁用Nagle算法，减少延迟
                .childOption(ChannelOption.TCP_NODELAY, true);

        // 绑定端口，开始接收进来的连接
        channel = bootstrap.bind(port).sync().channel();
        System.out.println("TCP IM服务器启动成功，端口：" + port);
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
