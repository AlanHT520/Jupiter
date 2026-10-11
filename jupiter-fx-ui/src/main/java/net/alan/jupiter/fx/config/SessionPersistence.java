package net.alan.jupiter.fx.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Slf4j
@Component
public class SessionPersistence {

    private static final Path SESSION_FILE = Path.of(
            System.getProperty("user.home"), ".jupiter", "session.properties"
    );

    public void save(FxContext context) {
        try {
            Files.createDirectories(SESSION_FILE.getParent());
            Properties props = new Properties();
            props.setProperty("token", context.getToken());
            props.setProperty("userId", context.getUserId());
            props.setProperty("username", context.getUsername());
            props.setProperty("role", context.getRole());
            try (OutputStream out = new FileOutputStream(SESSION_FILE.toFile())) {
                props.store(out, "Jupiter Session");
            }
            try {
                SESSION_FILE.toFile().setReadable(false, false);
                SESSION_FILE.toFile().setReadable(true, true);
                SESSION_FILE.toFile().setWritable(false, false);
                SESSION_FILE.toFile().setWritable(true, true);
            } catch (Exception ignored) {
            }
            log.debug("会话已保存");
        } catch (IOException e) {
            log.warn("保存会话失败: {}", e.getMessage());
        }
    }

    public FxContext load() {
        if (!Files.exists(SESSION_FILE)) {
            return null;
        }
        try (InputStream in = new FileInputStream(SESSION_FILE.toFile())) {
            Properties props = new Properties();
            props.load(in);
            FxContext context = new FxContext();
            context.setToken(props.getProperty("token"));
            context.setUserId(props.getProperty("userId"));
            context.setUsername(props.getProperty("username"));
            context.setRole(props.getProperty("role"));
            if (context.getToken() == null || context.getUserId() == null) {
                return null;
            }
            log.debug("已加载保存的会话");
            return context;
        } catch (IOException e) {
            log.warn("加载会话失败: {}", e.getMessage());
            return null;
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(SESSION_FILE);
            log.debug("会话已清除");
        } catch (IOException e) {
            log.warn("清除会话失败: {}", e.getMessage());
        }
    }
}