package net.alan.jupiter.ui.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import net.alan.jupiter.ui.components.QnCard
import net.alan.jupiter.ui.components.QnGroupLabel
import net.alan.jupiter.ui.components.QnNoticeCard
import net.alan.jupiter.ui.components.QnPrimaryButton
import net.alan.jupiter.ui.components.QnScaffold
import net.alan.jupiter.ui.components.QnTextField
import net.alan.jupiter.ui.theme.QnTone

/**
 * 申请加入（/register）。
 *
 * 三个中文分组：身份信息 / 密码 / 服务器；全部为 OutlinedTextField（8dp 圆角）。
 * 提交成功后先给 Snackbar，再回登录页。
 */
@Composable
fun RegisterScreen(
    onBackClick: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            snackbarHostState.showSnackbar(
                message = uiState.successMessage ?: "申请已提交",
                duration = SnackbarDuration.Short
            )
            delay(900)
            onSuccess()
        }
    }

    QnScaffold(
        title = "申请加入",
        onBack = onBackClick,
        snackbarHostState = snackbarHostState
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
            QnNoticeCard(
                title = "提交注册申请",
                text = "审核通过后即可用手机号登录使用",
                tone = QnTone.Warning
            )

            Spacer(modifier = Modifier.height(12.dp))

            QnCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
                // ── 身份信息 ──
                QnGroupLabel("身份信息")

                QnTextField(
                    value = uiState.name,
                    onValueChange = viewModel::updateName,
                    label = "姓名 *",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                QnTextField(
                    value = uiState.gender,
                    onValueChange = viewModel::updateGender,
                    label = "性别",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                QnTextField(
                    value = uiState.phone,
                    onValueChange = viewModel::updatePhone,
                    label = "手机号 *（用于登录）",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                QnTextField(
                    value = uiState.department,
                    onValueChange = viewModel::updateDepartment,
                    label = "部门 *",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                QnTextField(
                    value = uiState.className,
                    onValueChange = viewModel::updateClassName,
                    label = "班级 *",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                // ── 密码 ──
                QnGroupLabel("密码")

                QnTextField(
                    value = uiState.password,
                    onValueChange = viewModel::updatePassword,
                    label = "密码 *",
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                QnTextField(
                    value = uiState.confirmPassword,
                    onValueChange = viewModel::updateConfirmPassword,
                    label = "确认密码 *",
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (confirmPasswordVisible) "隐藏密码" else "显示密码",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    visualTransformation = if (confirmPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.submit() })
                )

                // ── 服务器 ──
                QnGroupLabel("服务器")

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QnTextField(
                        value = uiState.host,
                        onValueChange = viewModel::updateHost,
                        label = "服务器地址 *",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Right) }
                        ),
                        modifier = Modifier.weight(2f)
                    )
                    QnTextField(
                        value = uiState.port,
                        onValueChange = viewModel::updatePort,
                        label = "端口 *",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    QnNoticeCard(
                        text = uiState.error.orEmpty(),
                        tone = QnTone.Danger,
                        icon = Icons.Outlined.ErrorOutline
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                QnPrimaryButton(
                    text = if (uiState.isLoading) "提交中…" else "提交申请",
                    onClick = { viewModel.submit() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "注册开关由管理员在系统配置中控制；提交后需等待审核。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
            }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
