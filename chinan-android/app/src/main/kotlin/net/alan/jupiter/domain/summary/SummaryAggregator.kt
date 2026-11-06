package net.alan.jupiter.domain.summary

import kotlinx.serialization.Serializable
import net.alan.jupiter.domain.model.CheckStatus
import net.alan.jupiter.domain.model.SyncState
import java.time.LocalDate

/**
 * 总汇总的本地聚合口径（A09）。
 *
 * 数据来源：Room 里的 TaskSnapshot + StudentSnapshot + CheckRecordLocal 三表左连接，
 * 不请求服务端、不把 2140 人明细下发到手机；因此页面标注 `● CACHED asOf`，
 * 有未 ACK 记录时再标注「含 N 条待同步，为本地预估」。
 */
data class SummaryRow(
    val taskId: String,
    val taskDate: String?,
    val buildingName: String?,
    val className: String?,
    val roomNumber: String?,
    val studentId: String,
    val statusCode: Int?,
    val syncState: String?
)

/** 汇总范围：全部 / 今日 / 近 7 天（按 taskDate）。 */
enum class SummaryScope(val label: String, val code: String) {
    ALL("全部", "ALL"),
    TODAY("今日", "TODAY"),
    WEEK("近 7 天", "WEEK");

    fun matches(taskDate: String?, today: LocalDate = LocalDate.now()): Boolean = when (this) {
        ALL -> true
        TODAY -> taskDate == today.toString()
        WEEK -> {
            val date = taskDate?.trim().orEmpty()
            date.isNotEmpty() && date >= today.minusDays(6).toString() && date <= today.toString()
        }
    }

    companion object {
        fun fromCode(code: String?): SummaryScope = entries.firstOrNull { it.code == code } ?: ALL
    }
}

@Serializable
data class SummaryCounts(
    val should: Int = 0,
    val actual: Int = 0,
    val leave: Int = 0,
    val absent: Int = 0,
    val unchecked: Int = 0
) {
    val checked: Int get() = actual + leave + absent
    val completionRate: Float get() = if (should > 0) actual.toFloat() / should else 0f
}

@Serializable
data class SummaryBucket(
    val key: String,
    val should: Int,
    val actual: Int
) {
    val rate: Float get() = if (should > 0) actual.toFloat() / should else 0f
}

/** 存进 SummarySnapshot.itemsJson 的完整快照（便于离线显示分组）。 */
@Serializable
data class SummarySnapshotPayload(
    val counts: SummaryCounts = SummaryCounts(),
    val buildings: List<SummaryBucket> = emptyList(),
    val classes: List<SummaryBucket> = emptyList(),
    val pending: Int = 0,
    val taskCount: Int = 0,
    val studentCount: Int = 0
)

data class SummaryResult(
    val scope: SummaryScope,
    val asOf: Long,
    val counts: SummaryCounts,
    val buildings: List<SummaryBucket>,
    val classes: List<SummaryBucket>,
    val pending: Int,
    val taskCount: Int,
    val studentCount: Int
) {
    fun toPayload(): SummarySnapshotPayload = SummarySnapshotPayload(
        counts = counts,
        buildings = buildings,
        classes = classes,
        pending = pending,
        taskCount = taskCount,
        studentCount = studentCount
    )
}

object SummaryAggregator {

    /** 已经有结论（本地记录且不是草稿）的状态才算“已查”。 */
    private fun isDecided(syncState: String?): Boolean {
        if (syncState.isNullOrBlank()) return false
        val state = SyncState.fromName(syncState)
        return state != SyncState.LOCAL_DRAFT
    }

    private enum class Kind { ACTUAL, LEAVE, ABSENT, UNKNOWN }

    private fun kindOf(code: Int?): Kind = when (CheckStatus.fromCode(code)) {
        CheckStatus.PRESENT, CheckStatus.LATE_RETURN -> Kind.ACTUAL
        CheckStatus.ABSENT -> Kind.ABSENT
        CheckStatus.PERSONAL_LEAVE,
        CheckStatus.SICK_LEAVE,
        CheckStatus.MILITARY,
        CheckStatus.INTERN,
        CheckStatus.DAY_STUDENT,
        CheckStatus.LONG_SICK_LEAVE -> Kind.LEAVE
        else -> Kind.UNKNOWN
    }

    fun aggregate(scope: SummaryScope, rows: List<SummaryRow>, asOf: Long): SummaryResult {
        val scoped = rows.filter { scope.matches(it.taskDate) }

        var should = 0
        var actual = 0
        var leave = 0
        var absent = 0
        var unchecked = 0
        var pending = 0

        val buildingShould = linkedMapOf<String, Int>()
        val buildingActual = linkedMapOf<String, Int>()
        val classShould = linkedMapOf<String, Int>()
        val classActual = linkedMapOf<String, Int>()

        for (row in scoped) {
            should++
            val building = row.buildingName?.trim().takeUnless { it.isNullOrEmpty() } ?: "--"
            val className = row.className?.trim().takeUnless { it.isNullOrEmpty() } ?: "--"
            buildingShould[building] = (buildingShould[building] ?: 0) + 1
            classShould[className] = (classShould[className] ?: 0) + 1

            if (SyncState.fromName(row.syncState) in SyncState.PENDING) pending++

            if (!isDecided(row.syncState)) {
                unchecked++
                continue
            }
            when (kindOf(row.statusCode)) {
                Kind.ACTUAL -> {
                    actual++
                    buildingActual[building] = (buildingActual[building] ?: 0) + 1
                    classActual[className] = (classActual[className] ?: 0) + 1
                }
                Kind.LEAVE -> leave++
                Kind.ABSENT -> absent++
                Kind.UNKNOWN -> unchecked++
            }
        }

        fun buckets(keys: Collection<String>, shouldMap: Map<String, Int>, actualMap: Map<String, Int>) =
            keys.map { key ->
                SummaryBucket(key = key, should = shouldMap[key] ?: 0, actual = actualMap[key] ?: 0)
            }.sortedWith(compareByDescending<SummaryBucket> { it.should }.thenBy { it.key })

        return SummaryResult(
            scope = scope,
            asOf = asOf,
            counts = SummaryCounts(
                should = should,
                actual = actual,
                leave = leave,
                absent = absent,
                unchecked = unchecked
            ),
            buildings = buckets(buildingShould.keys, buildingShould, buildingActual),
            classes = buckets(classShould.keys, classShould, classActual),
            pending = pending,
            taskCount = scoped.map { it.taskId }.distinct().size,
            studentCount = scoped.size
        )
    }
}
