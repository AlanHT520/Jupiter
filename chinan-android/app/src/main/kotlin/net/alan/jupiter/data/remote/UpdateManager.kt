package net.alan.jupiter.data.remote

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import net.alan.jupiter.data.remote.dto.GiteeRelease
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var downloadId: Long = -1L
    private var latestDownloadUrl: String? = null

    fun checkForUpdate(
        onUpdateAvailable: (version: String, changelog: String) -> Unit,
        onNoUpdate: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        Thread {
            try {
                val url = java.net.URL(GITEE_API_LATEST)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/json")

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    onError("服务器响应异常 ($responseCode)")
                    return@Thread
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val release = json.decodeFromString<GiteeRelease>(body)
                connection.disconnect()

                val latestVersion = release.tag_name
                val currentVersion = getCurrentVersion()

                if (isNewer(latestVersion, currentVersion)) {
                    latestDownloadUrl = release.assets
                        .firstOrNull { it.browser_download_url.endsWith(".apk") }
                        ?.browser_download_url
                    val changelog = release.body.ifBlank { "无更新说明" }
                    onUpdateAvailable(latestVersion, changelog)
                } else {
                    onNoUpdate()
                }
            } catch (e: Exception) {
                onError(e.message ?: "检查更新失败")
            }
        }.start()
    }

    fun downloadApk(version: String) {
        val downloadUrl = latestDownloadUrl
            ?: "$GITEE_BASE_URL/download/$version/${apkFileName(version)}"

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Chinan 更新")
            .setDescription("正在下载 $version ...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")

        val apkFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            apkFileName(version)
        )
        if (apkFile.exists()) apkFile.delete()

        request.setDestinationUri(Uri.fromFile(apkFile))

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = manager.enqueue(request)
    }

    fun getInstallReceiver(): BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                installApk()
            }
        }
    }

    fun getReceiverFilter(): IntentFilter =
        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)

    private fun installApk() {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(query)

        if (cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val uri = cursor.getString(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                )
                cursor.close()
                triggerInstall(Uri.parse(uri))
            } else {
                cursor.close()
            }
        } else {
            cursor.close()
        }
    }

    private fun triggerInstall(apkUri: Uri) {
        val installUri = if (apkUri.scheme == "file") {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(apkUri.path!!)
            )
        } else {
            apkUri
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(installUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        context.startActivity(intent)
    }

    fun apkFileName(version: String): String {
        val safe = version.replace("/", "_").replace("\\", "_")
        return "Chinan-$safe.apk"
    }

    fun cleanupApkFiles() {
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
            dir.listFiles()?.filter { it.extension.equals("apk", ignoreCase = true) }
                ?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    private fun getCurrentVersion(): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    private fun isNewer(latestTag: String, currentTag: String): Boolean {
        val latest = parseVersion(latestTag) ?: return false
        val current = parseVersion(currentTag) ?: return false

        if (latest.major != current.major) return latest.major > current.major
        if (latest.minor != current.minor) return latest.minor > current.minor
        return latest.patch > current.patch
    }

    private fun parseVersion(tag: String): VersionInfo? {
        val cleaned = tag.lowercase()
            .removePrefix("v")
            .removePrefix("-")
            .removePrefix("release")
            .removePrefix("-")
            .trim()

        val parts = cleaned.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size < 3) return null
        return VersionInfo(parts[0], parts[1], parts[2])
    }

    private data class VersionInfo(val major: Int, val minor: Int, val patch: Int)

    companion object {
        private const val GITEE_BASE_URL = "https://gitee.com/AlanHT520/Chinan/releases"
        private const val GITEE_API_LATEST =
            "https://gitee.com/api/v5/repos/AlanHT520/Chinan/releases/latest"
    }
}