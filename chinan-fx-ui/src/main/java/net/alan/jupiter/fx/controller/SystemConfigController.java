package net.alan.jupiter.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.fx.config.FxContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.nio.file.Path;

/**
 * 系统配置（只读）：把运行环境摊开给评委看，不做可写配置，避免误操作。
 */
@Component
@RequiredArgsConstructor
public class SystemConfigController {

    private final Environment environment;
    private final FxContext fxContext;
    private final ApplicationContext context;

    @Value("${Chinan.version:v1.0.0}")
    private String appVersion;

    @Value("${Chinan.tcp.port:9090}")
    private int tcpPort;

    @Value("${Chinan.tcp.enabled:true}")
    private boolean tcpEnabled;

    @FXML
    private Label profileValue;
    @FXML
    private Label dbValue;
    @FXML
    private Label dbUrlValue;
    @FXML
    private Label tcpValue;
    @FXML
    private Label javaValue;
    @FXML
    private Label osValue;
    @FXML
    private Label workdirValue;
    @FXML
    private Label userValue;
    @FXML
    private Label versionValue;
    @FXML
    private Label timezoneValue;

    @FXML
    public void initialize() {
        refresh();
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    private void refresh() {
        String[] profiles = environment.getActiveProfiles();
        profileValue.setText(profiles.length == 0 ? "default" : String.join(", ", profiles));

        String url = environment.getProperty("spring.datasource.url", "unknown");
        dbUrlValue.setText(mask(url));
        dbValue.setText(dbKind(url));

        tcpValue.setText((tcpEnabled ? "ENABLED" : "DISABLED")
                + "  :" + tcpPort + "  ● " + activeConnections() + " conn");

        javaValue.setText(System.getProperty("java.version") + " / "
                + System.getProperty("java.vendor"));
        osValue.setText(System.getProperty("os.name") + " "
                + System.getProperty("os.version"));
        workdirValue.setText(Path.of("").toAbsolutePath().toString());
        userValue.setText(fxContext.getUsername() + "  ROLE " + fxContext.getRole());
        versionValue.setText("寝安 QIN'AN " + appVersion);
        timezoneValue.setText(java.util.TimeZone.getDefault().getID()
                + "  " + java.time.ZonedDateTime.now().getOffset());
    }

    private int activeConnections() {
        try {
            if (!context.containsBean("tcpServer")) {
                return 0;
            }
            Object bean = context.getBean("tcpServer");
            Method method = bean.getClass().getMethod("getActiveConnections");
            Object value = method.invoke(bean);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String dbKind(String url) {
        String lower = url == null ? "" : url.toLowerCase();
        if (lower.contains("h2")) return "H2 (in-memory / file)";
        if (lower.contains("mysql") || lower.contains("mariadb")) return "MySQL / MariaDB";
        if (lower.contains("postgres")) return "PostgreSQL";
        if (lower.contains("sqlserver")) return "SQL Server";
        return "unknown";
    }

    /** 只保留 host/db，隐藏 URL 上的账号密码参数。 */
    private String mask(String url) {
        if (url == null) return "unknown";
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query) + "?…";
    }
}
