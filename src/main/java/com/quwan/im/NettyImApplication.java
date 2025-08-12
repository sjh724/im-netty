package com.quwan.im;

import com.quwan.im.netty.IMNettyServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
public class NettyImApplication implements CommandLineRunner {

    @Autowired
    private IMNettyServer imNettyServer;

    public static void main(String[] args) {
        SpringApplication.run(NettyImApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // 启动TCP服务器
        imNettyServer.start();

        // 注册关闭钩子，优雅关闭服务器
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                imNettyServer.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }
}

