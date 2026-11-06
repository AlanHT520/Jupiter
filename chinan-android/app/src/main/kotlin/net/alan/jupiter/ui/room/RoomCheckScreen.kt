package net.alan.jupiter.ui.room

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import net.alan.jupiter.domain.model.CheckStatus
import net.alan.jupiter.ui.components.QnAvatar
import net.alan.jupiter.ui.components.QnCard
import net.alan.jupiter.ui.components.QnChip
import net.alan.jupiter.ui.components.QnDivider
import net.alan.jupiter.ui.components.QnEmpty
import net.alan.jupiter.ui.components.QnNoticeCard
import net.alan.jupiter.ui.components.QnPrimaryButton
import net.alan.jupiter.ui.components.QnScaffold
import net.alan.jupiter.ui.components.QnTextField
import net.alan.jupiter.ui.components.QnTonalButton
import net.alan.jupiter.ui.components.QnTopIcon
import net.alan.jupiter.ui.theme.QnTone
import net.alan.jupiter.ui.theme.statusTone
import java.io.File
import java.util.UUID

/**
 * 房间查寝（/tasks/{id}/rooms/{roomId}/{roomNumber}）。
 *
 * 学生卡 = 圆形头像位 + 姓名 / 床位 / 班级 + 状态 AssistChip；
 * 点状态打开 ModalBottomSheet 单选（RadioButton），请假类状态展开拍照 / 本地导入；
 * 底部常驻 SubmitBar；提交成功用结果卡 + Snackbar，不再有全屏呼吸动画。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomCheckScreen(
    taskId: String,
    roomId: String,
    roomNumber: String,
    onBackClick: () -> Unit,
    onSubmitSuccess: () -> Unit,
    buildingName: String = "",
    onOpenSync: (() -> Unit)? = null,
    viewModel: RoomCheckViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showStatusSheet by remember { mutableStateOf(false) }
    var statusPickerIndex by remember { mutableIntStateOf(-1) }
    var resultDismissed by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }
    var cameraTargetIndex by remember { mutableIntStateOf(-1) }
    var galleryTargetIndex by remember { mutableIntStateOf(-1) }
    val snackbarScope = rememberCoroutineScope()

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && cameraTargetIndex >= 0) {
            val path = pendingPhotoPath
            if (path != null) {
                viewModel.updateProofPhoto(cameraTargetIndex, path)
                Thread { saveToGallery(context, path) }.start()
            }
        }
        pendingPhotoPath = null
        cameraTargetIndex = -1
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && galleryTargetIndex >= 0) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    val file = File(context.cacheDir, "gallery_${UUID.randomUUID()}.jpg")
                    file.writeBytes(bytes)
                    viewModel.updateProofPhoto(galleryTargetIndex, file.absolutePath)
                }
            } catch (_: Exception) {
                // 读取相册失败不影响已有照片
            }
        }
        galleryTargetIndex = -1
    }

    val launchCameraIntent: () -> Unit = {
        val file = File(context.cacheDir, "proof_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        pendingPhotoPath = file.absolutePath
        val intent = android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        cameraLauncher.launch(intent)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && cameraTargetIndex >= 0) {
            launchCameraIntent()
        } else if (!granted) {
            snackbarScope.launch {
                snackbarHostState.showSnackbar(
                    message = "需要相机权限才能拍照取证",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    LaunchedEffect(roomId) { viewModel.loadRoom(taskId, roomId) }

    LaunchedEffect(uiState.submitSuccess) {
        if (uiState.submitSuccess) resultDismissed = false
    }

    // 失败就地给出「去同步」，不用回设置里找
    LaunchedEffect(uiState.error) {
        val msg = uiState.error ?: return@LaunchedEffect
        viewModel.clearError()
        val result = snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = if (onOpenSync != null) "去同步" else null,
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            onOpenSync?.invoke()
        }
    }

    val dirtyCount = uiState.students.count { it.isDirty }
    val displayRoom = uiState.roomNumber.ifBlank { roomNumber }
    val meta = listOfNotNull(
        "${uiState.students.size} 人",
        buildingName.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    val submitBar: (@Composable () -> Unit)? = if (uiState.students.isNotEmpty()) {
        {
            SubmitBar(
                students = uiState.students.size,
                dirtyCount = dirtyCount,
                queuedCount = uiState.queuedCount,
                isSubmitting = uiState.isSubmitting,
                onSubmit = { viewModel.submitChecks(taskId, roomId) }
            )
        }
    } else {
        null
    }

    QnScaffold(
        title = "$displayRoom 房间",
        onBack = onBackClick,
        actions = {
            QnTopIcon(
                icon = Icons.Default.Refresh,
                label = "刷新",
                onClick = { viewModel.loadRoom(taskId, roomId) }
            )
        },
        snackbarHostState = snackbarHostState,
        bottomBar = submitBar
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && uiState.students.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "正在加载学生名单…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.students.isEmpty() && uiState.error == null -> {
                    QnEmpty(
                        icon = Icons.Outlined.PersonOff,
                        title = "暂无学生",
                        hint = "该房间暂未分配学生，请返回房间列表或联系管理员",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                else -> {
                    val listState = rememberSaveable(saver = LazyListState.Saver) {
                        LazyListState()
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "room_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = meta.ifBlank { "本房间" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "查完每位学生的状态后点底部「提交本间」",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                QnChip(
                                    text = if (uiState.cacheOnly) "本地缓存" else "在线",
                                    tone = if (uiState.cacheOnly) QnTone.Warning else QnTone.Success
                                )
                            }
                        }

                        if (uiState.submitSuccess && !resultDismissed) {
                            item(key = "submit_result") {
                                QnCard(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentPadding = PaddingValues(16.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "本间查寝完成",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = if (uiState.submitQueued) {
                                                    "已保存到本机，联网后会自动上传"
                                                } else {
                                                    "已提交到服务器"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        QnTonalButton(
                                            text = "留在此页",
                                            onClick = { resultDismissed = true },
                                            modifier = Modifier.weight(1f)
                                        )
                                        QnTonalButton(
                                            text = "返回房间列表",
                                            onClick = onSubmitSuccess,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.cacheOnly) {
                            item(key = "cache_banner") {
                                QnNoticeCard(
                                    title = "当前离线",
                                    text = "学生名单与上次查寝状态来自本机快照，联网后会自动刷新",
                                    tone = QnTone.Warning
                                )
                            }
                        }

                        if (dirtyCount > 0) {
                            item(key = "dirty_banner") {
                                QnNoticeCard(
                                    text = "本间已修改 $dirtyCount 人，提交前会自动保存到本机。",
                                    tone = QnTone.Warning
                                )
                            }
                        }

                        itemsIndexed(
                            items = uiState.students,
                            key = { _, sws -> sws.student.id }
                        ) { index, sws ->
                            StudentCard(
                                studentWithStatus = sws,
                                onStatusClick = {
                                    statusPickerIndex = index
                                    showStatusSheet = true
                                },
                                onRemarkChanged = { viewModel.updateRemark(index, it) },
                                onTakePhoto = {
                                    cameraTargetIndex = index
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.CAMERA
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        launchCameraIntent()
                                    } else {
                                        cameraPermissionLauncher.launch(
                                            android.Manifest.permission.CAMERA
                                        )
                                    }
                                },
                                onPickFromGallery = {
                                    galleryTargetIndex = index
                                    galleryLauncher.launch("image/*")
                                },
                                onClearPhoto = { viewModel.clearProofPhoto(index) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showStatusSheet && statusPickerIndex in uiState.students.indices) {
        val currentStatus = uiState.students[statusPickerIndex].selectedStatus
        StatusPickerBottomSheet(
            currentStatus = currentStatus,
            sheetState = sheetState,
            onDismiss = { showStatusSheet = false },
            onSelect = { status ->
                viewModel.updateStatus(statusPickerIndex, status)
                showStatusSheet = false
            }
        )
    }
}

/** 底部常驻提交栏：中文人数 / 已修改 / 待同步 + 一个 filled 主操作。 */
@Composable
private fun SubmitBar(
    students: Int,
    dirtyCount: Int,
    queuedCount: Int,
    isSubmitting: Boolean,
    onSubmit: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column {
            QnDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$students 人 · 已修改 $dirtyCount · 待同步 $queuedCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (dirtyCount > 0) "改完状态后点右侧提交" else "确认无误后提交本间",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                QnPrimaryButton(
                    text = if (isSubmitting) "提交中…" else "提交本间",
                    onClick = onSubmit,
                    enabled = !isSubmitting
                )
            }
        }
    }
}

