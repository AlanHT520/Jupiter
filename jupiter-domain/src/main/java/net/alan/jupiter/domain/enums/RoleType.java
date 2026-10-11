package net.alan.jupiter.domain.enums;

public enum RoleType {

    ADMIN("管理员"),
    COUNSELOR("辅导员"),
    CAPTAIN("学生组长");

    private final String label;

    RoleType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}