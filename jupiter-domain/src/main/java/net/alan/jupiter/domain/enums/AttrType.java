package net.alan.jupiter.domain.enums;

public enum AttrType {

    TEXT("文本"),
    NUMBER("数字"),
    SELECT("下拉选择"),
    MULTI_SELECT("多选"),
    DATE("日期"),
    BOOLEAN("布尔");

    private final String label;

    AttrType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}