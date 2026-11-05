package net.alan.jupiter.fx.util;

import javafx.scene.control.Label;

/**
 * 状态徽标工厂：统一桌面端表格与列表中的状态呈现。
 * 变体对应 chinan.css 中的 .badge-success / .badge-warning / .badge-danger / .badge-info / .badge-muted。
 */
public final class Badges {

    private Badges() {
    }

    public static Label of(String text, String variant) {
        Label label = new Label(text == null || text.isBlank() ? "-" : text);
        label.getStyleClass().addAll("badge", "badge-" + (variant == null ? "muted" : variant));
        return label;
    }

    /** 任务状态枚举名 → 徽标变体。 */
    public static String taskStatusVariant(String statusName) {
        if (statusName == null) return "muted";
        return switch (statusName) {
            case "COMPLETED" -> "success";
            case "IN_PROGRESS" -> "warning";
            case "CANCELLED" -> "danger";
            default -> "muted";
        };
    }

    /** 查寝状态中文标签 → 徽标变体。 */
    public static String checkStatusVariant(String label) {
        if (label == null) return "muted";
        return switch (label) {
            case "在寝" -> "success";
            case "旷宿", "不在", "晚归" -> "danger";
            case "事假", "病假", "长期病假", "长病假" -> "warning";
            case "征兵", "实习", "走读" -> "info";
            default -> "muted";
        };
    }

    /** 账号启用状态文本 → 徽标变体。 */
    public static String activeVariant(String text) {
        return "启用".equals(text) ? "success" : "muted";
    }
}
