package net.alan.jupiter.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerConfigManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val KEY_HOST = stringPreferencesKey("server_host")
        private val KEY_PORT = stringPreferencesKey("server_port")
    }

    // 默认值不写死任何公网地址；端口与后端 TCP 默认端口（Chinan.tcp.port，缺省 9090）保持一致
    suspend fun getHost(): String = dataStore.data.first()[KEY_HOST] ?: ""
    suspend fun getPort(): String = dataStore.data.first()[KEY_PORT] ?: "9090"
    suspend fun getBaseUrl(): String {
        val host = getHost()
        if (host.isBlank()) return ""
        return "http://$host:${getPort()}/"
    }
    suspend fun saveServerConfig(host: String, port: String) {
        dataStore.edit {
            it[KEY_HOST] = host
            it[KEY_PORT] = port
        }
    }
    suspend fun hasConfig(): Boolean = getHost().isNotBlank()
}