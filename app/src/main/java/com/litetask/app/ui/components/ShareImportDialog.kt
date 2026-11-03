package com.litetask.app.ui.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.litetask.app.data.model.AccessPassData
import com.litetask.app.data.model.LinkAndPassSupport
import com.litetask.app.data.model.LinkBookmarkData
import com.litetask.app.data.model.Task
import com.litetask.app.util.IncomingSharePayload
import com.litetask.app.util.ShareIntentParser

/**
 * 接收系统 ACTION_SEND / ACTION_SEND_MULTIPLE 分享落地选择器。
 * 展示识别到的链接卡片、凭证码与文件，支持：
 * 1. 交给 AI 智能创建任务（支持图文/附件多模态识别并自动挂载）
 * 2. 手动新建任务并自动带入已识别组件
 * 3. 搜索并直接挂载到已有待办任务
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareImportDialog(
    payload: IncomingSharePayload,
    activeTasks: List<Task>,
    isBusy: Boolean = false,
    onDismiss: () -> Unit,
    onAiCreate: (combinedText: String, imageUris: List<Uri>, extraAttachmentUris: List<Uri>) -> Unit,
    onOpenManualAdd: (
        suggestedTitle: String,
        suggestedDescription: String,
        links: List<LinkBookmarkData>,
        passes: List<AccessPassData>,
        uris: List<Uri>
    ) -> Unit,
    onAttachToTask: (
        taskId: Long,
        links: List<LinkBookmarkData>,
        passes: List<AccessPassData>,
        uris: List<Uri>
    ) -> Unit
) {
    val context = LocalContext.current
    var editedText by remember(payload) { mutableStateOf(payload.combinedText) }
    var taskQuery by remember { mutableStateOf("") }

    val detectedLinks = remember(editedText) {
        LinkAndPassSupport.extractAllLinksFromText(editedText)
    }
    val detectedPasses = remember(editedText) {
        LinkAndPassSupport.extractPassesFallbackFromText(editedText)
    }
    val resolvedFiles = remember(payload.sharedUris) {
        payload.sharedUris.map { uri ->
            Triple(
                uri,
                ShareIntentParser.resolveDisplayName(context, uri),
                ShareIntentParser.isImageUri(context, uri)
            )
        }
    }
    val imageUris = remember(resolvedFiles) { resolvedFiles.filter { it.third }.map { it.first } }
    val nonImageUris = remember(resolvedFiles) { resolvedFiles.filter { !it.third }.map { it.first } }

    val filteredTasks = remember(activeTasks, taskQuery) {
        val q = taskQuery.trim().lowercase()
        if (q.isEmpty()) activeTasks else activeTasks.filter {
            it.title.lowercase().contains(q) || (it.description?.lowercase()?.contains(q) == true)
        }
    }

    val suggestedTitle = remember(payload, detectedLinks, resolvedFiles) {
        payload.sharedSubject?.takeIf { it.isNotBlank() }
            ?: detectedLinks.firstOrNull()?.title?.takeIf { it.isNotBlank() }
            ?: detectedPasses.firstOrNull()?.title?.takeIf { it.isNotBlank() }
            ?: resolvedFiles.firstOrNull()?.second
            ?: ""
    }

    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "保存到 LiteTask",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, enabled = !isBusy) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editedText,
                        onValueChange = { editedText = it },
                        label = { Text("分享内容 / 补充指令") },
                        placeholder = { Text("可补充截止时间或需求，例如：明晚8点前完成") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    if (detectedLinks.isNotEmpty() || detectedPasses.isNotEmpty() || resolvedFiles.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "已自动提取组件预览",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    detectedLinks.forEach { link ->
                                        val meta = LinkAndPassSupport.resolveBrandMeta(link.url, link.linkCategory)
                                        AssistChip(
                                            onClick = {},
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Link,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = Color(meta.accentHex)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    link.title ?: LinkAndPassSupport.extractHostDisplay(link.url),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        )
                                    }
                                    detectedPasses.forEach { pass ->
                                        AssistChip(
                                            onClick = {},
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.ConfirmationNumber,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = { Text("${pass.title}: ${pass.primaryCode}", maxLines = 1) }
                                        )
                                    }
                                    resolvedFiles.forEach { (_, name, _) ->
                                        AssistChip(
                                            onClick = {},
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.AttachFile,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onAiCreate(editedText, imageUris, nonImageUris) },
                            enabled = !isBusy && (editedText.isNotBlank() || payload.sharedUris.isNotEmpty()),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI 创建任务", maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                onOpenManualAdd(
                                    suggestedTitle,
                                    editedText,
                                    detectedLinks,
                                    detectedPasses,
                                    payload.sharedUris
                                )
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("手动带入新建", maxLines = 1)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text(
                        text = "或直接挂载到已有待办",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (activeTasks.isEmpty()) {
                        Text(
                            text = "暂无未完成的待办任务，可使用上方按钮新建任务",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        OutlinedTextField(
                            value = taskQuery,
                            onValueChange = { taskQuery = it },
                            placeholder = { Text("搜索现有待办标题...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 72.dp, max = 168.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredTasks, key = { it.id }) { task ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isBusy) {
                                            onAttachToTask(task.id, detectedLinks, detectedPasses, payload.sharedUris)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = task.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "挂载",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isBusy) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
