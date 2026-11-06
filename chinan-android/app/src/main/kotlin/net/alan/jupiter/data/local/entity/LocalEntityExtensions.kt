package net.alan.jupiter.data.local.entity

import net.alan.jupiter.domain.model.CheckStatus
import net.alan.jupiter.domain.model.SyncState

/**
 * 实体的派生属性放在实体**之外**：Room 会把实体里声明的属性当列处理，
 * 只读计算属性容易触发 "Cannot find setter for field"，所以统一写成扩展属性。
 */

/** 本地记录的同步状态（字符串列 → 枚举）。 */
val CheckRecordLocalEntity.recordSync: SyncState
    get() = SyncState.fromName(syncState)

/** 是否属于“本地有、服务端还没有”的记录（QUEUED/IN_FLIGHT/FAILED）。 */
val CheckRecordLocalEntity.isPendingSync: Boolean
    get() = recordSync.isPending

/** 本地记录的状态枚举。 */
val CheckRecordLocalEntity.checkStatusValue: CheckStatus?
    get() = CheckStatus.fromCode(statusCode)

/** outbox 行的同步状态（字符串列 → 枚举）。 */
val SyncOutboxEntity.outboxSync: SyncState
    get() = SyncState.fromName(state)
