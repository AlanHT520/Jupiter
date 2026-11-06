package net.alan.jupiter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.alan.jupiter.data.local.entity.SyncOutboxEntity

@Dao
interface SyncOutboxDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: SyncOutboxEntity): Long

    @Query(
        """
        SELECT * FROM sync_outbox
        WHERE ownerId = :ownerId AND state IN (:states)
        ORDER BY createdAt ASC
        """
    )
    suspend fun getByStates(ownerId: String, states: List<String>): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE ownerId = :ownerId ORDER BY createdAt DESC")
    fun observeByOwner(ownerId: String): Flow<List<SyncOutboxEntity>>

    @Query("SELECT * FROM sync_outbox WHERE ownerId = :ownerId AND clientOpId = :clientOpId LIMIT 1")
    suspend fun getByClientOpId(ownerId: String, clientOpId: String): SyncOutboxEntity?

    @Query(
        """
        SELECT * FROM sync_outbox
        WHERE ownerId = :ownerId AND taskId = :taskId AND roomId = :roomId
          AND state IN ('QUEUED', 'FAILED')
        ORDER BY createdAt DESC LIMIT 1
        """
    )
    suspend fun getSubmittableByRoom(
        ownerId: String,
        taskId: String,
        roomId: String
    ): SyncOutboxEntity?

    @Query(
        """
        UPDATE sync_outbox
        SET retryCount = retryCount + 1, lastError = :error, state = :state, updatedAt = :at
        WHERE clientOpId = :clientOpId
        """
    )
    suspend fun recordFailure(clientOpId: String, state: String, error: String?, at: Long)

    @Query("UPDATE sync_outbox SET payload = :payload, updatedAt = :at WHERE clientOpId = :clientOpId")
    suspend fun updatePayload(clientOpId: String, payload: String, at: Long)

    @Query("UPDATE sync_outbox SET state = :state, updatedAt = :at WHERE clientOpId = :clientOpId")
    suspend fun setState(clientOpId: String, state: String, at: Long)

    @Query("DELETE FROM sync_outbox WHERE clientOpId = :clientOpId")
    suspend fun deleteByClientOpId(clientOpId: String)

    /** 迁移遗留（ownerId='')的队列行，认领逻辑在 LocalStore 里做（避免 UPDATE OR IGNORE）。 */
    @Query("SELECT * FROM sync_outbox WHERE ownerId = ''")
    suspend fun getLegacy(): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE ownerId = ''")
    suspend fun deleteLegacy()

    @Query("DELETE FROM sync_outbox WHERE ownerId = :ownerId")
    suspend fun deleteByOwner(ownerId: String)

    @Query("DELETE FROM sync_outbox")
    suspend fun deleteAll()

    @Query(
        """
        SELECT COUNT(*) FROM sync_outbox
        WHERE ownerId = :ownerId AND state IN ('QUEUED', 'IN_FLIGHT', 'FAILED')
        """
    )
    fun observePendingCount(ownerId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE ownerId = :ownerId")
    suspend fun count(ownerId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM sync_outbox
        WHERE ownerId = :ownerId AND state IN ('QUEUED', 'IN_FLIGHT', 'FAILED')
        """
    )
    suspend fun countPending(ownerId: String): Int

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE ownerId = :ownerId AND state = 'FAILED'")
    fun observeFailedCount(ownerId: String): Flow<Int>
}
