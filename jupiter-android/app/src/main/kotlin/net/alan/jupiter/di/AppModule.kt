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
import net.alan.jupiter.data.local.TokenManager
import net.alan.jupiter.data.local.ServerConfigManager
import net.alan.jupiter.data.local.AppDatabase
import net.alan.jupiter.data.local.PendingCheckDao
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jupiter_pe_prefs")

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
    fun providePendingCheckDao(appDatabase: AppDatabase): PendingCheckDao = appDatabase.pendingCheckDao()
}