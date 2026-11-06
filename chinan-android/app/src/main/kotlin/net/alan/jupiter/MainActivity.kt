    package net.alan.jupiter

import android.content.BroadcastReceiver
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
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
import net.alan.jupiter.ui.theme.QnAccent
import net.alan.jupiter.ui.theme.QnBg
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
            // 暗色唯一主题：显式关闭跟随系统浅色
            ChinanTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    color = QnBg
                ) {
                    var startDestination by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        val hasToken = tokenManager.hasToken()
                        startDestination = if (hasToken) Screen.TaskList.route else Screen.Login.route
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
                                color = QnAccent,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    CheckUpdateSection()
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun CheckUpdateSection() {
        var showUpdateDialog by remember { mutableStateOf(false) }
        var updateVersion by remember { mutableStateOf("") }
        var updateChangelog by remember { mutableStateOf("") }
        var downloading by remember { mutableStateOf(false) }

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
                downloading = downloading,
                onDownload = {
                    downloading = true
                    updateManager.downloadApk(updateVersion)
                    // 下载交由系统 DownloadManager 在通知栏进行，这里恢复按钮，避免对话框永久卡住
                    downloading = false
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