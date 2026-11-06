package net.alan.jupiter

import android.content.BroadcastReceiver
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import net.alan.jupiter.data.local.TokenManager
import net.alan.jupiter.data.remote.UpdateManager
import net.alan.jupiter.ui.navigation.AppNavGraph
import net.alan.jupiter.ui.navigation.Screen
import net.alan.jupiter.ui.theme.ChinanTheme
import net.alan.jupiter.ui.update.UpdateDialog
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var updateManager: UpdateManager

    private var installReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updateManager.cleanupApkFiles()

        installReceiver = updateManager.getInstallReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                installReceiver,
                updateManager.getReceiverFilter(),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(installReceiver, updateManager.getReceiverFilter())
        }

        enableEdgeToEdge()
        setContent {
            // MD3：跟随系统浅/深色，Android 12+ 可开动态取色（默认关，保持品牌色稳定）
            ChinanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 系统栏 insets 由各屏 Scaffold 统一处理，这里不再叠加，避免双重留白
                    Box(modifier = Modifier.fillMaxSize()) {
                        var startDestination by remember { mutableStateOf<String?>(null) }

                        LaunchedEffect(Unit) {
                            val hasToken = tokenManager.hasToken()
                            startDestination = if (hasToken) {
                                Screen.TaskList.route
                            } else {
                                Screen.Login.route
                            }
                        }

                        val dest = startDestination
                        if (dest != null) {
                            AppNavGraph(startDestination = dest)
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp
                                )
                            }
                        }

                        CheckUpdateSection()
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun CheckUpdateSection() {
        var showUpdateDialog by remember { mutableStateOf(false) }
        var updateVersion by remember { mutableStateOf("") }
        var updateChangelog by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            updateManager.checkForUpdate(
                onUpdateAvailable = { version, changelog ->
                    updateVersion = version
                    updateChangelog = changelog
                    showUpdateDialog = true
                }
            )
        }

        if (showUpdateDialog) {
            UpdateDialog(
                version = updateVersion,
                changelog = updateChangelog,
                // 下载交给系统 DownloadManager，在通知栏完成；对话框随即关闭，避免重复触发
                onDownload = {
                    updateManager.downloadApk(updateVersion)
                    showUpdateDialog = false
                },
                onDismiss = { showUpdateDialog = false }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        installReceiver?.let { unregisterReceiver(it) }
    }
}
