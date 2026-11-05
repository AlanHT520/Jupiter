package net.alan.jupiter.fx.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;

import java.util.Optional;

public final class DialogUtils {

    private DialogUtils() {}

    public static void showInfo(String title, String message) {
        alert(Alert.AlertType.INFORMATION, title, message);
    }

    public static void showError(String title, String message) {
        alert(Alert.AlertType.ERROR, title, message);
    }

    public static void showWarn(String title, String message) {
        alert(Alert.AlertType.WARNING, title, message);
    }

    public static boolean showConfirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        applyStylesheet(alert);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private static void alert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        applyStylesheet(alert);
        alert.showAndWait();
    }

    private static void applyStylesheet(Alert alert) {
        DialogPane pane = alert.getDialogPane();
        java.net.URL cssUrl = DialogUtils.class.getResource("/css/chinan.css");
        if (cssUrl != null) {
            pane.getStylesheets().add(cssUrl.toExternalForm());
        }
        pane.getStyleClass().add("dialog-pane");
    }
}