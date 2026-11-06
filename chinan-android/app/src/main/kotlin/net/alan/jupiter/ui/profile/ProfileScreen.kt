package net.alan.jupiter.ui.profile

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.alan.jupiter.ui.components.QnAvatar
import net.alan.jupiter.ui.components.QnCard
import net.alan.jupiter.ui.components.QnChip
import net.alan.jupiter.ui.components.QnDivider
import net.alan.jupiter.ui.components.QnGroupLabel
import net.alan.jupiter.ui.components.QnKeyValue
import net.alan.jupiter.ui.components.QnNoticeCard
import net.alan.jupiter.ui.components.QnScaffold
import net.alan.jupiter.ui.components.maskPhone
import net.alan.jupiter.ui.components.roleLabel
import net.alan.jupiter.ui.theme.QnTone

/**
 * 我的（/me）。
 *
 * 账户卡 + 资料卡（班级 / 部门 / 手机 / 角色）；不展示学号（账号体系里学号不是身份）。
 * 改密码指向设置页的「账户管理」。
 */
@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val profile by viewModel.uiState.collectAsState()

    QnScaffold(title = "我的", onBack = onBackClick) { padding ->
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
                QnCard(contentPadding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QnAvatar(
                            initial = profile.name.ifBlank { "?" },
                            size = 56.dp,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name.ifBlank { "未设置姓名" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (profile.username.isBlank()) {
                                    "-"
                                } else {
                                    "@${profile.username}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        QnChip(text = roleLabel(profile.role), tone = QnTone.Success)
                    }
                }

                QnCard(contentPadding = PaddingValues(18.dp)) {
                    QnGroupLabel("资料", modifier = Modifier.padding(top = 0.dp))
                    QnKeyValue(label = "班级", value = profile.className.ifBlank { "-" })
                    QnKeyValue(label = "部门", value = profile.departmentName.ifBlank { "-" })
                    QnKeyValue(label = "手机", value = maskPhone(profile.phone), mono = true)
                    QnKeyValue(label = "角色", value = roleLabel(profile.role))
                }

                QnNoticeCard(
                    text = "修改密码请到「设置 → 账户管理」。",
                    tone = QnTone.Neutral
                )

                QnCard(onClick = onSettingsClick, contentPadding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "前往设置",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "同步与上传 · 本地数据 · 退出登录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                QnDivider()
                Text(
                    text = "寝安 · 宿舍查寝管理系统",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
