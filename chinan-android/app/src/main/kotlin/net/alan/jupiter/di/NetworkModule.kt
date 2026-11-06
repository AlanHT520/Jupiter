package net.alan.jupiter.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.alan.jupiter.data.local.ServerConfigManager
import net.alan.jupiter.data.remote.tcp.TcpClient
import net.alan.jupiter.data.sync.NetworkMonitor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideTcpClient(
        serverConfigManager: ServerConfigManager,
        networkMonitor: NetworkMonitor
    ): TcpClient {
        val client = TcpClient(serverConfigManager, networkMonitor)
        // 网络恢复时丢掉断网期间的半死连接，下一次请求才会真正重建，避免「联网后一直加载」
        networkMonitor.observeAvailable { client.disconnect() }
        return client
    }
}
