package net.alan.jupiter.ui.room

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.alan.jupiter.domain.model.CheckStatus
import net.alan.jupiter.ui.components.QnCard
import net.alan.jupiter.ui.components.QnEmpty
import net.alan.jupiter.ui.components.QnGhostButton
import net.alan.jupiter.ui.components.QnMono
import net.alan.jupiter.ui.components.QnPrimaryButton
import net.alan.jupiter.ui.components.QnRadius
import net.alan.jupiter.ui.components.QnScaffold
import net.alan.jupiter.ui.components.QnStatus
import net.alan.jupiter.ui.components.QnStatusTag
import net.alan.jupiter.ui.components.QnTag
import net.alan.jupiter.ui.theme.QnAccent
import net.alan.jupiter.ui.theme.QnBg
import net.alan.jupiter.ui.theme.QnLine
import net.alan.jupiter.ui.theme.QnPanel
import net.alan.jupiter.ui.theme.QnPanelAlt
import net.alan.jupiter.ui.theme.QnText
import net.alan.jupiter.ui.theme.QnTextDim
import net.alan.jupiter.ui.theme.QnTextMuted
import net.alan.jupiter.ui.theme.QnWarn
import net.alan.jupiter.ui.theme.StatusFemale
import net.alan.jupiter.ui.theme.StatusIntern
import net.alan.jupiter.ui.theme.statusColor
import java.io.File
import java.util.UUID

