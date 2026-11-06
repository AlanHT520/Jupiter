package net.alan.jupiter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.alan.jupiter.data.local.entity.StudentSnapshotEntity

@Dao
interface StudentSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<StudentSnapshotEntity>)

    @Query(
        """
        SELECT * FROM student_snapshots
        WHERE ownerId = :ownerId AND taskId = :taskId
        ORDER BY roomNumber ASC, bedNumber ASC
        """
    )
    fun observeByTask(ownerId: String, taskId: String): Flow<List<StudentSnapshotEntity>>

    @Query("SELECT * FROM student_snapshots WHERE ownerId = :ownerId AND taskId = :taskId")
    suspend fun getByTask(ownerId: String, taskId: String): List<StudentSnapshotEntity>

    @Query(
        """
        SELECT * FROM student_snapshots
        WHERE ownerId = :ownerId AND taskId = :taskId AND roomId = :roomId
        ORDER BY bedNumber ASC
        """
    )
    suspend fun getByRoom(ownerId: String, taskId: String, roomId: String): List<StudentSnapshotEntity>

    @Query(
        """
        SELECT * FROM student_snapshots
        WHERE ownerId = :ownerId AND taskId = :taskId AND roomId = :roomId
        ORDER BY bedNumber ASC
        """
    )
    fun observeByRoom(ownerId: String, taskId: String, roomId: String): Flow<List<StudentSnapshotEntity>>

    @Query("SELECT COUNT(*) FROM student_snapshots WHERE ownerId = :ownerId")
    suspend fun count(ownerId: String): Int

    @Query("SELECT COUNT(*) FROM student_snapshots")
    suspend fun countAll(): Int

    @Query("DELETE FROM student_snapshots WHERE ownerId = :ownerId AND taskId = :taskId")
    suspend fun deleteByTask(ownerId: String, taskId: String)

    @Query("DELETE FROM student_snapshots WHERE ownerId = :ownerId")
    suspend fun deleteByOwner(ownerId: String)

    @Query("DELETE FROM student_snapshots")
    suspend fun deleteAll()
}
