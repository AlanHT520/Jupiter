package net.alan.jupiter.domain.enums;

public enum CheckStatus {

    PRESENT("在寝", 1),
    PERSONAL_LEAVE("事假", 2),
    SICK_LEAVE("病假", 3),
    ABSENT("旷宿", 4),
    MILITARY("征兵", 5),
    INTERN("实习", 6),
    DAY_STUDENT("走读", 7),
    LATE_RETURN("晚归", 8),
    LONG_SICK_LEAVE("长期病假", 9),
    UNKNOWN("未知", 0);

    private final String label;
    private final int code;

    CheckStatus(String label, int code) {
        this.label = label;
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public int getCode() {
        return code;
    }

    public boolean isLeave() {
        return this == PERSONAL_LEAVE || this == SICK_LEAVE;
    }

    public static CheckStatus fromCode(int code) {
        for (CheckStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return UNKNOWN;
    }
}