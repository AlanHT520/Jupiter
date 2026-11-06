package net.alan.jupiter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.EntryPoints
import kotlinx.coroutines.launch
import net.alan.jupiter.di.UpdateManagerEntryPoint
import net.alan.jupiter.ui.components.QnAvatar
import net.alan.jupiter.ui.components.QnCard
import net.alan.jupiter.ui.components.QnChip
import net.alan.jupiter.ui.components.QnDangerTextButton
import net.alan.jupiter.ui.components.QnDivider
import net.alan.jupiter.ui.components.QnGroupLabel
import net.alan.jupiter.ui.components.QnKeyValue
import net.alan.jupiter.ui.components.QnOutlinedButton
import net.alan.jupiter.ui.components.QnPrimaryButton
import net.alan.jupiter.ui.components.QnScaffold
import net.alan.jupiter.ui.components.QnTextButton
import net.alan.jupiter.ui.components.QnTextField
import net.alan.jupiter.ui.components.QnTonalButton
import net.alan.jupiter.ui.components.maskPhone
import net.alan.jupiter.ui.components.roleLabel
import net.alan.jupiter.ui.theme.QnTone
import net.alan.jupiter.ui.update.UpdateDialog

/**
 * 设置（/settings）。
 *
 * 五个分组卡：账户 / 关于 / 检查更新 / 本地数据 / 账户操作。
 * 三个确认对话框都用 M3 默认 28dp 圆角；「确认清除」用 error 色，与「取消」明显区分。
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLogout: () -> Unit,
    onSyncClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    profileViewModel: net.alan.jupiter.ui.profile.ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val profile by profileViewModel.uiState.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    LaunchedEffect(uiState.passwordChanged) {
        if (uiState.passwordChanged) {
            showPasswordDialog = false
            onLogout()
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        viewModel.dismissMessage()
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
    }

    val context = LocalContext.current
    val updateManager = remember {
        EntryPoints.get(context.applicationContext, UpdateManagerEntryPoint::class.java)
            .updateManager()
    }

    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateVersion by remember { mutableStateOf("") }
    var updateChangelog by remember { mutableStateOf("") }
    var checkingUpdate by remember { mutableStateOf(false) }

    QnScaffold(
        title = "设置",
        onBack = onBackClick,
        snackbarHostState = snackbarHostState
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            // ── 账户 ──
            QnCard(
                onClick = onProfileClick,
                contentPadding = PaddingValues(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QnAvatar(
                        initial = profile.name.ifBlank { "?" },
                        size = 48.dp,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name.ifBlank { "未设置姓名" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = maskPhone(profile.phone),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    QnChip(text = roleLabel(profile.role), tone = QnTone.Success)
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "查看我的资料",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ── 关于 ──
            QnCard(contentPadding = PaddingValues(18.dp)) {
                QnGroupLabel("关于", modifier = Modifier.padding(top = 0.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QnAvatar(
                        initial = "寝",
                        size = 40.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "寝安 QIN'AN",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "宿舍查寝管理系统 · Kotlin Edition",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                QnDivider()
                QnKeyValue(label = "版本", value = "1.0.0", mono = true)
                QnKeyValue(label = "平台", value = "Android")
                QnKeyValue(label = "构建", value = "Release")
            }

            // ── 检查更新 ──
            QnCard(contentPadding = PaddingValues(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QnGroupLabel("检查更新", modifier = Modifier.weight(1f).padding(top = 0.dp))
                    if (checkingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = "APK 由系统下载器在通知栏完成下载与安装。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                QnOutlinedButton(
                    text = if (checkingUpdate) "检查中…" else "检查更新",
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
                                        message = "检查更新失败：$msg",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                    },
                    enabled = !checkingUpdate,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 本地数据 ──
            QnCard(contentPadding = PaddingValues(18.dp)) {
                QnGroupLabel("本地数据", modifier = Modifier.padding(top = 0.dp))
                Text(
                    text = "本地查寝记录永久保留，退出登录不会删除；只有这里可以清除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                QnDivider()
                QnKeyValue(
                    label = "记录",
                    value = "${uiState.records}（已上传 ${uiState.acked}）",
                    mono = true
                )
                QnKeyValue(
                    label = "待上传",
                    value = "${uiState.pending}",
                    mono = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QnOutlinedButton(
                        text = if (uiState.isClearing) "清除中…" else "清除本地数据",
                        onClick = { showClearDialog = true },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isClearing
                    )
                    QnTonalButton(
                        text = "同步与上传",
                        onClick = onSyncClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── 账户管理 ──
            QnCard(contentPadding = PaddingValues(18.dp)) {
                QnGroupLabel("账户管理", modifier = Modifier.padding(top = 0.dp))
                Text(
                    text = "修改密码会使当前登录失效；退出后需重新登录，本地记录保留。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                QnOutlinedButton(
                    text = "修改密码",
                    onClick = {
                        passwordError = null
                        showPasswordDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    QnTextButton(
                        text = "退出登录",
                        onClick = { showLogoutDialog = true },
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // ── 版权 ──
            QnCard(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentPadding = PaddingValues(18.dp)
            ) {
                QnGroupLabel("版权说明", modifier = Modifier.padding(top = 0.dp))
                Text(
                    text = "本软件著作权归张浩天所有，未经实现授权，不得将使用、分发、逆向工程、商业使用、公开展示或任何公开展示的组成部分。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("修改密码") },
            text = {
                Column {
                    Text(
                        text = "修改成功后当前登录会失效，需要重新登录；本地记录保留。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    QnTextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it },
                        label = "旧密码",
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    QnTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = "新密码（至少 4 位）",
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    QnTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = "确认新密码",
                        visualTransformation = PasswordVisualTransformation()
                    )
                    passwordError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                QnPrimaryButton(
                    text = if (uiState.isChangingPassword) "修改中…" else "确认修改",
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
                TextButton(onClick = { showPasswordDialog = false }) { Text("取消") }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("确认退出") },
            text = { Text("退出登录后需要重新输入手机号和密码；本机查寝记录不会被删除。") },
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
                TextButton(onClick = { showLogoutDialog = false }) { Text("取消") }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("清除本地数据") },
            text = {
                Text(
                    "将删除本机所有账号的查寝记录、待提交队列、缓存快照与证明照片，删除后不可恢复。"
                )
            },
            confirmButton = {
                QnDangerTextButton(
                    text = "确认清除",
                    onClick = {
                        showClearDialog = false
                        viewModel.clearLocalData()
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }

    if (showUpdateDialog) {
        UpdateDialog(
            version = updateVersion,
            changelog = updateChangelog,
            onDownload = {
                updateManager.downloadApk(updateVersion)
                showUpdateDialog = false
            },
            onDismiss = { showUpdateDialog = false }
        )
    }
}
