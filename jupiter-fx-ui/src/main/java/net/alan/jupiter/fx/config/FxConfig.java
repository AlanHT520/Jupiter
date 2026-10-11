package net.alan.jupiter.fx.config;

import atlantafx.base.theme.NordDark;
import javafx.application.Application;
import javafx.application.Platform;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class FxConfig {

    @PostConstruct
    public void init() {
        Platform.setImplicitExit(true);
        Application.setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());
    }
}