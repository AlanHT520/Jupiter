package net.alan.jupiter.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import net.alan.jupiter.ui.components.QnCard
import net.alan.jupiter.ui.components.QnKeyValue
import net.alan.jupiter.ui.components.QnMono
import net.alan.jupiter.ui.components.QnRadius
import net.alan.jupiter.ui.components.QnScaffold
import net.alan.jupiter.ui.components.QnSectionLabel
import net.alan.jupiter.ui.components.QnStatus
import net.alan.jupiter.ui.components.QnTag
import net.alan.jupiter.ui.theme.QnAccent
import net.alan.jupiter.ui.theme.QnLine
import net.alan.jupiter.ui.theme.QnPanelAlt
import net.alan.jupiter.ui.theme.QnText
import net.alan.jupiter.ui.theme.QnTextDim
import net.alan.jupiter.ui.theme.QnTextMuted

/**
 * 账户信息（/me）：头像卡 + 等宽 QnKeyValue 列表。
 */
@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val roleLabel = when (uiState.role.uppercase()) {
        "ADMIN" -> "管理员"
        "" -> "查宿员"
        else -> uiState.role
    }

    QnScaffold(
        path = "/me",
        status = if (uiState.isLoading) QnStatus.RUNNING else QnStatus.OK,
        onBack = onBackClick
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(34.dp),
                    strokeWidth = 2.dp,
                    color = QnAccent
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QnCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .background(QnPanelAlt, QnRadius)
                                .border(1.dp, QnLine, QnRadius),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.name.take(1).ifBlank { "?" },
                                color = QnAccent,
                                fontFamily = QnMono,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.name.ifBlank { "未设置姓名" },
                                color = QnText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "@${uiState.username.ifBlank { "-" }}",
                                color = QnTextDim,
                                fontFamily = QnMono,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QnTag(text = roleLabel, color = QnAccent)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "ROLE ${uiState.role.ifBlank { "-" }}",
                            color = QnTextDim,
                            fontFamily = QnMono,
                            fontSize = 9.5.sp
                        )
                    }
                }

                QnCard {
                    QnSectionLabel("PROFILE")
                    Spacer(modifier = Modifier.height(8.dp))
                    QnKeyValue(label = "班级", value = uiState.className.ifBlank { "-" })
                    QnKeyValue(label = "部门", value = uiState.departmentName.ifBlank { "-" })
                    QnKeyValue(label = "手机", value = uiState.phone.ifBlank { "-" })
                    QnKeyValue(label = "角色", value = roleLabel)
                }

                Text(
                    text = "// 如需修改密码或账户信息，请联系管理员",
                    color = QnTextMuted,
                    fontFamily = QnMono,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}
