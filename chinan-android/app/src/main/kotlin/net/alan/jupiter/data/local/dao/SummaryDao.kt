package net.alan.jupiter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.alan.jupiter.data.local.entity.SummarySnapshotEntity
import net.alan.jupiter.domain.summary.SummaryRow

@Dao
interface SummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: SummarySnapshotEntity)

    @Query("SELECT * FROM summary_snapshots WHERE ownerId = :ownerId AND scope = :scope LIMIT 1")
    suspend fun get(ownerId: String, scope: String): SummarySnapshotEntity?

    @Query("DELETE FROM summary_snapshots WHERE ownerId = :ownerId")
    suspend fun deleteByOwner(ownerId: String)

    @Query("DELETE FROM summary_snapshots")
    suspend fun deleteAll()

    /**
     * 本地聚合入口：学生快照左连接任务快照（楼栋/taskDate）与本地查寝记录。
     * 没有记录的学生 `syncState` / `statusCode` 为 null，代表“未查”。
     */
    @Query(
        """
        SELECT s.taskId AS taskId,
               t.taskDate AS taskDate,
               t.buildingName AS buildingName,
               s.className AS className,
               s.roomNumber AS roomNumber,
               s.studentId AS studentId,
               r.statusCode AS statusCode,
               r.syncState AS syncState
        FROM student_snapshots s
        LEFT JOIN task_snapshots t
               ON t.ownerId = s.ownerId AND t.taskId = s.taskId
        LEFT JOIN check_records_local r
               ON r.ownerId = s.ownerId AND r.taskId = s.taskId
              AND r.roomId = s.roomId AND r.studentId = s.studentId
        WHERE s.ownerId = :ownerId
        """
    )
    suspend fun summaryRows(ownerId: String): List<SummaryRow>
}
