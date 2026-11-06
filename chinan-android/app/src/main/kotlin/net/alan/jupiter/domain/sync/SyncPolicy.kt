package net.alan.jupiter.domain.sync

import net.alan.jupiter.domain.model.SyncState
import java.time.LocalDate
import java.util.UUID

/**
 * 上行队列的纯逻辑（不依赖 Android，可直接跑 JVM 单元测试）。
 *
 * 这里集中三条已确认的规则：
 * 1. 幂等：一次提交批次生成一个 `clientOpId`（UUID），重试复用同一个值；
 * 2. 状态机：失败 retryCount+1，超过 [MAX_RETRY] 才升为 FAILED；
 * 3. 补交：只按 `taskDate`（字符串 ISO 日期）过滤待提交记录，不改写 taskDate。
 */
object SyncPolicy {

    /** 失败重试上限：第 4 次失败才升为 FAILED。 */
    const val MAX_RETRY = 3

    /** 新批次生成新的幂等键。 */
    fun newClientOpId(): String = UUID.randomUUID().toString()

    /**
     * 重试必须复用同一个 `clientOpId`：只有本地没有既存值时才生成新的。
     * 这是服务端去重（dedupe）的唯一依据，不能和 TCP 的 requestId 混用。
     */
    fun resolveClientOpId(existing: String?): String =
        existing?.trim()?.takeIf { it.isNotEmpty() } ?: newClientOpId()

    /** 该状态的记录允许被上行。 */
    fun canSubmit(state: SyncState): Boolean = state in SyncState.SUBMITTABLE

    /**
     * @param retryCountAfterFailure 已经失败了几次（含本次）
     * @return 该批次失败后应停留的状态：仍可重试 → QUEUED；超限 → FAILED
     */
    fun nextStateAfterFailure(retryCountAfterFailure: Int, maxRetry: Int = MAX_RETRY): SyncState =
        if (retryCountAfterFailure > maxRetry) SyncState.FAILED else SyncState.QUEUED

    /** 重试预算是否已用完（用于 UI 显示 `retry 3/3`）。 */
    fun isRetryExhausted(retryCount: Int, maxRetry: Int = MAX_RETRY): Boolean = retryCount > maxRetry

    /** 日志/UI 展示用的短 id。 */
    fun shortClientOpId(clientOpId: String?): String =
        clientOpId?.takeIf { it.isNotBlank() }?.take(8) ?: "--"
}

/**
 * 待提交批次（SyncOutbox 的一行的纯数据视图）。
 */
data class PendingSubmission(
    val clientOpId: String,
    val taskId: String,
    val roomId: String,
    val taskDate: String?,
    val retryCount: Int = 0,
    val createdAt: Long = 0L
)

/**
 * 时段筛选 + 分组：补交只按 taskDate 过滤，不改写 taskDate。
 *
 * 规则：
 * - 不传窗口（from/to 都空）→ 全部待提交记录；
 * - 传了窗口 → 只挑 taskDate 落在闭区间内的记录；
 * - 窗口内 taskDate 为空的记录仍然保留（无法判断日期，不能静默丢弃）。
 */
object TaskDateWindow {

    fun isValidIsoDate(value: String?): Boolean = parseIsoDate(value) != null

    fun parseIsoDate(value: String?): LocalDate? {
        val text = value?.trim().orEmpty()
        if (text.length != 10) return null
        return runCatching { LocalDate.parse(text) }.getOrNull()
    }

    fun todayIso(today: LocalDate = LocalDate.now()): String = today.toString()

    fun inWindow(taskDate: String?, fromIso: String? = null, toIso: String? = null): Boolean {
        val from = fromIso?.trim().orEmpty()
        val to = toIso?.trim().orEmpty()
        if (from.isEmpty() && to.isEmpty()) return true

        val date = taskDate?.trim().orEmpty()
        // 没有日期的记录无法判断范围，宁可补交也不静默漏掉
        if (date.isEmpty()) return true

        if (from.isNotEmpty() && date < from) return false
        if (to.isNotEmpty() && date > to) return false
        return true
    }
}

/**
 * 按 taskDate 分组提交（同一 taskDate 的批次连续上行，便于报表按发布日期入账）。
 */
object SubmitGrouper {

    data class Group(val taskDate: String?, val submissions: List<PendingSubmission>)

    fun select(
        submissions: List<PendingSubmission>,
        fromIso: String? = null,
        toIso: String? = null
    ): List<PendingSubmission> = submissions
        .filter { TaskDateWindow.inWindow(it.taskDate, fromIso, toIso) }
        .sortedWith(compareBy({ it.taskDate ?: "" }, { it.createdAt }, { it.clientOpId }))

    fun groupByTaskDate(submissions: List<PendingSubmission>): List<Group> =
        submissions
            .groupBy { it.taskDate }
            .toList()
            .sortedBy { it.first ?: "" }
            .map { (taskDate, items) -> Group(taskDate, items) }
}