/**
 * 房间查寝（/check/<building>/<room>）：学生卡 = QnCard + QnTag(状态)；
 * 状态选择沿用原 ModalBottomSheet 逻辑，改为暗色细线风格；
 * 底部提交栏常驻并显示 ● QUEUED N。
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
    viewModel: RoomCheckViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showStatusSheet by remember { mutableStateOf(false) }
    var statusPickerIndex by remember { mutableIntStateOf(-1) }
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var cameraTargetIndex by remember { mutableIntStateOf(-1) }
    var galleryTargetIndex by remember { mutableIntStateOf(-1) }
    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }
    var showSuccessAnimation by remember { mutableStateOf(false) }
    val snackbarScope = rememberCoroutineScope()

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && cameraTargetIndex >= 0) {
            val path = pendingPhotoPath
            if (path != null) {
                viewModel.updateProofPhoto(cameraTargetIndex, path)
                // 同时保存到系统相册
                Thread {
                    saveToGallery(context, path)
                }.start()
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
            } catch (_: Exception) {}
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
        cameraUri = uri
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

    // 监听错误：弹 Snackbar 并清空，避免重复触发
    LaunchedEffect(uiState.error) {
        val msg = uiState.error ?: return@LaunchedEffect
        viewModel.clearError()
        snackbarHostState.showSnackbar(
            message = msg,
            duration = SnackbarDuration.Short
        )
    }

    // 监听成功：展示完成动画，但不自动返回；由用户点「留在此页」或「返回房间列表」
    LaunchedEffect(uiState.submitSuccess) {
        if (uiState.submitSuccess) {
            showSuccessAnimation = true
        }
    }

    val dirtyCount = uiState.students.count { it.isDirty }
    val displayRoom = uiState.roomNumber.ifBlank { roomNumber }
    val path = buildString {
        append("/check/")
        if (buildingName.isNotBlank()) {
            append(buildingName)
            append('/')
        }
        append(displayRoom)
    }

    val headerStatus = when {
        uiState.isSubmitting -> QnStatus.QUEUED
        uiState.queuedCount > 0 -> QnStatus.QUEUED
        uiState.error != null -> QnStatus.FAILED
        uiState.cacheOnly -> QnStatus.CACHED
        dirtyCount > 0 -> QnStatus.QUEUED
        uiState.isLoading && uiState.students.isEmpty() -> QnStatus.RUNNING
        uiState.students.isEmpty() -> QnStatus.IDLE
        else -> QnStatus.OK
    }
    val headerDetail = when {
        uiState.queuedCount > 0 -> "${uiState.queuedCount}"
        dirtyCount > 0 -> "$dirtyCount"
        else -> null
    }

    QnScaffold(
        path = path,
        status = headerStatus,
        statusDetail = headerDetail,
        onBack = onBackClick,
        actions = {
            IconButton(
                onClick = { viewModel.loadRoom(taskId, roomId) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "刷新",
                    tint = QnTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        bottomBar = {
            if (uiState.students.isNotEmpty()) {
                SubmitBar(
                    students = uiState.students.size,
                    dirtyCount = dirtyCount,
                    queuedCount = uiState.queuedCount,
                    isSubmitting = uiState.isSubmitting,
                    enabled = uiState.students.isNotEmpty(),
                    onSubmit = { viewModel.submitChecks(taskId, roomId) }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val listState = rememberSaveable(saver = LazyListState.Saver) {
                LazyListState()
            }
            when {
                uiState.isLoading && uiState.students.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(34.dp),
                            strokeWidth = 2.dp,
                            color = QnAccent
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "// 正在加载学生信息",
                            color = QnTextDim,
                            fontFamily = QnMono,
                            fontSize = 11.sp
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
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp,
                            top = 10.dp, bottom = 14.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.cacheOnly) {
                            item(key = "cache_banner") {
                                QnCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        QnTag(text = "CACHED", color = QnTextMuted)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "离线缓存：学生名单与上次查寝状态来自本地快照",
                                            color = QnTextMuted,
                                            fontSize = 11.sp,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }

                        if (dirtyCount > 0) {
                            item(key = "summary") {
                                DirtySummaryCard(
                                    dirtyCount = dirtyCount,
                                    totalCount = uiState.students.size
                                )
                            }
                        }

                        itemsIndexed(uiState.students, key = { _, sws -> sws.student.id }) { index, sws ->
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
                                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
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

    AnimatedVisibility(
        visible = showSuccessAnimation,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220)),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.96f, animationSpec = tween(150))
    ) {
        val pulse = rememberInfiniteTransition(label = "room-check-success")
        val glowAlpha by pulse.animateFloat(
            initialValue = 0.45f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "glow-alpha"
        )
        val glowScale by pulse.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "glow-scale"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(QnBg.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(136.dp)) {
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .graphicsLayer {
                                scaleX = glowScale
                                scaleY = glowScale
                                alpha = glowAlpha
                            }
                            .border(1.dp, QnAccent, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(QnPanel, QnRadius)
                            .border(1.dp, QnAccent, QnRadius),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "提交成功",
                            modifier = Modifier.size(46.dp),
                            tint = QnAccent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "本间查寝完成",
                    color = QnText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$displayRoom · " + if (uiState.submitQueued) "已保存本地，等待同步" else "已提交服务器",
                    color = QnAccent,
                    fontFamily = QnMono,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                QnPrimaryButton(
                    text = "留在此页继续查看",
                    onClick = { showSuccessAnimation = false }
                )
                Spacer(modifier = Modifier.height(8.dp))
                QnGhostButton(
                    text = "返回房间列表",
                    onClick = {
                        showSuccessAnimation = false
                        onSubmitSuccess()
                    }
                )
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

/** 底部提交栏：● QUEUED N（本地待上行）+ 提交按钮（原逻辑不变）。 */
@Composable
private fun SubmitBar(
    students: Int,
    dirtyCount: Int,
    queuedCount: Int,
    isSubmitting: Boolean,
    enabled: Boolean,
    onSubmit: () -> Unit
) {
    Surface(color = QnPanelAlt) {
        Column {
            HorizontalDivider(color = QnLine, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    QnStatusTag(
                        status = if (queuedCount > 0 || dirtyCount > 0) {
                            QnStatus.QUEUED
                        } else {
                            QnStatus.IDLE
                        },
                        detail = when {
                            queuedCount > 0 -> "$queuedCount"
                            dirtyCount > 0 -> "$dirtyCount"
                            else -> null
                        }
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "STUDENTS $students · EDITED $dirtyCount · QUEUED $queuedCount",
                        color = QnTextDim,
                        fontFamily = QnMono,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                QnPrimaryButton(
                    text = when {
                        isSubmitting -> "提交中..."
                        dirtyCount > 0 -> "提交 ($dirtyCount)"
                        else -> "确认在寝"
                    },
                    onClick = onSubmit,
                    enabled = enabled && !isSubmitting
                )
            }
        }
    }
}

@Composable
private fun DirtySummaryCard(dirtyCount: Int, totalCount: Int) {
    QnCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QnTag(text = "EDITED", color = QnWarn)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "已修改 $dirtyCount/$totalCount 名学生",
                color = QnTextMuted,
                fontSize = 11.sp
            )
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
    val statusCol = currentStatus?.let { statusColor(it.code) } ?: QnTextDim
    val studentInitial = if (s.name.isNotEmpty()) s.name.first().toString() else "?"

    QnCard(
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) { onStatusClick() },
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(QnPanelAlt, QnRadius)
                    .border(1.dp, QnLine, QnRadius),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = studentInitial,
                    color = QnText,
                    fontFamily = QnMono,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = s.name,
                        color = QnText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    if (s.gender.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        QnTag(
                            text = s.gender,
                            color = if (s.gender == "男") StatusIntern else StatusFemale
                        )
                    }
                    if (isDirty) {
                        Spacer(modifier = Modifier.width(6.dp))
                        QnTag(text = "EDITED", color = QnWarn)
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = listOfNotNull(
                        "${s.bedNumber}床".takeIf { s.bedNumber.isNotBlank() },
                        s.className?.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    color = QnTextDim,
                    fontFamily = QnMono,
                    fontSize = 9.5.sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = QnRadius,
                color = QnPanelAlt,
                border = BorderStroke(1.dp, QnLine),
                modifier = Modifier.clickable(
                    onClickLabel = "选择在寝状态"
                ) { onStatusClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QnTag(
                        text = currentStatus?.label ?: "未设置",
                        color = statusCol
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        Icons.Default.UnfoldMore,
                        contentDescription = null,
                        tint = QnTextDim,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        if (s.presetStatus != null && s.presetStatus != CheckStatus.PRESENT) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, QnLine, QnRadius)
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PRESET",
                    color = QnTextDim,
                    fontFamily = QnMono,
                    fontSize = 9.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                QnTag(
                    text = s.presetStatus.label,
                    color = statusColor(s.presetStatus.code)
                )
                if (s.presetStatusFrom != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = s.presetStatusFrom,
                        color = QnTextDim,
                        fontFamily = QnMono,
                        fontSize = 9.sp
                    )
                }
            }
        }

        if (isDirty) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, QnLine, QnRadius)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = QnTextDim
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = studentWithStatus.remark,
                    onValueChange = onRemarkChanged,
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = QnText,
                        fontFamily = QnMono,
                        fontSize = 11.sp
                    ),
                    cursorBrush = SolidColor(QnAccent),
                    decorationBox = { innerTextField ->
                        if (studentWithStatus.remark.isEmpty()) {
                            Text(
                                "点击添加备注...",
                                color = QnTextDim,
                                fontFamily = QnMono,
                                fontSize = 10.5.sp
                            )
                        }
                        innerTextField()
                    }
                )
                if (studentWithStatus.remark.isNotEmpty()) {
                    IconButton(
                        onClick = { onRemarkChanged("") },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "清除备注",
                            modifier = Modifier.size(14.dp),
                            tint = QnTextMuted
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = currentStatus == CheckStatus.PERSONAL_LEAVE ||
                    currentStatus == CheckStatus.SICK_LEAVE,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                val localProof = studentWithStatus.proofLocalPath
                val serverProof = studentWithStatus.proofFilePath
                if (localProof != null || serverProof != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, QnLine, QnRadius)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (localProof != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(localProof)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "证明照片",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(QnRadius),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            // 服务端相对路径无法直接加载，显示"已上传"占位而不是破图
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(QnRadius)
                                    .background(QnPanelAlt)
                                    .border(1.dp, QnLine, QnRadius),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.CameraAlt,
                                    contentDescription = "证明已上传",
                                    modifier = Modifier.size(20.dp),
                                    tint = QnTextDim
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (localProof != null) "PROOF" else "PROOF (SERVER)",
                                color = QnTextMuted,
                                fontFamily = QnMono,
                                fontSize = 9.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (localProof != null) "点击右侧按钮重新拍摄" else "服务端已有证明，可重新拍摄替换",
                                color = QnTextDim,
                                fontSize = 10.sp,
                                maxLines = 2
                            )
                        }
                        ProofIconButton(
                            icon = Icons.Outlined.CameraAlt,
                            label = "重拍证明照片",
                            onClick = onTakePhoto
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ProofIconButton(
                            icon = Icons.Outlined.PhotoLibrary,
                            label = "从相册选择证明照片",
                            onClick = onPickFromGallery
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QnGhostButton(
                            text = "拍照",
                            onClick = onTakePhoto,
                            modifier = Modifier.weight(1f)
                        )
                        QnGhostButton(
                            text = "本地导入",
                            onClick = onPickFromGallery,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProofIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = QnRadius,
        color = QnPanelAlt,
        border = BorderStroke(1.dp, QnLine)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = QnText,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

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
        shape = QnRadius,
        containerColor = QnPanel,
        tonalElevation = 0.dp,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(2.dp)
                        .background(QnLine)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "SELECT STATUS",
                    color = QnTextDim,
                    fontFamily = QnMono,
                    fontSize = 9.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CheckStatus.entries.forEach { status ->
                val col = statusColor(status.code)
                val isSelected = status == currentStatus

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(status) },
                    shape = QnRadius,
                    color = if (isSelected) QnPanelAlt else QnPanel,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) col.copy(alpha = 0.65f) else QnLine
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QnTag(text = status.label, color = col)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = statusDescription(status),
                            color = QnTextDim,
                            fontFamily = QnMono,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = col,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            QnGhostButton(
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
            put(MediaStore.Images.Media.DISPLAY_NAME,
                "Chinan_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Chinan")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
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
