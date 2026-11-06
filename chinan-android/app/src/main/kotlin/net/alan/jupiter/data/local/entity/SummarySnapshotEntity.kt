package net.alan.jupiter.data.local.entity

import androidx.room.Entity

/**
 * 总汇总快照：A09 页面只读本地聚合结果，标注 `● CACHED asOf`。
 *
 * `scope` 例如 `ALL` / `TODAY` / `WEEK`，`countsJson` 存 SummaryCounts，
 * `itemsJson` 存按楼栋/班级分组的明细（SummarySnapshotPayload）。
 */
@Entity(
    tableName = "summary_snapshots",
    primaryKeys = ["ownerId", "scope"]
)
data class SummarySnapshotEntity(
    val ownerId: String,
    val scope: String,
    val asOf: Long,
    val countsJson: String,
    val itemsJson: String? = null,
    val pendingCount: Int = 0,
    val taskCount: Int = 0,
    val studentCount: Int = 0
)
