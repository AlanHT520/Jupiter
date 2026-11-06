package net.alan.jupiter.ui.register

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import net.alan.jupiter.ui.components.QnMono
import net.alan.jupiter.ui.components.QnPrimaryButton
import net.alan.jupiter.ui.components.QnRadius
import net.alan.jupiter.ui.components.QnScaffold
import net.alan.jupiter.ui.components.QnSectionLabel
import net.alan.jupiter.ui.components.QnStatus
import net.alan.jupiter.ui.theme.QnAccent
import net.alan.jupiter.ui.theme.QnDanger
import net.alan.jupiter.ui.theme.QnLine
import net.alan.jupiter.ui.theme.QnPanel
import net.alan.jupiter.ui.theme.QnText
import net.alan.jupiter.ui.theme.QnTextDim
import net.alan.jupiter.ui.theme.QnTextMuted

/**
 * 注册申请（/register）：2dp 细线输入控件 + 等宽标签；业务逻辑与 ViewModel 不变。
 */
@Composable
fun RegisterScreen(
    onBackClick: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            onSuccess()
        }
    }

    QnScaffold(
        path = "/register",
        status = when {
            uiState.isLoading -> QnStatus.RUNNING
            uiState.error != null -> QnStatus.FAILED
            else -> QnStatus.OK
        },
        onBack = onBackClick
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Text(
                text = "提交注册申请",
                color = QnText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "// 审核通过后即可登录使用",
                color = QnTextDim,
                fontFamily = QnMono,
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
            QnSectionLabel("IDENTITY")
            Spacer(modifier = Modifier.height(8.dp))

            QnField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                label = "姓名 *",
                icon = Icons.Default.Person,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
            )
            Spacer(modifier = Modifier.height(10.dp))
            QnField(
                value = uiState.gender,
                onValueChange = viewModel::updateGender,
                label = "性别",
                icon = Icons.Default.Badge,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
            )
            Spacer(modifier = Modifier.height(10.dp))
            QnField(
                value = uiState.phone,
                onValueChange = viewModel::updatePhone,
                label = "手机号 *（用于登录）",
                icon = Icons.Default.Call,
                keyboardType = KeyboardType.Phone,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
            )
            Spacer(modifier = Modifier.height(10.dp))
            QnField(
                value = uiState.department,
                onValueChange = viewModel::updateDepartment,
                label = "部门 *",
                icon = Icons.Default.Groups,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
            )
            Spacer(modifier = Modifier.height(10.dp))
            QnField(
                value = uiState.className,
                onValueChange = viewModel::updateClassName,
                label = "班级 *",
                icon = Icons.Default.Groups,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
            )

            Spacer(modifier = Modifier.height(18.dp))
            QnSectionLabel("PASSWORD")
            Spacer(modifier = Modifier.height(8.dp))

            QnField(
                value = uiState.password,
                onValueChange = viewModel::updatePassword,
                label = "密码 *",
                icon = Icons.Default.Lock,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            tint = QnTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            QnField(
                value = uiState.confirmPassword,
                onValueChange = viewModel::updateConfirmPassword,
                label = "确认密码 *",
                icon = Icons.Default.LockReset,
                imeAction = ImeAction.Done,
                visualTransformation = if (confirmPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                onImeAction = { viewModel.submit() },
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (confirmPasswordVisible) "隐藏密码" else "显示密码",
                            tint = QnTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(18.dp))
            QnSectionLabel("SERVER")
            Spacer(modifier = Modifier.height(8.dp))

            QnField(
                value = uiState.host,
                onValueChange = viewModel::updateHost,
                label = "服务器地址 *",
                icon = Icons.Default.Dns,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
            )
            Spacer(modifier = Modifier.height(10.dp))
            QnField(
                value = uiState.port,
                onValueChange = viewModel::updatePort,
                label = "端口 *",
                icon = Icons.Default.Tag,
                keyboardType = KeyboardType.Number,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
            )

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, QnDanger.copy(alpha = 0.55f), QnRadius)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = QnDanger,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.error ?: "",
                        color = QnDanger,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            QnPrimaryButton(
                text = if (uiState.isLoading) "提交中..." else "提交申请",
                onClick = { viewModel.submit() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/** 统一风格的表单控件：1dp 细线 + 2dp 圆角 + 等宽输入。 */
@Composable
private fun QnField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                fontFamily = QnMono,
                fontSize = 10.5.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = QnTextDim,
                modifier = Modifier.size(16.dp)
            )
        },
        trailingIcon = trailingIcon,
        singleLine = true,
        shape = QnRadius,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = QnText,
            fontFamily = QnMono,
            fontSize = 12.sp
        ),
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() }
        ),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = QnAccent,
            unfocusedBorderColor = QnLine,
            focusedLabelColor = QnAccent,
            unfocusedLabelColor = QnTextMuted,
            focusedTextColor = QnText,
            unfocusedTextColor = QnText,
            cursorColor = QnAccent,
            focusedContainerColor = QnPanel,
            unfocusedContainerColor = QnPanel,
            focusedLeadingIconColor = QnAccent,
            unfocusedLeadingIconColor = QnTextDim,
            focusedTrailingIconColor = QnAccent,
            unfocusedTrailingIconColor = QnTextMuted
        )
    )
}
