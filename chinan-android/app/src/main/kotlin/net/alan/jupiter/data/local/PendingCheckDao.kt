package net.alan.jupiter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.alan.jupiter.data.local.entity.PendingCheckEntity

@Dao
interface PendingCheckDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingCheckEntity)

    @Query("SELECT * FROM pending_checks WHERE taskId = :taskId AND roomId = :roomId")
    suspend fun getByTaskAndRoom(taskId: String, roomId: String): List<PendingCheckEntity>

    @Query("SELECT * FROM pending_checks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PendingCheckEntity>>

    @Query("SELECT COUNT(*) FROM pending_checks")
    suspend fun count(): Int

    @Query("DELETE FROM pending_checks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM pending_checks WHERE taskId = :taskId AND roomId = :roomId AND studentId = :studentId")
    suspend fun deleteByTaskRoomAndStudent(taskId: String, roomId: String, studentId: String)

    @Query("DELETE FROM pending_checks WHERE taskId = :taskId AND roomId = :roomId")
    suspend fun deleteByTaskAndRoom(taskId: String, roomId: String)

    @Query("DELETE FROM pending_checks")
    suspend fun deleteAll()
}