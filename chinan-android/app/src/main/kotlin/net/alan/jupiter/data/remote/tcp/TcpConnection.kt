package net.alan.jupiter.data.remote.tcp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TcpConnection(
    private val configProvider: suspend () -> Pair<String, Int>
) {
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private val writeLock = Mutex()
    private val connectLock = Mutex()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<TcpProtocol.Frame>>()
    private var readerJob: kotlinx.coroutines.Job? = null
    private var scope: CoroutineScope? = null

    @Volatile
    private var connected = false

    @Volatile
    var onDisconnected: (() -> Unit)? = null

    suspend fun <T> request(
        type: String,
        payload: JsonObject?,
        decode: (JsonObject) -> T
    ): T = withContext(Dispatchers.IO) {
        val (host, port) = configProvider()
        ensureConnected(host, port)
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<TcpProtocol.Frame>()
        pending[requestId] = deferred

        try {
            writeLock.withLock {
                TcpProtocol.encode(output!!, TcpProtocol.Frame(
                    type = type, requestId = requestId, payload = payload
                ))
            }

            val frame = withTimeout(60_000) { deferred.await() }

            if (frame.code == 401) throw AuthExpiredException(
                frame.message.ifBlank { "密码已变更，请重新登录" }
            )
            if (frame.code != 200) throw IOException(
                frame.message.ifBlank { "服务器错误: ${frame.code}" }
            )
            decode(frame.payload ?: JsonObject(emptyMap()))
        } finally {
            pending.remove(requestId)
        }
    }

    private suspend fun ensureConnected(host: String, port: Int) {
        if (connected && socket != null && socket!!.isConnected && !socket!!.isClosed) return

        connectLock.withLock {
            if (connected && socket != null && socket!!.isConnected && !socket!!.isClosed) return

            disconnectUnderLock()

            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            if (host.isBlank()) throw IOException("未配置服务器地址")

            val newSocket = withContext(Dispatchers.IO) {
                Socket().apply {
                    tcpNoDelay = true
                    receiveBufferSize = 256 * 1024
                    sendBufferSize = 256 * 1024
                    connect(InetSocketAddress(host, port), 10_000)
                    soTimeout = 30_000
                }
            }

            socket = newSocket
            output = DataOutputStream(BufferedOutputStream(newSocket.getOutputStream()))
            input = DataInputStream(BufferedInputStream(newSocket.getInputStream()))
            connected = true

            startReader()
        }
    }

    private fun startReader() {
        readerJob = scope!!.launch(Dispatchers.IO) {
            while (isActive && connected) {
                try {
                    val frame = TcpProtocol.decode(input!!)
                    pending[frame.requestId]?.complete(frame)
                } catch (e: IOException) {
                    if (isActive) {
                        connected = false
                        onDisconnected?.invoke()
                    }
                    break
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    if (isActive && connected) {
                        connected = false
                        onDisconnected?.invoke()
                    }
                    break
                }
            }
        }
    }

    /**
     * 在持有 connectLock 时调用，直接清理资源。
     */
    private fun disconnectUnderLock() {
        connected = false
        readerJob?.cancel()
        readerJob = null
        scope?.cancel()
        scope = null

        pending.values.forEach { deferred ->
            deferred.completeExceptionally(IOException("连接已断开"))
        }
        pending.clear()

        try { input?.close() } catch (_: Exception) {}
        input = null
        try { output?.close() } catch (_: Exception) {}
        output = null
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    fun disconnect() {
        connected = false
        readerJob?.cancel()
        readerJob = null
        scope?.cancel()
        scope = null

        pending.values.forEach { deferred ->
            deferred.completeExceptionally(IOException("连接已断开"))
        }
        pending.clear()

        try { input?.close() } catch (_: Exception) {}
        input = null
        try { output?.close() } catch (_: Exception) {}
        output = null
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}