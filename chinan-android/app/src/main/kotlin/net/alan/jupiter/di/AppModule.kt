package net.alan.jupiter.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.alan.jupiter.data.local.AppDatabase
import net.alan.jupiter.data.local.ServerConfigManager
import net.alan.jupiter.data.local.TokenManager
import net.alan.jupiter.data.local.dao.CheckRecordDao
import net.alan.jupiter.data.local.dao.StudentSnapshotDao
import net.alan.jupiter.data.local.dao.SummaryDao
import net.alan.jupiter.data.local.dao.SyncOutboxDao
import net.alan.jupiter.data.local.dao.TaskSnapshotDao
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "Chinan_pe_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides
    @Singleton
    fun provideTokenManager(dataStore: DataStore<Preferences>): TokenManager = TokenManager(dataStore)

    @Provides
    @Singleton
    fun provideServerConfigManager(dataStore: DataStore<Preferences>): ServerConfigManager = ServerConfigManager(dataStore)

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase = AppDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideTaskSnapshotDao(appDatabase: AppDatabase): TaskSnapshotDao = appDatabase.taskSnapshotDao()

    @Provides
    @Singleton
    fun provideStudentSnapshotDao(appDatabase: AppDatabase): StudentSnapshotDao = appDatabase.studentSnapshotDao()

    @Provides
    @Singleton
    fun provideCheckRecordDao(appDatabase: AppDatabase): CheckRecordDao = appDatabase.checkRecordDao()

    @Provides
    @Singleton
    fun provideSyncOutboxDao(appDatabase: AppDatabase): SyncOutboxDao = appDatabase.syncOutboxDao()

    @Provides
    @Singleton
    fun provideSummaryDao(appDatabase: AppDatabase): SummaryDao = appDatabase.summaryDao()
}
