package net.alan.jupiter.data.remote.tcp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 裸 TCP 长连接（一帧一问一答）。
 *
 * 断网恢复后「一直加载」的三个成因，都在这层修掉：
 * 1. 请求超时曾用默认 60s，又被上层重试 3 次 → 最长 3 分钟白等；
 * 2. 超时抛的是 [TimeoutCancellationException]（CancellationException 的子类），
 *    上层的 `catch (IOException)` 抓不到，连接状态也不会被重置；
 * 3. 读线程遇到断网只把 `connected` 置 false，**不关闭 socket、不唤醒在途请求**，
 *    那个半死的 socket 在下一次请求里还能通过 `isConnected` 检查，于是继续往黑洞里写。
 *
 * 现在的约定：任何一次读失败或请求超时都调用 [markBroken] —— 立刻失败在途请求、
 * 关掉 socket、丢掉连接，下一次请求必然重新建链。
 */
class TcpConnection(
    private val configProvider: suspend () -> Pair<String, Int>
) {
    private companion object {
        /** 单次请求上限：查寝现场网络差时也要在十几秒内给出结果，而不是让用户盯着转圈。 */
        const val REQUEST_TIMEOUT_MS = 15_000L

        /** 建立连接上限。 */
        const val CONNECT_TIMEOUT_MS = 8_000

        const val DISCONNECTED_MESSAGE = "连接已断开，请重试"
    }

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

    /** 每次建链递增：用于判断「坏掉的到底是哪条连接」，避免误杀刚建好的新连接。 */
    @Volatile
    private var connectionId = 0L

    @Volatile
    var onDisconnected: (() -> Unit)? = null

    val isConnected: Boolean
        get() = connected

    suspend fun <T> request(
        type: String,
        payload: JsonObject?,
        decode: (JsonObject) -> T
    ): T = withContext(Dispatchers.IO) {
        val (host, port) = configProvider()
        ensureConnected(host, port)
        val myConnId = connectionId

        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<TcpProtocol.Frame>()
        pending[requestId] = deferred

        try {
            val stream = output ?: throw IOException(DISCONNECTED_MESSAGE)
            writeLock.withLock {
                TcpProtocol.encode(stream, TcpProtocol.Frame(
                    type = type, requestId = requestId, payload = payload
                ))
            }

            val frame = try {
                withTimeout(REQUEST_TIMEOUT_MS) { deferred.await() }
            } catch (e: TimeoutCancellationException) {
                // 超时基本等于「这条连接已经半死」：拆掉它，让下一次请求重建，别复用坏 socket
                markBroken(myConnId, "请求超时，请检查网络后重试")
                throw IOException("请求超时，请检查网络后重试")
            }

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

            if (host.isBlank()) throw IOException("未配置服务器地址")

            val newSocket = withContext(Dispatchers.IO) {
                Socket().apply {
                    tcpNoDelay = true
                    receiveBufferSize = 256 * 1024
                    sendBufferSize = 256 * 1024
                    connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                    // 读超时交给请求自身的 REQUEST_TIMEOUT_MS，socket 不再另设，避免空闲 30s 就被判死
                    soTimeout = 0
                }
            }

            socket = newSocket
            output = DataOutputStream(BufferedOutputStream(newSocket.getOutputStream()))
            input = DataInputStream(BufferedInputStream(newSocket.getInputStream()))
            connectionId++
            connected = true

            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            startReader(connectionId)
        }
    }

    private fun startReader(connId: Long) {
        val currentScope = scope ?: return
        readerJob = currentScope.launch(Dispatchers.IO) {
            while (isActive) {
                val frame = try {
                    TcpProtocol.decode(input!!)
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    // 断网 / 服务端关闭 / 读失败：连接已不可用，唤醒在途请求并丢弃 socket
                    markBroken(connId, DISCONNECTED_MESSAGE)
                    onDisconnected?.invoke()
                    break
                }
                pending[frame.requestId]?.complete(frame)
            }
        }
    }

    /**
     * 连接不可用：立刻让在途请求失败、关闭 socket、清空连接状态。
     * 只有「坏掉的正是当前这条连接」才动手，避免误杀刚重建的新连接。
     */
    private fun markBroken(connId: Long, reason: String) {
        synchronized(this) {
            if (connId != connectionId) return
            connected = false
            val snapshot = pending.values.toList()
            pending.clear()
            snapshot.forEach { deferred ->
                deferred.completeExceptionally(IOException(reason))
            }
            readerJob?.cancel()
            readerJob = null
            scope?.cancel()
            scope = null
            closeQuietly()
        }
    }

    /** 在持有 connectLock 时调用，直接清理资源。 */
    private fun disconnectUnderLock() {
        connected = false
        connectionId++          // 旧连接作废：它的读线程即使报错也不能再影响新连接
        readerJob?.cancel()
        readerJob = null
        scope?.cancel()
        scope = null

        val snapshot = pending.values.toList()
        pending.clear()
        snapshot.forEach { deferred ->
            deferred.completeExceptionally(IOException(DISCONNECTED_MESSAGE))
        }

        closeQuietly()
    }

    private fun closeQuietly() {
        try { input?.close() } catch (_: Exception) {}
        input = null
        try { output?.close() } catch (_: Exception) {}
        output = null
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    /** 主动断开（退出登录 / 网络恢复时丢弃断网期间的旧连接）。 */
    fun disconnect() {
        connected = false
        connectionId++
        readerJob?.cancel()
        readerJob = null
        scope?.cancel()
        scope = null

        val snapshot = pending.values.toList()
        pending.clear()
        snapshot.forEach { deferred ->
            deferred.completeExceptionally(IOException(DISCONNECTED_MESSAGE))
        }

        closeQuietly()
    }
}
