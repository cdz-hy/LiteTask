package com.litetask.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.litetask.app.data.model.FileAttachmentData
import com.litetask.app.util.AttachmentContentReader
import com.litetask.app.util.AttachmentContentReader.AttachmentPreviewType
import com.litetask.app.util.AttachmentStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用内附件预览查看器：
 * - 图片：支持双指缩放、拖拽与双击复位；
 * - PDF：基于 PdfRenderer 逐页渲染并支持翻页与缩放；
 * - 纯文本 / Markdown / JSON / CSV / 日志：支持有界可选择正文预览；
 * - 其他二进制格式：展示文件元信息并一键调用系统应用打开或分享；
 * - 失效外链：引导重连文件。
 */
@Composable
fun AttachmentViewerDialog(
    data: FileAttachmentData,
    onDismiss: () -> Unit,
    onRequestRename: (() -> Unit)? = null,
    onRequestRelink: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val previewType = remember(data.fileName, data.mimeType) {
        AttachmentContentReader.detectPreviewType(data)
    }

    var isAccessible by remember(data) { mutableStateOf(true) }
    var isLoading by remember(data) { mutableStateOf(true) }
    var imageBitmap by remember(data) { mutableStateOf<Bitmap?>(null) }
    var textPreview by remember(data) { mutableStateOf<AttachmentContentReader.TextPreviewResult?>(null) }
    var pdfPageCount by remember(data) { mutableStateOf(0) }
    var pdfPageIndex by remember(data) { mutableStateOf(0) }
    var pdfBitmap by remember(data, pdfPageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(data) {
        isLoading = true
        val accessible = withContext(Dispatchers.IO) {
            AttachmentStorageHelper.isFileAccessible(context, data)
        }
        isAccessible = accessible
        if (!accessible) {
            isLoading = false
            return@LaunchedEffect
        }

        when (previewType) {
            AttachmentPreviewType.IMAGE -> {
                imageBitmap = withContext(Dispatchers.IO) {
                    AttachmentContentReader.decodeBitmapForPreview(context, data)
                }
            }
            AttachmentPreviewType.PDF -> {
                pdfPageCount = withContext(Dispatchers.IO) {
                    AttachmentContentReader.getPdfPageCount(context, data)
                }
            }
            AttachmentPreviewType.TEXT -> {
                textPreview = withContext(Dispatchers.IO) {
                    AttachmentContentReader.readTextPreview(context, data)
                }
            }
            AttachmentPreviewType.UNSUPPORTED -> Unit
        }
        isLoading = false
    }

    LaunchedEffect(data, pdfPageIndex, pdfPageCount, isAccessible) {
        if (previewType == AttachmentPreviewType.PDF && isAccessible && pdfPageCount > 0) {
            isLoading = true
            pdfBitmap = withContext(Dispatchers.IO) {
                AttachmentContentReader.renderPdfPage(context, data, pdfPageIndex)
            }
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部标题与动作栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = data.effectiveDisplayName(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val storageLabel = if (data.isManagedCopy) "本地托管" else "外部引用"
                        val pageLabel = if (previewType == AttachmentPreviewType.PDF && pdfPageCount > 0) {
                            " · 第 ${pdfPageIndex + 1}/$pdfPageCount 页"
                        } else ""
                        Text(
                            text = "${formatFileSize(data.fileSize)} · $storageLabel$pageLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (onRequestRename != null) {
                        IconButton(onClick = onRequestRename) {
                            Icon(Icons.Default.Edit, contentDescription = "修改备注名")
                        }
                    }
                    if (isAccessible) {
                        IconButton(onClick = { shareFileAttachment(context, data) }) {
                            Icon(Icons.Default.Share, contentDescription = "分享")
                        }
                        IconButton(onClick = { openFileAttachment(context, data) }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "外部应用打开")
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 主预览区域
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        !isAccessible -> {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "源文件已移动或系统授权已失效",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "文件名: ${data.fileName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (onRequestRelink != null) {
                                    Button(onClick = onRequestRelink) {
                                        Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("重新选择文件关联")
                                    }
                                }
                            }
                        }

                        isLoading && imageBitmap == null && pdfBitmap == null && textPreview == null -> {
                            CircularProgressIndicator()
                        }

                        previewType == AttachmentPreviewType.IMAGE -> {
                            val bmp = imageBitmap
                            if (bmp != null) {
                                ZoomableBitmapViewer(
                                    bitmap = bmp,
                                    contentDescription = data.effectiveDisplayName()
                                )
                            } else {
                                FallbackExternalOpenView(data = data)
                            }
                        }

                        previewType == AttachmentPreviewType.PDF -> {
                            val bmp = pdfBitmap
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .background(Color(0xFF1C1E21)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bmp != null) {
                                        ZoomableBitmapViewer(
                                            bitmap = bmp,
                                            contentDescription = "PDF 第 ${pdfPageIndex + 1} 页"
                                        )
                                    } else if (isLoading) {
                                        CircularProgressIndicator(color = Color.White)
                                    } else {
                                        FallbackExternalOpenView(data = data)
                                    }
                                }
                                if (pdfPageCount > 1) {
                                    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = { if (pdfPageIndex > 0) pdfPageIndex-- },
                                                enabled = pdfPageIndex > 0
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null)
                                                Text("上一页")
                                            }
                                            Text(
                                                text = "第 ${pdfPageIndex + 1} / $pdfPageCount 页",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            TextButton(
                                                onClick = { if (pdfPageIndex < pdfPageCount - 1) pdfPageIndex++ },
                                                enabled = pdfPageIndex < pdfPageCount - 1
                                            ) {
                                                Text("下一页")
                                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        previewType == AttachmentPreviewType.TEXT -> {
                            val preview = textPreview
                            if (preview != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp)
                                ) {
                                    if (preview.content.isBlank()) {
                                        Text(
                                            text = "（文件内容为空）",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        SelectionContainer {
                                            Text(
                                                text = preview.content,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    lineHeight = 20.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    if (preview.isTruncated) {
                                        Spacer(Modifier.height(12.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "文件较长，已截取前段预览，完整内容可点击右上角用外部应用打开。",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                FallbackExternalOpenView(data = data)
                            }
                        }

                        else -> FallbackExternalOpenView(data = data)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableBitmapViewer(
    bitmap: Bitmap,
    contentDescription: String
) {
    var scale by remember(bitmap) { mutableStateOf(1f) }
    var offsetX by remember(bitmap) { mutableStateOf(0f) }
    var offsetY by remember(bitmap) { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141619))
            .pointerInput(bitmap) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.2f
                        }
                    }
                )
            }
            .pointerInput(bitmap) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = nextScale
                    if (nextScale > 1.02f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

@Composable
private fun FallbackExternalOpenView(data: FileAttachmentData) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp)
        )
        Text(
            text = data.effectiveDisplayName(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        if (data.effectiveDisplayName() != data.fileName) {
            Text(
                text = "原文件名: ${data.fileName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "类型: ${data.mimeType.ifBlank { "未知" }} · ${formatFileSize(data.fileSize)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "此格式暂不支持应用内直接渲染，可调用系统关联应用查看或分享给他人。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { shareFileAttachment(context, data) }) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("分享")
            }
            Button(onClick = { openFileAttachment(context, data) }) {
                Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("用其他应用打开")
            }
        }
    }
}

/**
 * 修改附件备注名（显示别名）弹窗。保留原始扩展名/原文件名。
 */
@Composable
fun RenameAttachmentDialog(
    data: FileAttachmentData,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var aliasInput by remember(data) { mutableStateOf(data.displayAlias.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改附件名称") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "原文件名：${data.fileName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = aliasInput,
                    onValueChange = { aliasInput = it },
                    label = { Text("自定义显示名称（留空则恢复原名）") },
                    placeholder = { Text(data.fileName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(aliasInput.trim().takeIf { it.isNotEmpty() })
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
