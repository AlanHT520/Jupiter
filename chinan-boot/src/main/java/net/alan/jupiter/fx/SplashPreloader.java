package net.alan.jupiter.fx;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Preloader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class SplashPreloader extends Preloader {

    private Stage stage;
    private Label statusLabel;
    private Label dotsLabel;
    private Timeline dotsTimeline;
    private int dotCount = 0;

    private Label codeLabel;
    private Label cursorLabel;
    private Timeline typingTimeline;
    private Timeline cursorTimeline;
    private int charIndex = 0;

    private static final String CODE_TEXT =
            "public class Main {\n" +
            "    static void main() {\n" +
            "        IO.println(\"Hello, Chinan!\");\n" +
            "    }\n" +
            "}";

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.initStyle(StageStyle.UNDECORATED);

        Label titleLabel = new Label("Chinan");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 40));
        titleLabel.setTextFill(Color.WHITE);

        Label editionLabel = new Label("Java Edition");
        editionLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 16));
        editionLabel.setTextFill(Color.rgb(130, 130, 130));

        Label subtitleLabel = new Label("寝室管理系统");
        subtitleLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 15));
        subtitleLabel.setTextFill(Color.rgb(130, 130, 130));

        VBox topBox = new VBox(2);
        topBox.setPadding(new Insets(48, 0, 0, 52));
        topBox.getChildren().addAll(titleLabel, editionLabel, subtitleLabel);

        statusLabel = new Label("正在启动服务...");
        statusLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 13));
        statusLabel.setTextFill(Color.rgb(150, 150, 150));

        dotsLabel = new Label("");
        dotsLabel.setFont(Font.font("Consolas", FontWeight.NORMAL, 13));
        dotsLabel.setTextFill(Color.rgb(150, 150, 150));
        dotsLabel.setMinWidth(32);

        HBox statusRow = new HBox(4);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.getChildren().addAll(statusLabel, dotsLabel);

        Label versionLabel = new Label("v-0.8.9");
        versionLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 13));
        versionLabel.setTextFill(Color.rgb(110, 110, 110));

        HBox bottomRow = new HBox();
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        bottomRow.getChildren().addAll(statusRow, spacer(), versionLabel);

        Label copyrightLabel = new Label("©2026  本软件著作权归张浩天所有。未经作者事先书面授权，任何个人或组织不得使用、复制、修改或分发本软件，不得将其用于商业用途，亦不得将其作为任何公开展示的组成部分。");
        copyrightLabel.setFont(Font.font("Microsoft YaHei", FontWeight.NORMAL, 11));
        copyrightLabel.setTextFill(Color.rgb(80, 80, 80));
        copyrightLabel.setWrapText(true);
        copyrightLabel.setMaxWidth(540);

        VBox bottomBox = new VBox(4);
        bottomBox.setPadding(new Insets(0, 36, 28, 52));
        bottomBox.getChildren().addAll(bottomRow, copyrightLabel);

        VBox codeBox = createCodeAnimation();

        BorderPane root = new BorderPane();
        root.setTop(topBox);
        root.setCenter(codeBox);
        root.setBottom(bottomBox);
        root.setStyle("-fx-background-color: #2B2B2B;");
        BorderPane.setAlignment(codeBox, Pos.CENTER);
        BorderPane.setAlignment(bottomBox, Pos.BOTTOM_LEFT);

        Scene scene = new Scene(root, 620, 380);
        scene.setFill(Color.TRANSPARENT);

        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.show();

        startDotsAnimation();
        startTypingAnimation();
        centerStage(stage);
    }

    private HBox spacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private void startDotsAnimation() {
        dotsTimeline = new Timeline(
                new KeyFrame(Duration.millis(400), e -> {
                    dotCount = (dotCount + 1) % 4;
                    dotsLabel.setText(".".repeat(dotCount));
                })
        );
        dotsTimeline.setCycleCount(Timeline.INDEFINITE);
        dotsTimeline.play();
    }

    private VBox createCodeAnimation() {
        codeLabel = new Label();
        codeLabel.setFont(Font.font("Consolas", FontWeight.NORMAL, 14));
        codeLabel.setTextFill(Color.rgb(106, 135, 89));
        codeLabel.setStyle("-fx-background-color: #1E1E1E; -fx-padding: 18 24 18 24;");
        codeLabel.setMinHeight(145);
        codeLabel.setMaxWidth(460);

        cursorLabel = new Label("|");
        cursorLabel.setFont(Font.font("Consolas", FontWeight.NORMAL, 14));
        cursorLabel.setTextFill(Color.rgb(106, 135, 89));

        HBox codeRow = new HBox(2);
        codeRow.setAlignment(Pos.TOP_LEFT);
        codeRow.getChildren().addAll(codeLabel, cursorLabel);

        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(10, 52, 10, 52));
        box.getChildren().add(codeRow);

        return box;
    }

    private void startTypingAnimation() {
        typingTimeline = new Timeline(
                new KeyFrame(Duration.millis(30), e -> {
                    if (charIndex < CODE_TEXT.length()) {
                        charIndex++;
                        codeLabel.setText(CODE_TEXT.substring(0, charIndex));
                    } else {
                        typingTimeline.stop();
                        Timeline restartDelay = new Timeline(
                                new KeyFrame(Duration.seconds(1.5), ev -> {
                                    charIndex = 0;
                                    codeLabel.setText("");
                                    startTypingAnimation();
                                })
                        );
                        restartDelay.setCycleCount(1);
                        restartDelay.play();
                    }
                })
        );
        typingTimeline.setCycleCount(Timeline.INDEFINITE);
        typingTimeline.play();

        cursorTimeline = new Timeline(
                new KeyFrame(Duration.millis(530), e -> {
                    cursorLabel.setVisible(!cursorLabel.isVisible());
                })
        );
        cursorTimeline.setCycleCount(Timeline.INDEFINITE);
        cursorTimeline.play();
    }

    private void stopCodeAnimation() {
        if (typingTimeline != null) {
            typingTimeline.stop();
        }
        if (cursorTimeline != null) {
            cursorTimeline.stop();
        }
    }

    @Override
    public void handleApplicationNotification(PreloaderNotification info) {
        if (info instanceof StageNotification sn) {
            statusLabel.setText(sn.getMessage());
            dotCount = 0;
            dotsLabel.setText("");
        }
    }

    @Override
    public void handleStateChangeNotification(StateChangeNotification info) {
        if (info.getType() == StateChangeNotification.Type.BEFORE_START) {
            if (dotsTimeline != null) {
                dotsTimeline.stop();
            }
            stopCodeAnimation();
            statusLabel.setText("准备就绪");
            dotsLabel.setText("");

            FadeTransition ft = new FadeTransition(Duration.millis(500), stage.getScene().getRoot());
            ft.setFromValue(1.0);
            ft.setToValue(0.0);
            ft.setOnFinished(e -> stage.hide());
            ft.play();
        }
    }

    private void centerStage(Stage stage) {
        stage.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (isShowing) {
                javafx.application.Platform.runLater(stage::centerOnScreen);
            }
        });
    }

    public static class StageNotification implements PreloaderNotification {
        private final String message;

        public StageNotification(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}