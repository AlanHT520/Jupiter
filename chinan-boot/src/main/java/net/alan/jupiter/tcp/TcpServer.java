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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class TcpServer {

    @Value("${Chinan.tcp.port:9090}")
    private int port;

    @Value("${Chinan.tcp.enabled:true}")
    private boolean enabled;

    private final TcpDispatcher dispatcher;
    private volatile ServerSocket serverSocket;
    private ExecutorService ioPool;
    private ExecutorService bizPool;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 当前活跃连接，仅用于桌面状态栏与系统配置页展示。 */
    private final java.util.Set<Socket> activeClients = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("TCP服务未启用");
            return;
        }

        ioPool = new ThreadPoolExecutor(
                8, 200,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                r -> new Thread(r, "tcp-io"));

        bizPool = new ThreadPoolExecutor(
                8, 32,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                r -> new Thread(r, "tcp-biz"));

        running.set(true);
        new Thread(this::run, "tcp-acceptor").start();
        log.info("TCP服务线程池已初始化: io(core=8,max=200), biz(core=8,max=32)");
    }

    private void run() {
        try {
            serverSocket = new ServerSocket(port);
            log.info("TCP服务器已启动，监听端口: {}", port);
            while (running.get() && serverSocket != null && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    log.debug("新客户端连接: {}", client.getInetAddress());
                    activeClients.add(client);
                    try {
                        ioPool.submit(() -> {
                            try {
                                handleClient(client);
                            } finally {
                                activeClients.remove(client);
                            }
                        });
                    } catch (java.util.concurrent.RejectedExecutionException rejected) {
                        // 线程池满时只拒绝该连接，绝不能让 accept 循环退出
                        log.warn("TCP 连接数已达上限，拒绝新连接: {}", client.getInetAddress());
                        activeClients.remove(client);
                        closeQuietly(client);
                    }
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

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 忽略关闭异常
        }
    }

    private void handleClient(Socket client) {
        try {
            client.setTcpNoDelay(true);
            client.setSoTimeout(30000);
            client.setReceiveBufferSize(256 * 1024);
            client.setSendBufferSize(256 * 1024);
        } catch (IOException e) {
            log.debug("设置Socket参数失败: {}", e.getMessage());
        }

        try (client;
             DataInputStream in = new DataInputStream(
                     new BufferedInputStream(client.getInputStream()));
             DataOutputStream out = new DataOutputStream(
                     new BufferedOutputStream(client.getOutputStream()))) {

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

                final DataOutputStream writeStream = out;
                bizPool.submit(() -> dispatchAndWrite(request, writeStream));
            }
        } catch (EOFException e) {
            log.debug("客户端断开连接: {}", client.getInetAddress());
        } catch (Exception e) {
            log.error("处理客户端消息异常", e);
        }
    }

    private void dispatchAndWrite(TcpMessage request, DataOutputStream out) {
        try {
            TcpMessage response = dispatcher.dispatch(request);
            synchronized (out) {
                TcpMessageCodec.writeFrame(out, response);
            }
        } catch (Exception e) {
            log.error("TCP业务处理异常: requestId={}", request.getRequestId(), e);
            try {
                TcpMessage errorResp = TcpMessage.response(
                        request.getRequestId() != null ? request.getRequestId() : "",
                        500, "系统异常，请稍后再试", null);
                synchronized (out) {
                    TcpMessageCodec.writeFrame(out, errorResp);
                }
            } catch (IOException ioe) {
                log.debug("写入错误响应失败: {}", ioe.getMessage());
            }
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
        shutdownPool(ioPool);
        shutdownPool(bizPool);
    }

    private void shutdownPool(ExecutorService pool) {
        if (pool == null) {
            return;
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public int getPort() {
        return port;
    }

    /** 当前活跃 TCP 连接数（桌面状态栏 / 系统配置页只读展示）。 */
    public int getActiveConnections() {
        return activeClients.size();
    }
}