package com.litetask.app.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.litetask.app.data.model.VoiceNoteData
import com.litetask.app.data.model.VoiceNoteSupport
import com.litetask.app.util.AttachmentStorageHelper
import com.litetask.app.util.VoiceNotePlaybackState
import com.litetask.app.util.VoiceNotePlayerManager
import com.litetask.app.util.VoiceNoteRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 任务详情与编辑弹窗内展示的语音备忘录播放器卡片。
 * 支持单实例全局互斥播放、波形进度条点击跳转、倍速切换、转文字、文本编辑、提取子任务与分享 `.m4a`。
 */
@Composable
fun VoiceNoteCard(
    componentKey: String,
    data: VoiceNoteData,
    playbackState: VoiceNotePlaybackState,
    onUpdateData: ((VoiceNoteData) -> Unit)? = null,
    onTranscribe: (suspend (VoiceNoteData) -> Result<String>)? = null,
    onGenerateSubTasks: ((String) -> Unit)? = null,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isActive = playbackState.activeKey == componentKey
    val isPlaying = isActive && playbackState.isPlaying
    val totalDurationMs = if (isActive && playbackState.durationMs > 0L) {
        playbackState.durationMs
    } else {
        data.durationMs.coerceAtLeast(1000L)
    }
    val currentPosMs = if (isActive) playbackState.positionMs.coerceIn(0L, totalDurationMs) else 0L
    val progress = (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)

    val isAccessible = remember(data.filePath, data.fileUri) {
        AttachmentStorageHelper.isVoiceNoteAccessible(context, data)
    }
    val peaks = remember(data.waveformPeaks) {
        if (data.waveformPeaks.isNotEmpty()) {
            data.waveformPeaks
        } else {
            List(VoiceNoteSupport.WAVEFORM_BAR_COUNT) { idx -> 20 + (idx % 4) * 14 }
        }
    }

    var isTranscribing by remember(componentKey) { mutableStateOf(false) }
    var transcriptExpanded by remember(componentKey) { mutableStateOf(!data.transcript.isNullOrBlank()) }
    var showEditDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color(0xFFE11D48),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = data.label?.trim()?.takeIf { it.isNotEmpty() } ?: "语音备忘录",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = { VoiceNotePlayerManager.cycleSpeed() },
                    label = {
                        Text(
                            text = VoiceNoteSupport.formatSpeedLabel(playbackState.speed),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.height(26.dp)
                )
                if (onUpdateData != null) {
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑标题与转写文本",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { AttachmentStorageHelper.shareVoiceNote(context, data) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "分享音频",
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = {
                        if (isActive) VoiceNotePlayerManager.stop()
                        onRemove()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "删除语音备忘",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 波形播放控制行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledIconButton(
                    onClick = {
                        VoiceNotePlayerManager.togglePlayPause(context, componentKey, data)
                    },
                    enabled = isAccessible,
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isPlaying) Color(0xFFE11D48) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(20.dp)
                    )
                }

                VoiceWaveformBar(
                    peaks = peaks,
                    progress = progress,
                    enabled = isAccessible && isActive,
                    onSeekFraction = { frac ->
                        VoiceNotePlayerManager.seekToFraction(componentKey, frac)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                )

                Text(
                    text = if (isActive) {
                        "${VoiceNoteSupport.formatDurationClock(currentPosMs)} / ${VoiceNoteSupport.formatDurationClock(totalDurationMs)}"
                    } else {
                        VoiceNoteSupport.formatDurationClock(totalDurationMs)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isAccessible) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            if (!isAccessible) {
                Text(
                    text = "音频文件暂不可用（可能已被外部清理或未随纯文本备份导入）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // 转文字与子任务快捷按钮行
            if (isAccessible && (onTranscribe != null || !data.transcript.isNullOrBlank())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onTranscribe != null && onUpdateData != null) {
                        OutlinedButton(
                            onClick = {
                                if (isTranscribing) return@OutlinedButton
                                scope.launch {
                                    isTranscribing = true
                                    val result = withContext(Dispatchers.IO) { onTranscribe(data) }
                                    isTranscribing = false
                                    result.onSuccess { text ->
                                        onUpdateData(data.copy(transcript = text))
                                        transcriptExpanded = true
                                        Toast.makeText(context, "语音转文字完成", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        Toast.makeText(
                                            context,
                                            err.message ?: "转写失败，请检查语音服务配置",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            enabled = !isTranscribing,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            if (isTranscribing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("识别中...", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (data.transcript.isNullOrBlank()) "转为文字" else "重新转写",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    val existingTranscript = data.transcript?.trim().orEmpty()
                    if (existingTranscript.isNotEmpty() && onGenerateSubTasks != null) {
                        FilledTonalButton(
                            onClick = { onGenerateSubTasks(existingTranscript) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("生成子任务", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (existingTranscript.isNotEmpty()) {
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { transcriptExpanded = !transcriptExpanded },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = if (transcriptExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (transcriptExpanded) "收起转写" else "展开转写",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (transcriptExpanded && !data.transcript.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = data.transcript.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }

    if (showEditDialog && onUpdateData != null) {
        EditVoiceNoteMetaDialog(
            data = data,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                onUpdateData(updated)
                if (!updated.transcript.isNullOrBlank()) {
                    transcriptExpanded = true
                }
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun VoiceWaveformBar(
    peaks: List<Int>,
    progress: Float,
    enabled: Boolean,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = Color(0xFFE11D48)
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    Canvas(
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures { offset ->
                if (size.width > 0) {
                    onSeekFraction((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
        }
    ) {
        if (peaks.isEmpty()) return@Canvas
        val count = peaks.size
        val step = size.width / count.toFloat()
        val barWidth = (step * 0.56f).coerceIn(2f, 8f)
        val radius = barWidth / 2f
        val maxH = size.height
        val playedBars = (progress * count).toInt()

        peaks.forEachIndexed { idx, peak ->
            val ratio = (peak.coerceIn(8, 100) / 100f)
            val barH = (maxH * ratio).coerceAtLeast(barWidth)
            val left = idx * step + (step - barWidth) / 2f
            val top = (maxH - barH) / 2f
            drawRoundRect(
                color = if (idx <= playedBars && progress > 0f) activeColor else inactiveColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
    }
}

@Composable
private fun EditVoiceNoteMetaDialog(
    data: VoiceNoteData,
    onDismiss: () -> Unit,
    onSave: (VoiceNoteData) -> Unit
) {
    var label by remember(data) { mutableStateOf(data.label.orEmpty()) }
    var transcript by remember(data) { mutableStateOf(data.transcript.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑语音备忘信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("备忘标题（可选）") },
                    placeholder = { Text("如：客户口头需求确认") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = transcript,
                    onValueChange = { transcript = it },
                    label = { Text("文字记录 / 转写文本") },
                    placeholder = { Text("补充或修订语音要点...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        data.copy(
                            label = label.trim().takeIf { it.isNotEmpty() },
                            transcript = transcript.trim().takeIf { it.isNotEmpty() }
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 语音备忘录录制弹窗（含 RECORD_AUDIO 权限申请、实时波形、3分钟上限保护）。
 */
@Composable
fun RecordVoiceNoteDialog(
    onDismiss: () -> Unit,
    onConfirm: (VoiceNoteData) -> Unit
) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (!granted) permissionDeniedOnce = true
    }

    val recorder = remember { VoiceNoteRecorder(context.applicationContext) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var label by remember { mutableStateOf("") }
    var recordError by remember { mutableStateOf<String?>(null) }
    val livePeaks = remember { mutableStateListOf<Int>() }
    var completedSaveTriggered by remember { mutableStateOf(false) }

    fun finishAndEmit() {
        if (!isRecording || completedSaveTriggered) return
        val output = recorder.stopAndSave()
        isRecording = false
        if (output == null) {
            Toast.makeText(context, "录音时长过短或写入失败", Toast.LENGTH_SHORT).show()
            onDismiss()
            return
        }
        completedSaveTriggered = true
        val shareUri = runCatching {
            AttachmentStorageHelper.fileProviderUri(context, output.file).toString()
        }.getOrNull()
        VoiceNotePlayerManager.stop()
        onConfirm(
            VoiceNoteData(
                filePath = output.file.absolutePath,
                fileUri = shareUri,
                durationMs = output.durationMs,
                waveformPeaks = output.waveformPeaks,
                label = label.trim().takeIf { it.isNotEmpty() },
                createdAtMs = System.currentTimeMillis()
            )
        )
    }

    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(hasMicPermission) {
        if (hasMicPermission && !isRecording && !completedSaveTriggered && recordError == null) {
            VoiceNotePlayerManager.stop()
            if (recorder.start()) {
                isRecording = true
            } else {
                recordError = "无法开启麦克风录音，请确认未被其他应用占用"
            }
        }
    }

    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        while (isActive && isRecording) {
            val amp = recorder.sampleAmplitude()
            elapsedMs = recorder.currentDurationMs()
            val normalizedBar = ((amp / 32767f).coerceIn(0f, 1f) * 90f + 10f).toInt().coerceIn(8, 100)
            livePeaks.add(normalizedBar)
            if (livePeaks.size > VoiceNoteSupport.WAVEFORM_BAR_COUNT) {
                livePeaks.removeAt(0)
            }
            if (elapsedMs >= VoiceNoteSupport.MAX_RECORDING_DURATION_MS) {
                Toast.makeText(context, "已达单条 3 分钟录制上限，自动保存", Toast.LENGTH_SHORT).show()
                finishAndEmit()
                break
            }
            delay(100L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!completedSaveTriggered) {
                recorder.cancel()
            }
        }
    }

    Dialog(
        onDismissRequest = {
            recorder.cancel()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "录制语音备忘",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                when {
                    !hasMicPermission -> {
                        Text(
                            text = if (permissionDeniedOnce) {
                                "需要麦克风权限以录制任务语音备忘。"
                            } else {
                                "正在请求麦克风录音权限..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = onDismiss) { Text("取消") }
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                                Text("授予权限")
                            }
                            if (permissionDeniedOnce) {
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        runCatching { context.startActivity(intent) }
                                    }
                                ) { Text("系统设置") }
                            }
                        }
                    }
                    recordError != null -> {
                        Text(
                            text = recordError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = onDismiss) { Text("关闭") }
                    }
                    else -> {
                        Text(
                            text = "${VoiceNoteSupport.formatDurationClock(elapsedMs)} / ${VoiceNoteSupport.formatDurationClock(VoiceNoteSupport.MAX_RECORDING_DURATION_MS)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE11D48)
                        )

                        VoiceWaveformBar(
                            peaks = if (livePeaks.isEmpty()) List(VoiceNoteSupport.WAVEFORM_BAR_COUNT) { 12 } else livePeaks.toList(),
                            progress = 1f,
                            enabled = false,
                            onSeekFraction = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        )

                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text("备忘标题（可选）") },
                            placeholder = { Text("如：方案沟通关键结论") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    recorder.cancel()
                                    onDismiss()
                                }
                            ) {
                                Text("取消并丢弃")
                            }

                            Button(
                                onClick = { finishAndEmit() },
                                enabled = isRecording && elapsedMs >= 500L,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("完成并保存")
                            }
                        }
                    }
                }
            }
        }
    }
}
