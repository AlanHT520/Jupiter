package net.alan.jupiter.fx.config;

import atlantafx.base.theme.NordDark;
import javafx.application.Application;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * JavaFX 主题初始化。
 * <p>只在桌面模式（{@code --Chinan.runtime.mode=desktop}）下生效。
 * 服务端模式（{@code --mode=server}）没有 JavaFX 工具包，若执行本类逻辑会抛
 * {@code IllegalStateException: Toolkit not initialized} 并导致 Spring 上下文启动失败。</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "Chinan.runtime.mode", havingValue = "desktop")
public class FxConfig {

    @PostConstruct
    public void init() {
        Platform.setImplicitExit(true);
        try {
            Application.setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());
        } catch (IllegalStateException e) {
            // 没有 JavaFX 工具包时（例如被非桌面入口加载）降级为默认样式，不阻断启动
            log.warn("JavaFX 主题初始化跳过（工具包未启动）: {}", e.getMessage());
        }
    }
}