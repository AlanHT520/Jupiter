package net.alan.jupiter.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dagger.hilt.EntryPoints
import kotlinx.coroutines.launch
import net.alan.jupiter.di.UpdateManagerEntryPoint
import net.alan.jupiter.ui.components.ChinanCard
import net.alan.jupiter.ui.update.UpdateDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLogout: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    val context = LocalContext.current
    val entryPoint = remember {
        EntryPoints.get(
            context.applicationContext,
            UpdateManagerEntryPoint::class.java
        )
    }
    val updateManager = remember { entryPoint.updateManager() }

    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateVersion by remember { mutableStateOf("") }
    var updateChangelog by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text("设置", fontWeight = FontWeight.ExtraBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ChinanCard {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(14.dp)
                                .fillMaxSize(),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Chinan",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Kotlin Edition",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "宿舍查寝管理系统",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        InfoCard(
                            icon = Icons.Outlined.Info,
                            label = "版本",
                            value = "v-0.8.9",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        InfoCard(
                            icon = Icons.Outlined.Android,
                            label = "平台",
                            value = "Android",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        InfoCard(
                            icon = Icons.Outlined.Layers,
                            label = "构建",
                            value = "Release",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            ChinanCard {
                Column(modifier = Modifier.padding(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Icon(
                                Icons.Outlined.Copyright,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .fillMaxSize(),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "版权信息",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "本软件著作权归张浩天所有。未经作者事先书面授权，任何个人或组织不得使用、复制、修改或分发本软件，不得将其用于商业用途，亦不得将其作为任何公开展示的组成部分。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                    )
                }
            }

            ChinanCard {
                Column(modifier = Modifier.padding(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                        ) {
                            Icon(
                                Icons.Outlined.SystemUpdateAlt,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .fillMaxSize(),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "软件更新",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            checkingUpdate = true
                            updateManager.checkForUpdate(
                                onUpdateAvailable = { version, changelog ->
                                    checkingUpdate = false
                                    updateVersion = version
                                    updateChangelog = changelog
                                    showUpdateDialog = true
                                },
                                onNoUpdate = {
                                    checkingUpdate = false
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "当前已是最新版本",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                },
                                onError = { msg ->
                                    checkingUpdate = false
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "检查更新失败: $msg",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !checkingUpdate,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (checkingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("检查中...")
                        } else {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("检查更新")
                        }
                    }
                }
            }

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        ) {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .fillMaxSize(),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "账户操作",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Default.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("退出登录")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            shape = MaterialTheme.shapes.large,
            icon = {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxSize(),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            },
            title = {
                Text(
                    "确认退出",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "退出登录后需要重新输入用户名和密码才能使用",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("确认退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
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
                // 交给系统 DownloadManager 通知栏下载，立即恢复按钮，避免对话框卡死
                downloading = false
            },
            onDismiss = { showUpdateDialog = false }
        )
    }
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: androidx.compose.ui.graphics.Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = tint.copy(alpha = 0.1f)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxSize(),
                tint = tint
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}