package net.alan.jupiter.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.auth.LoginUseCase;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.fx.config.FxContext;
import net.alan.jupiter.fx.config.SessionPersistence;
import net.alan.jupiter.fx.config.StageManager;
import net.alan.jupiter.fx.util.FxUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoginController {

    private final LoginUseCase loginUseCase;
    private final StageManager stageManager;
    private final FxContext fxContext;
    private final SessionPersistence sessionPersistence;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private CheckBox autoLoginCheckbox;

    @FXML
    private Label messageLabel;

    @FXML
    private void onLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isBlank() || password.isBlank()) {
            messageLabel.setText("用户名和密码不能为空");
            return;
        }

        messageLabel.setText("正在登录...");
        messageLabel.setStyle("-fx-text-fill: #0078D4;");
        usernameField.setDisable(true);
        passwordField.setDisable(true);

        FxUtils.runAsync(
                () -> loginUseCase.execute(username, password),
                result -> {
                    fxContext.setUserId(result.getUserId());
                    fxContext.setUsername(result.getUsername());
                    fxContext.setRole(result.getRole());
                    fxContext.setToken(result.getToken());
                    if (autoLoginCheckbox.isSelected()) {
                        sessionPersistence.save(fxContext);
                    } else {
                        sessionPersistence.clear();
                    }
                    stageManager.showMain((Stage) usernameField.getScene().getWindow());
                },
                ex -> {
                    log.error("登录失败", ex);
                    String msg = (ex instanceof BizException)
                            ? ex.getMessage()
                            : "系统异常，请稍后再试";
                    messageLabel.setText(msg);
                    messageLabel.setStyle("-fx-text-fill: #E81123;");
                    usernameField.setDisable(false);
                    passwordField.setDisable(false);
                    passwordField.requestFocus();
                }
        );
    }
}