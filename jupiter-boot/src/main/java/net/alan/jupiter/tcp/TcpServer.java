package net.alan.jupiter.tcp;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.tcp.model.TcpMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class TcpServer {

    @Value("${jupiter.tcp.port:9090}")
    private int port;

    @Value("${jupiter.tcp.enabled:true}")
    private boolean enabled;

    private final TcpDispatcher dispatcher;
    private volatile ServerSocket serverSocket;
    private ExecutorService threadPool;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("TCP服务未启用");
            return;
        }
        threadPool = Executors.newCachedThreadPool(
                r -> new Thread(r, "tcp-worker")
        );
        running.set(true);
        new Thread(this::run, "tcp-acceptor").start();
    }

    private void run() {
        try {
            serverSocket = new ServerSocket(port);
            log.info("TCP服务器已启动，监听端口: {}", port);
            while (running.get() && serverSocket != null && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    log.debug("新客户端连接: {}", client.getInetAddress());
                    threadPool.submit(() -> handleClient(client));
                } catch (IOException e) {
                    if (running.get()) {
                        log.error("接受客户端连接异常", e);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                log.error("TCP服务器异常", e);
            }
        }
    }

    private void handleClient(Socket client) {
        try {
            client.setTcpNoDelay(true);
            client.setReceiveBufferSize(256 * 1024);
            client.setSendBufferSize(256 * 1024);
        } catch (IOException e) {
            log.debug("设置Socket参数失败: {}", e.getMessage());
        }

        try (client;
             DataInputStream in = new DataInputStream(new BufferedInputStream(client.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(client.getOutputStream()))) {

            log.debug("客户端已连接: {}", client.getInetAddress());

            while (!client.isClosed()) {
                TcpMessage request;
                try {
                    request = TcpMessageCodec.readFrame(in);
                } catch (EOFException e) {
                    log.debug("客户端关闭了连接: {}", client.getInetAddress());
                    break;
                } catch (IOException e) {
                    log.debug("读取消息失败: {}", e.getMessage());
                    break;
                }
                TcpMessage response = dispatcher.dispatch(request);
                try {
                    TcpMessageCodec.writeFrame(out, response);
                } catch (IOException e) {
                    log.debug("写入响应失败，客户端已断开: {}", e.getMessage());
                    break;
                }
            }
        } catch (EOFException e) {
            log.debug("客户端断开连接: {}", client.getInetAddress());
        } catch (Exception e) {
            log.error("处理客户端消息异常", e);
        }
    }

    @PreDestroy
    public void stop() {
        log.info("TCP服务器正在关闭...");
        running.set(false);
        try {
            ServerSocket ss = serverSocket;
            if (ss != null && !ss.isClosed()) {
                ss.close();
            }
        } catch (Exception e) {
            log.error("关闭TCP服务器异常", e);
        }
        if (threadPool != null) {
            threadPool.shutdown();
        }
    }

    public int getPort() {
        return port;
    }
}