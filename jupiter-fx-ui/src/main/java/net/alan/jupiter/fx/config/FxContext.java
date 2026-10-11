package net.alan.jupiter.fx.config;

import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
public class FxContext {
    private String userId;
    private String username;
    private String role;
    private String token;

    public boolean isLoggedIn() {
        return userId != null;
    }

    public void clear() {
        userId = null;
        username = null;
        role = null;
        token = null;
    }

    public void setAsDesktopUser() {
        this.userId = "desktop";
        this.username = "桌面端";
        this.role = "ADMIN";
        this.token = null;
    }
}