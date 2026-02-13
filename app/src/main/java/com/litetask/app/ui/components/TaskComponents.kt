package com.litetask.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.litetask.app.data.model.AMapRouteData
import com.litetask.app.data.model.FileAttachmentData
import com.litetask.app.data.model.TaskComponent

/**
 * 任务组件添加栏
 * 用于 AddTaskDialog 和 TaskDetailScreen，提供添加地图、文件等组件的入口
 */
@Composable
fun TaskComponentAddBar(
    onAddAMap: (AMapRouteData) -> Unit,
    onAddFile: (FileAttachmentData) -> Unit,
    amapKey: String? = null,
    onGeocode: (suspend (String) -> AMapRouteData?)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAMapDialog by remember { mutableStateOf(false) }

    // 文件选择器
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // 获取持久化权限
            try {
                val takeFlags: Int = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                // 部分文件管理器可能不支持持久化权限，这里做容错
                e.printStackTrace()
            }

            // 获取文件信息
            val cursor = context.contentResolver.query(it, null, null, null, null)
            val nameIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.SIZE)
            
            cursor?.moveToFirst()
            val fileName = cursor?.getString(nameIndex ?: 0) ?: "Unknown File"
            val fileSize = cursor?.getLong(sizeIndex ?: 0) ?: 0L
            val mimeType = context.contentResolver.getType(it) ?: "*/*"
            cursor?.close()

            onAddFile(
                FileAttachmentData(
                    fileName = fileName,
                    fileUri = it.toString(),
                    mimeType = mimeType,
                    fileSize = fileSize
                )
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 地图按钮
        AssistChip(
            onClick = { showAMapDialog = true },
            label = { Text("添加路线") },
            leadingIcon = { Icon(Icons.Default.Map, null) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        // 附件按钮
        AssistChip(
            onClick = { fileLauncher.launch(arrayOf("*/*")) },
            label = { Text("添加附件") },
            leadingIcon = { Icon(Icons.Default.AttachFile, null) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }

    if (showAMapDialog) {
        AddAMapRouteDialog(
            onDismiss = { showAMapDialog = false },
            onConfirm = { data ->
                onAddAMap(data)
                showAMapDialog = false
            },
            onGeocode = onGeocode
        )
    }
}

/**
 * 添加高德地图路线对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAMapRouteDialog(
    onDismiss: () -> Unit,
    onConfirm: (AMapRouteData) -> Unit,
    onGeocode: (suspend (String) -> AMapRouteData?)? = null
) {
    var destination by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加路线导航") },
        text = {
            Column {
                OutlinedTextField(
                    value = destination,
                    onValueChange = { 
                        destination = it
                        errorMsg = ""
                    },
                    label = { Text("目的地 (例如: 北京市)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMsg.isNotEmpty(),
                    supportingText = if (errorMsg.isNotEmpty()) { { Text(errorMsg) } } else null
                )
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                Text(
                    text = if (onGeocode != null) "输入地点名称即可自动搜索坐标" else "注：演示模式，将使用模拟坐标",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (destination.isBlank()) {
                        errorMsg = "请输入目的地"
                        return@TextButton
                    }
                    
                    if (onGeocode != null) {
                        scope.launch {
                            isLoading = true
                            val result = onGeocode(destination)
                            isLoading = false
                            if (result != null) {
                                onConfirm(result)
                            } else {
                                errorMsg = "未找到该地点或 API Key 无效"
                            }
                        }
                    } else {
                        // 模拟数据
                        onConfirm(
                            AMapRouteData(
                                startName = "我的位置",
                                endName = destination,
                                endAddress = "模拟地址(北京市)",
                                endLat = 39.9042,
                                endLng = 116.4074
                            )
                        )
                    }
                },
                enabled = !isLoading
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 组件列表展示
 */
@Composable
fun TaskComponentList(
    components: List<TaskComponent>,
    onRemove: (TaskComponent) -> Unit,
    amapKey: String? = null,
    modifier: Modifier = Modifier
) {
    if (components.isEmpty()) return

    var componentToDelete by remember { mutableStateOf<TaskComponent?>(null) }

    Column(modifier = modifier) {
        components.forEach { component ->
            when (component) {
                is TaskComponent.AMapComponent -> AMapComponentCard(component.data, amapKey) { 
                    componentToDelete = component 
                }
                is TaskComponent.FileComponent -> FileComponentCard(component.data) { 
                    componentToDelete = component 
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // 二次确认对话框
    if (componentToDelete != null) {
        val title = when (componentToDelete) {
            is TaskComponent.AMapComponent -> "删除路线"
            is TaskComponent.FileComponent -> "删除附件"
            null -> ""
        }
        val message = when (val c = componentToDelete) {
            is TaskComponent.AMapComponent -> "确定要删除通往“${c.data.endName}”的路线吗？"
            is TaskComponent.FileComponent -> "确定要删除附件“${c.data.fileName}”吗？"
            null -> ""
        }

        AlertDialog(
            onDismissRequest = { componentToDelete = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        componentToDelete?.let { onRemove(it) }
                        componentToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { componentToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 高德地图卡片
 */
@Composable
fun AMapComponentCard(
    data: AMapRouteData,
    apiKey: String? = null,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    // 高德静态地图 URL
    val staticMapUrl = if (!apiKey.isNullOrBlank()) {
        "https://restapi.amap.com/v3/staticmap?location=${data.endLng},${data.endLat}&zoom=14&size=600*300&markers=mid,0xFF0000,A:${data.endLng},${data.endLat}&key=$apiKey"
    } else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clickable {
                // 唤起高德地图 App
                val uri = android.net.Uri.parse("androidamap://navi?sourceApplication=LiteTask&lat=${data.endLat}&lon=${data.endLng}&dev=0&style=2")
                val intent = android.content.Intent("android.intent.action.VIEW", uri)
                intent.setPackage("com.autonavi.minimap")
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "未安装高德地图", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 地图缩略图
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                if (staticMapUrl != null) {
                    androidx.compose.ui.layout.ContentScale
                    AsyncImage(
                        model = staticMapUrl,
                        contentDescription = "Map",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Map, null, tint = Color.Gray)
                }
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "导航至：${data.endName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "点击一键导航",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, "删除组件", modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * 文件组件卡片
 */
@Composable
fun FileComponentCard(
    data: FileAttachmentData,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    
    AssistChip(
        onClick = {
            try {
                val uri = android.net.Uri.parse(data.fileUri)
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, data.mimeType)
                    flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "无法打开此文件", android.widget.Toast.LENGTH_SHORT).show()
            }
        },
        label = { 
            Text(
                text = "${data.fileName} (${formatFileSize(data.fileSize)})",
                maxLines = 1
            ) 
        },
        leadingIcon = { 
            Icon(Icons.Default.AttachFile, null, Modifier.size(16.dp)) 
        },
        trailingIcon = {
            IconButton(onClick = onRemove, modifier = Modifier.size(16.dp)) {
                Icon(Icons.Default.Close, "删除", modifier = Modifier.size(12.dp))
            }
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
