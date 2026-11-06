package net.alan.jupiter.fx;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.application.Preloader;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * 启动加载页：细线进度条 + 百分比 + 阶段文案。
 * 进度由 FxLauncher 的阶段通知驱动，动画全部用 JavaFX 时间线实现。
 */
public class SplashPreloader extends Preloader {

    private static final Color BG = Color.web("#0B0E11");
    private static final Color PANEL = Color.web("#11161B");
    private static final Color LINE = Color.web("#222B32");
    private static final Color TRACK = Color.web("#1A2228");
    private static final Color TEXT = Color.web("#E6EDF0");
    private static final Color MUTED = Color.web("#8A97A0");
    private static final Color DIM = Color.web("#5A666E");
    private static final Color ACCENT = Color.web("#3DDC97");

    private static final int TOTAL_STAGES = 6;

    private Stage stage;
    private Label stageLabel;
    private Label stepLabel;
    private Label percentLabel;
    private Label counterLabel;
    private Rectangle fill;
    private Circle head;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private Timeline progressAnim;
    private double targetProgress = 0;
    private int stageIndex = 0;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.initStyle(StageStyle.UNDECORATED);

        Label title = new Label("寝安");
        title.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 32));
        title.setTextFill(TEXT);

        Label subtitle = new Label("宿舍查寝系统");
        subtitle.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, 12));
        subtitle.setTextFill(MUTED);

        VBox brand = new VBox(2, title, subtitle);
        brand.setAlignment(Pos.CENTER_LEFT);

        Label version = new Label("v1.0.0");
        version.setFont(Font.font("Consolas", FontWeight.NORMAL, 10));
        version.setTextFill(DIM);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(10, brand, headerSpacer, version);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 26, 0));

        stageLabel = new Label("正在启动");
        stageLabel.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 13));
        stageLabel.setTextFill(TEXT);

        stepLabel = new Label("初始化运行环境");
        stepLabel.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, 11));
        stepLabel.setTextFill(DIM);

        counterLabel = new Label("阶段 0 / " + TOTAL_STAGES);
        counterLabel.setFont(Font.font("Consolas", FontWeight.NORMAL, 10));
        counterLabel.setTextFill(DIM);

        Region labelSpacer = new Region();
        HBox.setHgrow(labelSpacer, Priority.ALWAYS);
        HBox labelRow = new HBox(8, stageLabel, labelSpacer, counterLabel);
        labelRow.setAlignment(Pos.CENTER_LEFT);

        // 自定义细进度条：4px 轨道 + 强调色填充 + 末端光点
        Pane barPane = new Pane();
        barPane.setPrefHeight(8);
        barPane.setMinHeight(8);

        Rectangle track = new Rectangle();
        track.setHeight(4);
        track.setY(2);
        track.setFill(TRACK);
        track.widthProperty().bind(barPane.widthProperty());

        fill = new Rectangle();
        fill.setHeight(4);
        fill.setY(2);
        fill.setFill(ACCENT);
        fill.widthProperty().bind(barPane.widthProperty().multiply(progress));

        head = new Circle(3.5, ACCENT);
        head.centerYProperty().set(4);
        head.centerXProperty().bind(fill.widthProperty());
        head.visibleProperty().bind(progress.greaterThan(0.02));

        barPane.getChildren().addAll(track, fill, head);

        percentLabel = new Label("0%");
        percentLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
        percentLabel.setTextFill(ACCENT);
        percentLabel.textProperty().bind(progress.multiply(100).asString("%.0f%%"));

        HBox barRow = new HBox(14, barPane, percentLabel);
        barRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(barPane, Priority.ALWAYS);

        VBox content = new VBox(10, labelRow, stepLabel, barRow);
        content.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(header, content);
        root.setPadding(new Insets(30, 34, 30, 34));
        root.setStyle("-fx-background-color: #0B0E11; -fx-border-color: #222B32; -fx-border-width: 1;");
        root.setPrefSize(540, 228);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.centerOnScreen();

        FadeTransition fadeIn = new FadeTransition(Duration.millis(240), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        stage.show();
        Platform.runLater(stage::centerOnScreen);
    }

    @Override
    public void handleApplicationNotification(PreloaderNotification info) {
        if (!(info instanceof StageNotification notification)) {
            return;
        }
        String message = notification.getMessage();
        stageIndex++;
        stageLabel.setText("正在启动");
        stepLabel.setText(message);
        counterLabel.setText("阶段 " + Math.min(stageIndex, TOTAL_STAGES)
                + " / " + TOTAL_STAGES);
        targetProgress = Math.min(0.98, stageIndex / (double) TOTAL_STAGES);
        animateTo(targetProgress);

        if (message.contains("完成")) {
            targetProgress = 1.0;
            animateTo(1.0);
            fadeOutAndHide();
        }
    }

    @Override
    public void handleStateChangeNotification(StateChangeNotification info) {
        if (info.getType() == StateChangeNotification.Type.BEFORE_START) {
            fadeOutAndHide();
        }
    }

    private void animateTo(double target) {
        if (progressAnim != null) {
            progressAnim.stop();
        }
        progressAnim = new Timeline(new KeyFrame(
                Duration.millis(480),
                new KeyValue(progress, target, Interpolator.EASE_BOTH)));
        progressAnim.play();
    }

    private void fadeOutAndHide() {
        if (stage == null || !stage.isShowing()) {
            return;
        }
        FadeTransition fade = new FadeTransition(Duration.millis(360), stage.getScene().getRoot());
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        fade.setOnFinished(event -> stage.hide());
        fade.play();
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
