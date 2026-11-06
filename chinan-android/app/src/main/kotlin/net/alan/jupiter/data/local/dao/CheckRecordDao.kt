package net.alan.jupiter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.alan.jupiter.data.local.entity.CheckRecordLocalEntity

/** 每个任务还剩多少条待上行记录（任务列表 `● SYNC N`）。 */
data class TaskPendingCount(
    val taskId: String,
    val pending: Int
)

@Dao
interface CheckRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CheckRecordLocalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<CheckRecordLocalEntity>)

    @Query("SELECT * FROM check_records_local WHERE ownerId = :ownerId AND taskId = :taskId")
    suspend fun getByTask(ownerId: String, taskId: String): List<CheckRecordLocalEntity>

    @Query("SELECT * FROM check_records_local WHERE ownerId = :ownerId AND taskId = :taskId")
    fun observeByTask(ownerId: String, taskId: String): Flow<List<CheckRecordLocalEntity>>

    @Query(
        """
        SELECT * FROM check_records_local
        WHERE ownerId = :ownerId AND taskId = :taskId AND roomId = :roomId
        """
    )
    suspend fun getByRoom(ownerId: String, taskId: String, roomId: String): List<CheckRecordLocalEntity>

    @Query(
        """
        SELECT * FROM check_records_local
        WHERE ownerId = :ownerId AND taskId = :taskId AND roomId = :roomId
        """
    )
    fun observeByRoom(ownerId: String, taskId: String, roomId: String): Flow<List<CheckRecordLocalEntity>>

    @Query(
        """
        SELECT * FROM check_records_local
        WHERE ownerId = :ownerId AND taskId = :taskId AND roomId = :roomId AND studentId = :studentId
        LIMIT 1
        """
    )
    suspend fun getByStudent(
        ownerId: String,
        taskId: String,
        roomId: String,
        studentId: String
    ): CheckRecordLocalEntity?

    @Query("SELECT * FROM check_records_local WHERE ownerId = :ownerId AND syncState IN (:states)")
    suspend fun getByStates(ownerId: String, states: List<String>): List<CheckRecordLocalEntity>

    @Query(
        """
        SELECT taskId, COUNT(*) AS pending FROM check_records_local
        WHERE ownerId = :ownerId AND syncState IN ('QUEUED', 'IN_FLIGHT', 'FAILED')
        GROUP BY taskId
        """
    )
    fun observePendingByTask(ownerId: String): Flow<List<TaskPendingCount>>

    @Query(
        """
        SELECT COUNT(*) FROM check_records_local
        WHERE ownerId = :ownerId AND syncState IN ('QUEUED', 'IN_FLIGHT', 'FAILED')
        """
    )
    fun observePendingCount(ownerId: String): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM check_records_local
        WHERE ownerId = :ownerId AND taskId = :taskId AND roomId = :roomId
          AND syncState IN ('QUEUED', 'IN_FLIGHT', 'FAILED')
        """
    )
    suspend fun countPendingInRoom(ownerId: String, taskId: String, roomId: String): Int

    @Query("SELECT COUNT(*) FROM check_records_local WHERE ownerId = :ownerId")
    suspend fun countByOwner(ownerId: String): Int

    @Query("SELECT COUNT(*) FROM check_records_local")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM check_records_local WHERE ownerId = :ownerId")
    fun observeTotalCount(ownerId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM check_records_local WHERE ownerId = :ownerId AND syncState = 'ACKED'")
    fun observeAckedCount(ownerId: String): Flow<Int>

    @Query(
        """
        UPDATE check_records_local
        SET syncState = 'ACKED', ackedAt = :at, submittedAt = COALESCE(submittedAt, :at), lastError = NULL
        WHERE ownerId = :ownerId AND clientOpId = :clientOpId
        """
    )
    suspend fun markAcked(ownerId: String, clientOpId: String, at: Long)

    @Query(
        """
        UPDATE check_records_local
        SET syncState = :state, lastError = :error, updatedAt = :at
        WHERE ownerId = :ownerId AND clientOpId = :clientOpId
        """
    )
    suspend fun updateStateForClientOp(
        ownerId: String,
        clientOpId: String,
        state: String,
        error: String?,
        at: Long
    )

    @Query(
        "SELECT proofPath FROM check_records_local WHERE ownerId = :ownerId AND proofPath IS NOT NULL"
    )
    suspend fun getProofPaths(ownerId: String): List<String>

    @Query("SELECT proofPath FROM check_records_local WHERE proofPath IS NOT NULL")
    suspend fun getAllProofPaths(): List<String>

    /** 迁移遗留（ownerId=''）的记录，认领逻辑在 LocalStore 里做（避免 UPDATE OR IGNORE）。 */
    @Query("SELECT * FROM check_records_local WHERE ownerId = ''")
    suspend fun getLegacy(): List<CheckRecordLocalEntity>

    @Query("DELETE FROM check_records_local WHERE ownerId = ''")
    suspend fun deleteLegacy()

    @Query("DELETE FROM check_records_local WHERE ownerId = :ownerId")
    suspend fun deleteByOwner(ownerId: String)

    @Query("DELETE FROM check_records_local")
    suspend fun deleteAll()
}
