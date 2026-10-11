package net.alan.jupiter.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.alan.jupiter.data.local.entity.PendingCheckEntity

@Database(entities = [PendingCheckEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingCheckDao(): PendingCheckDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "jupiter_pe_cache.db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}