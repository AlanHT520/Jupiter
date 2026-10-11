package net.alan.jupiter.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.alan.jupiter.data.local.ServerConfigManager
import net.alan.jupiter.data.remote.tcp.TcpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideTcpClient(serverConfigManager: ServerConfigManager): TcpClient =
        TcpClient(serverConfigManager)
}