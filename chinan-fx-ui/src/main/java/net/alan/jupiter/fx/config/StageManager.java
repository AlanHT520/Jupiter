package net.alan.jupiter.fx.config;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class StageManager {

    private final ApplicationContext context;

    public void showMain(Stage stage) {
        show(stage, "/fxml/main.fxml", "Chinan 寝室管理系统 · v-0.8.9", 1180, 760, true);
        stage.setMinWidth(1000);
        stage.setMinHeight(640);
    }

    private void show(Stage stage, String fxml, String title, int w, int h, boolean resizable) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            loader.setControllerFactory(context::getBean);
            Parent root = loader.load();

            Scene scene = new Scene(root, w, h);
            scene.getStylesheets().add(
                    getClass().getResource("/css/chinan.css").toExternalForm()
            );

            stage.setTitle(title);
            stage.setResizable(resizable);
            stage.getIcons().add(new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/chinan.png"))));
            stage.setScene(scene);
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            log.error("加载FXML失败: {}", fxml, e);
            throw new RuntimeException("界面加载失败，请重启应用", e);
        }
    }
}