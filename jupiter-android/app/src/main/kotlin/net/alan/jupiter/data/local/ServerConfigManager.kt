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

    suspend fun getHost(): String = dataStore.data.first()[KEY_HOST] ?: ""
    suspend fun getPort(): String = dataStore.data.first()[KEY_PORT] ?: ""
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