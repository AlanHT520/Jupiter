package net.alan.jupiter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.alan.jupiter.data.local.entity.TaskSnapshotEntity

@Dao
interface TaskSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TaskSnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TaskSnapshotEntity)

    @Query(
        """
        SELECT * FROM task_snapshots
        WHERE ownerId = :ownerId
        ORDER BY CASE WHEN taskDate IS NULL OR taskDate = '' THEN 1 ELSE 0 END,
                 taskDate DESC, taskNo DESC
        """
    )
    fun observeByOwner(ownerId: String): Flow<List<TaskSnapshotEntity>>

    @Query("SELECT * FROM task_snapshots WHERE ownerId = :ownerId AND taskId = :taskId LIMIT 1")
    suspend fun getByTaskId(ownerId: String, taskId: String): TaskSnapshotEntity?

    @Query("SELECT COUNT(*) FROM task_snapshots WHERE ownerId = :ownerId")
    suspend fun count(ownerId: String): Int

    @Query("SELECT COUNT(*) FROM task_snapshots")
    suspend fun countAll(): Int

    @Query("DELETE FROM task_snapshots WHERE ownerId = :ownerId AND taskId = :taskId")
    suspend fun deleteByTaskId(ownerId: String, taskId: String)

    @Query("DELETE FROM task_snapshots WHERE ownerId = :ownerId")
    suspend fun deleteByOwner(ownerId: String)

    @Query("DELETE FROM task_snapshots")
    suspend fun deleteAll()
}
