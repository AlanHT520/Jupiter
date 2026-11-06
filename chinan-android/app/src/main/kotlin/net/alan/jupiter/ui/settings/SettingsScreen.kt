package net.alan.jupiter.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.EntryPoints
import kotlinx.coroutines.launch
import net.alan.jupiter.di.UpdateManagerEntryPoint
import net.alan.jupiter.ui.components.QnCard
import net.alan.jupiter.ui.components.QnGhostButton
import net.alan.jupiter.ui.components.QnKeyValue
import net.alan.jupiter.ui.components.QnMono
import net.alan.jupiter.ui.components.QnPrimaryButton
import net.alan.jupiter.ui.components.QnRadius
import net.alan.jupiter.ui.components.QnScaffold
import net.alan.jupiter.ui.components.QnSectionLabel
import net.alan.jupiter.ui.components.QnStatus
import net.alan.jupiter.ui.update.UpdateDialog
import net.alan.jupiter.ui.theme.QnAccent
import net.alan.jupiter.ui.theme.QnDanger
import net.alan.jupiter.ui.theme.QnLine
import net.alan.jupiter.ui.theme.QnPanel
import net.alan.jupiter.ui.theme.QnPanelAlt
import net.alan.jupiter.ui.theme.QnText
import net.alan.jupiter.ui.theme.QnTextDim
import net.alan.jupiter.ui.theme.QnTextMuted

/**
 * 设置（/settings）：QnCard + 等宽条目；保留更新检查、退出登录等全部逻辑。
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLogout: () -> Unit,
    onSyncClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.passwordChanged) {
        if (uiState.passwordChanged) {
            showPasswordDialog = false
            onLogout()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        viewModel.dismissMessage()
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short
        )
    }

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

    QnScaffold(
        path = "/settings",
        status = if (checkingUpdate) QnStatus.RUNNING else QnStatus.OK,
        onBack = onBackClick
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QnCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(QnPanelAlt, QnRadius)
                                .border(1.dp, QnLine, QnRadius),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = QnAccent
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "寝安 QIN'AN",
                                color = QnText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "KOTLIN EDITION · 宿舍查寝",
                                color = QnAccent,
                                fontFamily = QnMono,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = QnLine, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    QnKeyValue(label = "VERSION", value = "1.0.0")
                    QnKeyValue(label = "PLATFORM", value = "Android")
                    QnKeyValue(label = "BUILD", value = "Release")
                }

                QnCard {
                    QnSectionLabel("LICENSE")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "本软件著作权归张浩天所有。未经作者事先书面授权，任何个人或组织不得使用、复制、修改或分发本软件，不得将其用于商业用途，亦不得将其作为任何公开展示的组成部分。",
                        color = QnTextMuted,
                        fontSize = 11.sp,
                        lineHeight = 17.sp
                    )
                }

                QnCard {
                    QnSectionLabel("UPDATE")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "从服务器检查最新版本",
                                color = QnTextMuted,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "APK · DOWNLOAD MANAGER",
                                color = QnTextDim,
                                fontFamily = QnMono,
                                fontSize = 9.5.sp
                            )
                        }
                        if (checkingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = QnAccent
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    QnPrimaryButton(
                        text = if (checkingUpdate) "检查中..." else "检查更新",
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
                        enabled = !checkingUpdate
                    )
                }

                QnCard {
                    QnSectionLabel("LOCAL DATA")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "本地查寝记录永久保留，退出登录不会删除；只有这里可以清除。",
                        color = QnTextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = QnLine, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    QnKeyValue(
                        label = "RECORDS",
                        value = "${uiState.records}（ACKED ${uiState.acked}）"
                    )
                    QnKeyValue(
                        label = "OUTBOX",
                        value = "${uiState.outbox}（待同步 ${uiState.pending}）"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QnGhostButton(
                            text = if (uiState.isClearing) "清除中..." else "清除本地数据",
                            onClick = { showClearDialog = true },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isClearing
                        )
                        QnGhostButton(
                            text = "缓存与同步",
                            onClick = onSyncClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                QnCard {
                    QnSectionLabel("ACCOUNT")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "退出登录后需要重新输入用户名和密码才能使用",
                        color = QnTextMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    QnGhostButton(
                        text = "修改密码",
                        onClick = {
                            passwordError = null
                            showPasswordDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    QnGhostButton(
                        text = "退出登录",
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = QnPanel,
                    contentColor = QnText,
                    shape = QnRadius,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            containerColor = QnPanel,
            shape = QnRadius,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "修改密码",
                    color = QnText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "修改成功后 token 会失效，需要重新登录；本地记录保留。",
                        color = QnTextMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it },
                        label = { Text("旧密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = QnRadius,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("新密码（至少 4 位）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = QnRadius,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("确认新密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = QnRadius,
                        modifier = Modifier.fillMaxWidth()
                    )
                    passwordError?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = it, color = QnDanger, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                QnPrimaryButton(
                    text = if (uiState.isChangingPassword) "修改中..." else "确认修改",
                    enabled = !uiState.isChangingPassword,
                    onClick = {
                        passwordError = when {
                            oldPassword.isBlank() -> "请输入旧密码"
                            newPassword.length < 4 -> "新密码至少 4 位"
                            newPassword != confirmPassword -> "两次输入的新密码不一致"
                            else -> null
                        }
                        if (passwordError == null) {
                            viewModel.changePassword(oldPassword, newPassword)
                        }
                    }
                )
            },
            dismissButton = {
                QnGhostButton(
                    text = "取消",
                    onClick = { showPasswordDialog = false }
                )
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = QnPanel,
            shape = QnRadius,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "确认退出",
                    color = QnText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "退出登录后需要重新输入用户名和密码才能使用",
                    color = QnTextMuted,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                QnPrimaryButton(
                    text = "确认退出",
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                )
            },
            dismissButton = {
                QnGhostButton(
                    text = "取消",
                    onClick = { showLogoutDialog = false }
                )
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = QnPanel,
            shape = QnRadius,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "清除本地数据",
                    color = QnText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "将删除本机所有账号的查寝记录、待提交队列、缓存快照与证明照片，删除后不可恢复。确定继续？",
                    color = QnTextMuted,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                QnPrimaryButton(
                    text = "确认清除",
                    onClick = {
                        showClearDialog = false
                        viewModel.clearLocalData()
                    }
                )
            },
            dismissButton = {
                QnGhostButton(
                    text = "取消",
                    onClick = { showClearDialog = false }
                )
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
