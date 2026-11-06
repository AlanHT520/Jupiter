package net.alan.jupiter.domain.model

/**
 * 本地查寝记录 / 上行队列的状态机（docs/offline-sync.md）：
 *
 * `LOCAL_DRAFT → QUEUED → IN_FLIGHT → ACKED / FAILED / CONFLICT`
 *
 * - `LOCAL_DRAFT`：用户在房间页改了状态但还没点提交，只落本地，不上行；
 * - `QUEUED`：已写 CheckRecordLocal + SyncOutbox，等网络；
 * - `IN_FLIGHT`：本次正在发送（发送前标记，进程被杀也能在下次启动时重新捡起）；
 * - `ACKED`：服务端已确认，此后以下行数据为准；
 * - `FAILED`：重试 3 次仍失败，保留人工入口，不静默丢弃；
 * - `CONFLICT`：服务端拒绝（任务取消/越权等），保留本地记录等待人工处理。
 */
enum class SyncState {
    LOCAL_DRAFT,
    QUEUED,
    IN_FLIGHT,
    ACKED,
    FAILED,
    CONFLICT;

    /** 是否属于“本地有、服务端还没有”的记录。 */
    val isPending: Boolean
        get() = this == QUEUED || this == IN_FLIGHT || this == FAILED

    companion object {
        fun fromName(name: String?): SyncState =
            entries.firstOrNull { it.name == name } ?: LOCAL_DRAFT

        val PENDING: Set<SyncState> = linkedSetOf(QUEUED, IN_FLIGHT, FAILED)
        val SUBMITTABLE: Set<SyncState> = linkedSetOf(QUEUED, FAILED)
        val PENDING_NAMES: List<String> = PENDING.map { it.name }
        val SUBMITTABLE_NAMES: List<String> = SUBMITTABLE.map { it.name }
    }
}
