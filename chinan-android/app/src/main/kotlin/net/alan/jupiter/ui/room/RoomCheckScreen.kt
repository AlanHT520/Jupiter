package net.alan.jupiter.ui.room

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import net.alan.jupiter.domain.model.CheckStatus
import net.alan.jupiter.ui.theme.*
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomCheckScreen(
    taskId: String,
    roomId: String,
    roomNumber: String,
    onBackClick: () -> Unit,
    onSubmitSuccess: () -> Unit,
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

    // 监听成功：绿圈动画 1.5 秒后返回
    LaunchedEffect(uiState.submitSuccess) {
        if (uiState.submitSuccess) {
            showSuccessAnimation = true
            delay(1500)
            showSuccessAnimation = false
            onSubmitSuccess()
        }
    }

    val dirtyCount = uiState.students.count { it.isDirty }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            uiState.roomNumber.ifBlank { roomNumber },
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (uiState.students.isNotEmpty()) {
                            Text(
                                "${uiState.students.size} 名学生",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
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
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "加载学生信息...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.students.isEmpty() && uiState.error == null -> {
                    net.alan.jupiter.ui.components.EmptyState(
                        icon = Icons.Outlined.PersonOff,
                        title = "暂无学生",
                        hint = "该房间暂未分配学生，请返回房间列表或联系管理员",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp,
                            top = 8.dp, bottom = 88.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (dirtyCount > 0) {
                            item(key = "summary") {
                                DirtySummaryCard(dirtyCount = dirtyCount, totalCount = uiState.students.size)
                            }
                        }

                        itemsIndexed(uiState.students, key = { _, sws -> sws.student.id }) { index, sws ->
                            StudentCard(
                                index = index,
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

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        shape = MaterialTheme.shapes.large,
                        shadowElevation = 6.dp,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Button(
                            onClick = { viewModel.submitChecks(taskId, roomId) },
                            enabled = !uiState.isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = MaterialTheme.shapes.large,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (dirtyCount > 0)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.tertiary
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            if (uiState.isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("提交中...")
                            } else {
                                Icon(
                                    if (dirtyCount > 0) Icons.Default.CloudUpload else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    if (dirtyCount > 0) "提交查寝结果 ($dirtyCount)" else "一键确认全部在寝",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showSuccessAnimation,
        enter = scaleIn(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = CircleShape,
                color = StatusPresent.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "提交成功",
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxSize(),
                    tint = Color.White
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

@Composable
private fun DirtySummaryCard(dirtyCount: Int, totalCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Icon(
                    Icons.Outlined.EditNote,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(5.dp)
                        .fillMaxSize(),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "已修改 $dirtyCount/$totalCount 名学生",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StudentCard(
    index: Int,
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
    val statusCol = currentStatus?.let { statusColor(it.code) } ?: StatusUnchecked
    val studentInitial = if (s.name.isNotEmpty()) s.name.first().toString() else "?"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onStatusClick() },
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDirty)
                statusCol.copy(alpha = 0.04f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isDirty) statusCol.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = statusCol.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = studentInitial,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusCol,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = s.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (s.gender.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = if (s.gender == "男")
                                    StatusIntern.copy(alpha = 0.1f)
                                else
                                    StatusFemale.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = s.gender,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (s.gender == "男") StatusIntern else StatusFemale,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (isDirty) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "已修改",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Row {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Badge,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = s.studentNo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Bed,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${s.bedNumber}床",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        if (s.className != null) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = s.className,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                FilledTonalButton(
                    onClick = onStatusClick,
                    modifier = Modifier.heightIn(min = 40.dp),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = statusCol.copy(alpha = 0.1f),
                        contentColor = statusCol
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusCol)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        currentStatus?.label ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (s.presetStatus != null && s.presetStatus != CheckStatus.PRESENT) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "系统预置: ${s.presetStatus.label}${s.presetStatusFrom?.let { " ($it)" } ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isDirty) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = studentWithStatus.remark,
                        onValueChange = onRemarkChanged,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        decorationBox = { innerTextField ->
                            if (studentWithStatus.remark.isEmpty()) {
                                Text(
                                    "点击添加备注...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (studentWithStatus.remark.isNotEmpty()) {
                        IconButton(
                            onClick = { onRemarkChanged("") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "清除备注",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = currentStatus == CheckStatus.PERSONAL_LEAVE
                        || currentStatus == CheckStatus.SICK_LEAVE,
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
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
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
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                // 服务端相对路径无法直接加载，显示"已上传"占位而不是破图
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.CameraAlt,
                                        contentDescription = "证明已上传",
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (localProof != null) "证明照片" else "证明已上传",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (localProof != null) "点击下方按钮重新拍摄" else "服务端已有证明，可重新拍摄替换",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            FilledTonalButton(
                                onClick = onTakePhoto,
                                modifier = Modifier.size(44.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.CameraAlt,
                                    contentDescription = "重拍证明照片",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            FilledTonalButton(
                                onClick = onPickFromGallery,
                                modifier = Modifier.size(44.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.PhotoLibrary,
                                    contentDescription = "从相册选择证明照片",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onTakePhoto,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Outlined.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("拍照")
                            }
                            FilledTonalButton(
                                onClick = onPickFromGallery,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Outlined.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("本地导入")
                            }
                        }
                    }
                }
            }
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
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "选择在寝状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CheckStatus.entries.forEach { status ->
                val col = statusColor(status.code)
                val isSelected = status == currentStatus

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(status) },
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            col.copy(alpha = 0.08f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = if (isSelected)
                        BorderStroke(1.5f.dp, col.copy(alpha = 0.5f))
                    else
                        null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = col.copy(alpha = 0.12f)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = status.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) col else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = statusDescription(status),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = col,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("取消")
            }
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