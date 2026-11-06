package net.alan.jupiter.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.alan.jupiter.data.local.dao.CheckRecordDao
import net.alan.jupiter.data.local.dao.StudentSnapshotDao
import net.alan.jupiter.data.local.dao.SummaryDao
import net.alan.jupiter.data.local.dao.SyncOutboxDao
import net.alan.jupiter.data.local.dao.TaskSnapshotDao
import net.alan.jupiter.data.local.entity.CheckRecordLocalEntity
import net.alan.jupiter.data.local.entity.StudentSnapshotEntity
import net.alan.jupiter.data.local.entity.SummarySnapshotEntity
import net.alan.jupiter.data.local.entity.SyncOutboxEntity
import net.alan.jupiter.data.local.entity.TaskSnapshotEntity

/**
 * 本地唯一事实来源（local-first）。
 *
 * 版本历史：
 * - v1：pending_checks（离线草稿）
 * - v2：pending_checks + proofFilePath + updatedAt
 * - v3：任务/学生快照 + 查寝记录 + 上行队列 + 汇总快照；
 *       旧的 pending_checks 数据迁移为 `LOCAL_DRAFT` 记录，**不做 destructive 回退**。
 *
 * 说明：旧库没有开启 `fallbackToDestructiveMigration`，所以这里只提供显式 Migration，
 * 保证升级不丢数据；`pending_checks` 里的草稿不会被自动上行（避免把没点提交的编辑
 * 静默发布到服务端），而是在当前账号首次登录后归属到该账号，用户可自行补交。
 */
@Database(
    entities = [
        TaskSnapshotEntity::class,
        StudentSnapshotEntity::class,
        CheckRecordLocalEntity::class,
        SyncOutboxEntity::class,
        SummarySnapshotEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskSnapshotDao(): TaskSnapshotDao

    abstract fun studentSnapshotDao(): StudentSnapshotDao

    abstract fun checkRecordDao(): CheckRecordDao

    abstract fun syncOutboxDao(): SyncOutboxDao

    abstract fun summaryDao(): SummaryDao

    companion object {
        private const val DB_NAME = "Chinan_pe_cache.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE pending_checks ADD COLUMN proofFilePath TEXT DEFAULT NULL"
                )
                database.execSQL(
                    "ALTER TABLE pending_checks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                CREATE_V3_TABLES.forEach { sql -> database.execSQL(sql) }

                // 旧草稿 → 本地查寝记录（LOCAL_DRAFT，不自动上行）
                database.execSQL(
                    """
                    INSERT OR REPLACE INTO check_records_local (
                        ownerId, taskId, roomId, studentId, taskDate, statusCode, remark,
                        proofPath, proofFilePath, syncState, clientOpId, late,
                        updatedAt, submittedAt, ackedAt, lastError
                    )
                    SELECT '', taskId, roomId, studentId, NULL, checkStatusCode, remark,
                           proofLocalPath, proofFilePath, 'LOCAL_DRAFT', NULL, 0,
                           CASE WHEN updatedAt > 0 THEN updatedAt ELSE createdAt END,
                           NULL, NULL, NULL
                    FROM pending_checks
                    """.trimIndent()
                )

                database.execSQL("DROP TABLE IF EXISTS pending_checks")
            }
        }

        /**
         * v3 建表语句。字段顺序/可空性/主键顺序与实体声明保持一致，
         * Room 打开数据库时会用 TableInfo 校验，务必不要随手改。
         */
        private val CREATE_V3_TABLES = listOf(
            """
            CREATE TABLE IF NOT EXISTS `task_snapshots` (
                `ownerId` TEXT NOT NULL,
                `taskId` TEXT NOT NULL,
                `taskNo` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `buildingName` TEXT NOT NULL,
                `floorNumber` TEXT NOT NULL,
                `taskDate` TEXT,
                `deadlineTime` TEXT,
                `status` TEXT NOT NULL,
                `totalRooms` INTEGER NOT NULL,
                `checkedRooms` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`ownerId`, `taskId`)
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS `index_task_snapshots_owner_date` " +
                "ON `task_snapshots` (`ownerId`, `taskDate`)",
            """
            CREATE TABLE IF NOT EXISTS `student_snapshots` (
                `ownerId` TEXT NOT NULL,
                `taskId` TEXT NOT NULL,
                `studentId` TEXT NOT NULL,
                `studentNo` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `gender` TEXT NOT NULL,
                `className` TEXT,
                `roomId` TEXT NOT NULL,
                `roomNumber` TEXT NOT NULL,
                `bedNumber` TEXT NOT NULL,
                `presetStatusCode` INTEGER,
                `statusCode` INTEGER,
                `statusSource` TEXT,
                `remark` TEXT,
                `proofFilePath` TEXT,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`ownerId`, `taskId`, `studentId`)
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS `index_student_snapshots_owner_task` " +
                "ON `student_snapshots` (`ownerId`, `taskId`)",
            "CREATE INDEX IF NOT EXISTS `index_student_snapshots_owner_room` " +
                "ON `student_snapshots` (`ownerId`, `roomId`)",
            """
            CREATE TABLE IF NOT EXISTS `check_records_local` (
                `ownerId` TEXT NOT NULL,
                `taskId` TEXT NOT NULL,
                `roomId` TEXT NOT NULL,
                `studentId` TEXT NOT NULL,
                `taskDate` TEXT,
                `statusCode` INTEGER NOT NULL,
                `remark` TEXT,
                `proofPath` TEXT,
                `proofFilePath` TEXT,
                `syncState` TEXT NOT NULL,
                `clientOpId` TEXT,
                `late` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `submittedAt` INTEGER,
                `ackedAt` INTEGER,
                `lastError` TEXT,
                PRIMARY KEY(`ownerId`, `taskId`, `roomId`, `studentId`)
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS `index_check_records_owner_state` " +
                "ON `check_records_local` (`ownerId`, `syncState`)",
            "CREATE INDEX IF NOT EXISTS `index_check_records_owner_op` " +
                "ON `check_records_local` (`ownerId`, `clientOpId`)",
            "CREATE INDEX IF NOT EXISTS `index_check_records_owner_date` " +
                "ON `check_records_local` (`ownerId`, `taskDate`)",
            """
            CREATE TABLE IF NOT EXISTS `sync_outbox` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `clientOpId` TEXT NOT NULL,
                `ownerId` TEXT NOT NULL,
                `taskId` TEXT NOT NULL,
                `roomId` TEXT NOT NULL,
                `studentId` TEXT,
                `taskDate` TEXT,
                `payload` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `retryCount` INTEGER NOT NULL,
                `lastError` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent(),
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_op` " +
                "ON `sync_outbox` (`clientOpId`)",
            "CREATE INDEX IF NOT EXISTS `index_sync_outbox_owner_state` " +
                "ON `sync_outbox` (`ownerId`, `state`)",
            "CREATE INDEX IF NOT EXISTS `index_sync_outbox_owner_date` " +
                "ON `sync_outbox` (`ownerId`, `taskDate`)",
            """
            CREATE TABLE IF NOT EXISTS `summary_snapshots` (
                `ownerId` TEXT NOT NULL,
                `scope` TEXT NOT NULL,
                `asOf` INTEGER NOT NULL,
                `countsJson` TEXT NOT NULL,
                `itemsJson` TEXT,
                `pendingCount` INTEGER NOT NULL,
                `taskCount` INTEGER NOT NULL,
                `studentCount` INTEGER NOT NULL,
                PRIMARY KEY(`ownerId`, `scope`)
            )
            """.trimIndent()
        )

        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { INSTANCE = it }
        }
    }
}
