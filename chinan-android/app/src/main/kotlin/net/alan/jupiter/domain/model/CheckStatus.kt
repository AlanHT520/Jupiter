package net.alan.jupiter.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CheckStatus(val label: String, val code: Int) {
    UNKNOWN("未查", 0),
    PRESENT("在寝", 1),
    PERSONAL_LEAVE("事假", 2),
    SICK_LEAVE("病假", 3),
    ABSENT("旷宿", 4),
    MILITARY("征兵", 5),
    INTERN("实习", 6),
    DAY_STUDENT("走读", 7),
    LATE_RETURN("晚归", 8),
    LONG_SICK_LEAVE("长期病假", 9);

    companion object {
        fun fromCode(code: Int?): CheckStatus? =
            if (code != null) entries.find { it.code == code } else null

        fun fromName(name: String?): CheckStatus? =
            name?.let { n -> entries.find { it.name == n } }
    }
}