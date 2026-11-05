package net.alan.jupiter.fx.util;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * 统一的空状态块：虚线卡片 + 图标 + 标题 + 指引。
 */
public final class EmptyStates {

    private EmptyStates() {
    }

    public static VBox block(String title, String hint, FontAwesomeSolid icon) {
        VBox box = new VBox();
        box.getStyleClass().add("empty-block");

        FontIcon graphic = new FontIcon(icon);
        graphic.getStyleClass().add("empty-block-icon");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-block-title");

        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("empty-block-hint");
        hintLabel.setWrapText(true);

        box.getChildren().addAll(graphic, titleLabel, hintLabel);
        return box;
    }
}