@Composable
private fun StudentCard(
    studentWithStatus: StudentWithStatus,
    onStatusClick: () -> Unit,
    onRemarkChanged: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onClearPhoto: () -> Unit
) {
    val s = studentWithStatus.student
    val currentStatus = studentWithStatus.selectedStatus
    val isDirty = studentWithStatus.isDirty
    val tone = statusTone(currentStatus?.code)
    val needsProof = currentStatus == CheckStatus.PERSONAL_LEAVE ||
        currentStatus == CheckStatus.SICK_LEAVE

    QnCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QnAvatar(initial = s.name, size = 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = s.name.ifBlank { "未命名" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    if (s.gender.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = s.gender,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isDirty) {
                        Spacer(modifier = Modifier.width(6.dp))
                        QnChip(text = "已修改", tone = QnTone.Warning)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        s.bedNumber.takeIf { it.isNotBlank() }?.let { "${it} 床" },
                        s.className?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ").ifBlank { "--" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            QnChip(
                text = currentStatus?.label ?: "未设置",
                tone = tone,
                onClick = onStatusClick
            )
        }

        if (s.presetStatus != null && s.presetStatus != CheckStatus.PRESENT) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "学籍预置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                QnChip(
                    text = s.presetStatus.label,
                    tone = statusTone(s.presetStatus.code)
                )
                if (s.presetStatusFrom != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = s.presetStatusFrom,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        // 备注默认不显示：只有「本次确实改过状态」的学生才出现备注入口。
        // 已有备注（上次查寝留下的）仍然显示，否则用户看不到自己写过什么。
        val showRemark = isDirty || studentWithStatus.remark.isNotBlank()
        if (showRemark) {
            Spacer(modifier = Modifier.height(10.dp))

            val clearRemarkSlot: (@Composable () -> Unit)? =
                if (studentWithStatus.remark.isNotEmpty()) {
                    {
                        IconButton(onClick = { onRemarkChanged("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "清除备注",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    null
                }

            QnTextField(
                value = studentWithStatus.remark,
                onValueChange = onRemarkChanged,
                placeholder = "备注（选填）",
                maxLines = 2,
                singleLine = false,
                trailingIcon = clearRemarkSlot
            )
        }

        AnimatedVisibility(
            visible = needsProof,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(10.dp))
                val localProof = studentWithStatus.proofLocalPath
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (localProof != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(localProof)
                                .crossfade(true)
                                .build(),
                            contentDescription = "请假证明",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        QnTonalButton(
                            text = if (localProof != null) "重新拍照" else "拍照",
                            onClick = onTakePhoto,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        QnTonalButton(
                            text = "本地导入",
                            onClick = onPickFromGallery,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (localProof != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    RemoveProofRow(onClear = onClearPhoto)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "请假类状态建议拍照留证，证明会随记录一起上传。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RemoveProofRow(onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClear),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "删除这张证明",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/** 状态选择：M3 单选列表（RadioButton），一行一个中文状态 + 说明。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusPickerBottomSheet(
    currentStatus: CheckStatus?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSelect: (CheckStatus) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "选择查寝状态",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            CheckStatus.entries.forEach { status ->
                val selected = status == currentStatus
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onSelect(status) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onSelect(status) }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = statusDescription(status),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                QnDivider()
            }

            Spacer(modifier = Modifier.height(12.dp))
            QnTonalButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 将拍照的请假截图保存到系统相册（Pictures/Chinan 目录），
 * 方便用户在系统相册中也能查看和管理。
 */
private fun saveToGallery(context: android.content.Context, filePath: String) {
    try {
        val file = File(filePath)
        if (!file.exists()) return

        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "Chinan_${System.currentTimeMillis()}.jpg"
            )
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Chinan"
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        )
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                out.write(file.readBytes())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(it, values, null, null)
            }
        }
    } catch (_: Exception) {
        // 保存到相册失败不影响主流程（查寝提交照常进行）
    }
}

private fun statusDescription(status: CheckStatus): String = when (status) {
    CheckStatus.UNKNOWN -> "尚未查寝"
    CheckStatus.PRESENT -> "学生在宿舍内"
    CheckStatus.PERSONAL_LEAVE -> "因私事请假离校"
    CheckStatus.SICK_LEAVE -> "因病请假离校"
    CheckStatus.ABSENT -> "未经批准擅自离宿"
    CheckStatus.MILITARY -> "因征兵入伍不在校"
    CheckStatus.INTERN -> "校外实习"
    CheckStatus.DAY_STUDENT -> "走读生，不在校住宿"
    CheckStatus.LATE_RETURN -> "晚归，查寝时未在宿舍"
    CheckStatus.LONG_SICK_LEAVE -> "长期病假，不在校"
}
