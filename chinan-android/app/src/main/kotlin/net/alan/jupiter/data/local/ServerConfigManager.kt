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
        /** 默认查寝服务器：登录页首次进入即用这套地址/端口，用户仍可在「服务器配置」里改。 */
        const val DEFAULT_HOST = "218.12.120.170"
        const val DEFAULT_PORT = "49749"

        private val KEY_HOST = stringPreferencesKey("server_host")
        private val KEY_PORT = stringPreferencesKey("server_port")

        /**
         * 服务器地址归一化。
         *
         * 中文输入法会把 `.` 打成全角 `。`（`，`、`．` 同理），按原样保存会让 TCP 连接
         * 必然失败且看不出原因。读写两侧统一归一，历史脏数据也能自愈。
         */
        fun normalizeHost(raw: String?): String = raw.orEmpty()
            .replace('。', '.')
            .replace('．', '.')
            .replace('，', ',')
            .trim()
    }

    /** 已保存的地址；没保存过（或存的是空白）就用默认服务器。 */
    suspend fun getHost(): String =
        normalizeHost(dataStore.data.first()[KEY_HOST]).ifBlank { DEFAULT_HOST }

    suspend fun getPort(): String =
        dataStore.data.first()[KEY_PORT]?.trim()?.ifBlank { DEFAULT_PORT } ?: DEFAULT_PORT

    suspend fun getBaseUrl(): String = "http://${getHost()}:${getPort()}/"

    suspend fun saveServerConfig(host: String, port: String) {
        dataStore.edit {
            it[KEY_HOST] = normalizeHost(host).ifBlank { DEFAULT_HOST }
            it[KEY_PORT] = port.trim().ifBlank { DEFAULT_PORT }
        }
    }

    /** 是否已保存过自定义配置（默认值不算）。 */
    suspend fun hasConfig(): Boolean = dataStore.data.first()[KEY_HOST].isNullOrBlank().not()
}
