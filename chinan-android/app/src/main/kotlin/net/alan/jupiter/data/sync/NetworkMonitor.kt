package net.alan.jupiter.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网络状态：既用于「现在要不要试一把」，也用于「网络回来了，先把断网期间的旧连接丢掉」。
 *
 * 为什么要后者：断网时 TCP socket 常常是半死的（客户端还以为连着，写进去没有回包），
 * 如果网络恢复后直接复用它，请求会一直等到超时，表现就是「联网了却一直加载」。
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "NetworkMonitor"
    }

    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    @Volatile
    private var callbackRegistered = false

    fun isOnline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** 网络从不可用变为可用时回调（用于丢弃半死的 TCP 连接）。 */
    fun observeAvailable(listener: () -> Unit) {
        listeners.add(listener)
        registerCallback()
    }

    private fun registerCallback() {
        if (callbackRegistered) return
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            manager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (!isOnline()) return
                    listeners.forEach { listener ->
                        runCatching { listener() }
                    }
                }
            })
            callbackRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "注册网络回调失败，网络恢复后需要等一次请求超时才重建连接", e)
        }
    }
}
