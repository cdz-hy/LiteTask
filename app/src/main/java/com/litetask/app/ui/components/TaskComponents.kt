package com.litetask.app.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.litetask.app.util.AttachmentStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Extension
import com.litetask.app.data.model.AMapRouteData
import com.litetask.app.data.model.AMapRouteSupport
import com.litetask.app.data.model.AMapRouteSupport.resolvedDrivingStrategy
import com.litetask.app.data.model.AMapRouteSupport.resolvedTravelMode
import com.litetask.app.data.model.AccessPassData
import com.litetask.app.data.model.FileAttachmentData
import com.litetask.app.data.model.LinkAndPassSupport.effectiveTitle
import com.litetask.app.data.model.LinkBookmarkData
import com.litetask.app.data.model.RecurrenceRuleData
import com.litetask.app.data.model.TaskComponent
import com.litetask.app.data.model.TaskComponentRegistry
import com.litetask.app.data.model.VoiceNoteData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 任务组件添加栏
 * 用于 AddTaskDialog 和 TaskDetailSheet，提供添加地图、文件等组件的入口
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskComponentAddBar(
    onAddAMap: (AMapRouteData) -> Unit,
    onAddFile: (FileAttachmentData) -> Unit,
    onAddLink: ((LinkBookmarkData) -> Unit)? = null,
    onAddAccessPass: ((AccessPassData) -> Unit)? = null,
    onAddRecurrence: ((RecurrenceRuleData) -> Unit)? = null,
    onAddVoiceNote: ((VoiceNoteData) -> Unit)? = null,
    existingRecurrenceRule: RecurrenceRuleData? = null,
    anchorStartTimeMs: Long = System.currentTimeMillis(),
    anchorDeadlineMs: Long = anchorStartTimeMs + 3600_000L,
    amapKey: String? = null,
    onGeocode: (suspend (String) -> AMapRouteData?)? = null,
    onSearchLocations: (suspend (String) -> List<AMapRouteData>)? = null,
    amapRouteActions: AMapRouteActions? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAMapDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showPassDialog by remember { mutableStateOf(false) }
    var showRecurrenceDialog by remember { mutableStateOf(false) }
    var showVoiceNoteDialog by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var pendingCameraUriStr by rememberSaveable { mutableStateOf<String?>(null) }

    fun importUris(uris: List<Uri>, cleanupTempCamera: Boolean = false) {
        if (uris.isEmpty()) return
        scope.launch {
            isImporting = true
            val imported = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    AttachmentStorageHelper.importFromUri(context, uri).also {
                        if (cleanupTempCamera) {
                            AttachmentStorageHelper.cleanupTempCameraFileIfMatches(context, uri)
                        }
                    }
                }
            }
            imported.forEach(onAddFile)
            isImporting = false
            if (imported.size > 1) {
                Toast.makeText(context, "已导入 ${imported.size} 个附件", Toast.LENGTH_SHORT).show()
            } else if (imported.isEmpty()) {
                Toast.makeText(context, "无法读取所选文件", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 多文件选择器（<=20MB 自动本地托管）
    val multiFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        importUris(uris)
    }

    // 相册多选图片
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 6)
    ) { uris ->
        importUris(uris)
    }

    // 拍照直传
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val rawUri = pendingCameraUriStr
        pendingCameraUriStr = null
        if (!rawUri.isNullOrBlank()) {
            val uri = Uri.parse(rawUri)
            if (success) {
                importUris(listOf(uri), cleanupTempCamera = true)
            } else {
                AttachmentStorageHelper.cleanupTempCameraFileIfMatches(context, uri)
            }
        }
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 4.dp else 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "add_route") {
            ComponentBarActionPill(
                label = "路线",
                icon = Icons.Default.Map,
                accentColor = Color(0xFF0284C7),
                onClick = { showAMapDialog = true }
            )
        }

        item(key = "add_file") {
            Box {
                ComponentBarActionPill(
                    label = if (isImporting) "导入..." else "附件",
                    icon = Icons.Default.AttachFile,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isLoading = isImporting,
                    isHighlighted = isImporting,
                    onClick = { if (!isImporting) showAttachmentMenu = true }
                )
                DropdownMenu(
                    expanded = showAttachmentMenu,
                    onDismissRequest = { showAttachmentMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("选择文件（可多选）") },
                        leadingIcon = { Icon(Icons.Default.AttachFile, null) },
                        onClick = {
                            showAttachmentMenu = false
                            multiFileLauncher.launch(arrayOf("*/*"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("拍照上传") },
                        leadingIcon = { Icon(Icons.Default.CameraAlt, null) },
                        onClick = {
                            showAttachmentMenu = false
                            val (camUri, _) = AttachmentStorageHelper.createTempCameraImageUri(context)
                            pendingCameraUriStr = camUri.toString()
                            try {
                                cameraLauncher.launch(camUri)
                            } catch (_: Exception) {
                                pendingCameraUriStr = null
                                AttachmentStorageHelper.cleanupTempCameraFileIfMatches(context, camUri)
                                Toast.makeText(context, "无法启动系统相机", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("相册图片（可多选）") },
                        leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) },
                        onClick = {
                            showAttachmentMenu = false
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }
            }
        }

        if (onAddLink != null) {
            item(key = "add_link") {
                ComponentBarActionPill(
                    label = "链接",
                    icon = Icons.Default.Link,
                    accentColor = Color(0xFF7C3AED),
                    onClick = { showLinkDialog = true }
                )
            }
        }

        if (onAddAccessPass != null) {
            item(key = "add_pass") {
                ComponentBarActionPill(
                    label = "凭证",
                    icon = Icons.Default.ConfirmationNumber,
                    accentColor = Color(0xFFD97706),
                    onClick = { showPassDialog = true }
                )
            }
        }

        if (onAddRecurrence != null) {
            item(key = "add_recurrence") {
                ComponentBarActionPill(
                    label = if (existingRecurrenceRule != null) "已设重复" else "重复",
                    icon = Icons.Default.Repeat,
                    accentColor = Color(0xFF0D9488),
                    isHighlighted = existingRecurrenceRule != null,
                    onClick = { showRecurrenceDialog = true }
                )
            }
        }

        if (onAddVoiceNote != null) {
            item(key = "add_voice") {
                ComponentBarActionPill(
                    label = "语音",
                    icon = Icons.Default.Mic,
                    accentColor = Color(0xFFE11D48),
                    onClick = { showVoiceNoteDialog = true }
                )
            }
        }
    }

    val effectiveRouteActions = remember(amapRouteActions, onGeocode, onSearchLocations) {
        amapRouteActions ?: AMapRouteActions.fromLegacy(
            onGeocode = onGeocode,
            onSearchLocations = onSearchLocations
        )
    }

    if (showAMapDialog) {
        AddAMapRouteDialog(
            onDismiss = { showAMapDialog = false },
            onConfirm = { data ->
                onAddAMap(data)
                showAMapDialog = false
            },
            amapKey = amapKey,
            routeActions = effectiveRouteActions
        )
    }

    if (showLinkDialog && onAddLink != null) {
        AddLinkBookmarkDialog(
            onDismiss = { showLinkDialog = false },
            onConfirm = { data ->
                onAddLink(data)
                showLinkDialog = false
            }
        )
    }

    if (showPassDialog && onAddAccessPass != null) {
        AddAccessPassDialog(
            onDismiss = { showPassDialog = false },
            onConfirm = { data ->
                onAddAccessPass(data)
                showPassDialog = false
            }
        )
    }

    if (showRecurrenceDialog && onAddRecurrence != null) {
        AddRecurrenceRuleDialog(
            initialData = existingRecurrenceRule,
            anchorStartTimeMs = anchorStartTimeMs,
            anchorDeadlineMs = anchorDeadlineMs,
            onDismiss = { showRecurrenceDialog = false },
            onConfirm = { data ->
                onAddRecurrence(data)
                showRecurrenceDialog = false
            }
        )
    }

    if (showVoiceNoteDialog && onAddVoiceNote != null) {
        RecordVoiceNoteDialog(
            onDismiss = { showVoiceNoteDialog = false },
            onConfirm = { data ->
                onAddVoiceNote(data)
                showVoiceNoteDialog = false
            }
        )
    }
}

/**
 * 添加 / 编辑高德地图路线对话框
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddAMapRouteDialog(
    initialData: AMapRouteData? = null,
    onDismiss: () -> Unit,
    onConfirm: (AMapRouteData) -> Unit,
    amapKey: String? = null,
    routeActions: AMapRouteActions = AMapRouteActions(),
    onGeocode: (suspend (String) -> AMapRouteData?)? = null,
    onSearchLocations: (suspend (String) -> List<AMapRouteData>)? = null
) {
    val effectiveActions = remember(routeActions, onGeocode, onSearchLocations) {
        if (routeActions.onGeocode != null || routeActions.onSearchLocations != null) {
            routeActions
        } else {
            AMapRouteActions.fromLegacy(
                onGeocode = onGeocode,
                onSearchLocations = onSearchLocations
            )
        }
    }

    var originName by remember(initialData) { mutableStateOf(initialData?.startName?.ifBlank { "我的位置" } ?: "我的位置") }
    var originLat by remember(initialData) { mutableStateOf(initialData?.startLat) }
    var originLng by remember(initialData) { mutableStateOf(initialData?.startLng) }

    var selectedMode by remember(initialData) { mutableStateOf(initialData?.resolvedTravelMode() ?: AMapRouteSupport.TravelMode.DRIVING) }
    var selectedStrategy by remember(initialData) { mutableStateOf(initialData?.strategy ?: 0) }

    var destination by remember(initialData) { mutableStateOf(initialData?.endName ?: "") }
    var selectedDest by remember(initialData) { mutableStateOf(initialData) }
    var previewDurationSec by remember(initialData) { mutableStateOf(initialData?.estimatedDurationSec) }
    var previewDistanceMeters by remember(initialData) { mutableStateOf(initialData?.estimatedDistanceMeters) }
    var isCalculatingEta by remember { mutableStateOf(false) }
    var isResolvingCurrentOrigin by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AMapRouteData>>(emptyList()) }
    var showDropdown by remember { mutableStateOf(false) }
    val validFrequentLocations = remember(effectiveActions.frequentLocations) {
        effectiveActions.frequentLocations
            .filter { it.latitude != 0.0 && it.longitude != 0.0 }
            .sortedByDescending { it.originCount + it.destinationCount }
            .take(6)
    }

    val scope = rememberCoroutineScope()

    // 自动计算实时路线耗时与距离预估
    LaunchedEffect(
        originLat,
        originLng,
        selectedDest?.endLat,
        selectedDest?.endLng,
        selectedMode,
        selectedStrategy,
        effectiveActions.onCalculateRoute
    ) {
        val dest = selectedDest ?: return@LaunchedEffect
        val calcFn = effectiveActions.onCalculateRoute ?: return@LaunchedEffect
        isCalculatingEta = true
        val resolvedOriginCoords = if (originLat != null && originLng != null) {
            originLat!! to originLng!!
        } else {
            effectiveActions.onResolveCurrentLocation?.invoke()?.let { it.lat to it.lng }
        }
        val etaResult = if (resolvedOriginCoords != null) {
            calcFn(
                resolvedOriginCoords.first,
                resolvedOriginCoords.second,
                dest.endLat,
                dest.endLng,
                selectedMode.code,
                selectedStrategy
            )
        } else null
        isCalculatingEta = false
        previewDurationSec = etaResult?.first
        previewDistanceMeters = etaResult?.second
    }

    // 防抖动地点搜索（带出发地或常用地坐标偏向）
    LaunchedEffect(destination, originLat, originLng) {
        val searchFn = effectiveActions.onSearchLocations
        val trimmed = destination.trim()
        val alreadySelected = selectedDest != null && selectedDest?.endName == trimmed
        if (!alreadySelected && trimmed.length > 1 && searchFn != null) {
            kotlinx.coroutines.delay(450)
            val biasLocation = when {
                originLat != null && originLng != null -> "${originLng},${originLat}"
                else -> validFrequentLocations.firstOrNull()?.let { "${it.longitude},${it.latitude}" }
            }
            val results = searchFn(trimmed, biasLocation, null)
            searchResults = results
            showDropdown = results.isNotEmpty()
        } else {
            showDropdown = false
        }
    }

    var textFieldSize by remember { mutableStateOf(IntSize.Zero) }
    val hasValidKey = !amapKey.isNullOrBlank() || effectiveActions.onGeocode != null || effectiveActions.onSearchLocations != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialData != null) "编辑路线导航" else "添加路线导航") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 1. 出发地选择器
                Text(
                    text = "出发地",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = originName == "我的位置" && originLat == null,
                            onClick = {
                                originName = "我的位置"
                                originLat = null
                                originLng = null
                            },
                            label = { Text("我的位置") }
                        )
                    }
                    if (effectiveActions.onResolveCurrentLocation != null) {
                        item {
                            FilterChip(
                                selected = originName == "当前定位" && originLat != null,
                                onClick = {
                                    scope.launch {
                                        isResolvingCurrentOrigin = true
                                        val current = effectiveActions.onResolveCurrentLocation.invoke()
                                        isResolvingCurrentOrigin = false
                                        if (current != null) {
                                            originName = "当前定位"
                                            originLat = current.lat
                                            originLng = current.lng
                                        } else {
                                            errorMsg = "未获取到定位权限或位置缓存"
                                        }
                                    }
                                },
                                leadingIcon = {
                                    if (isResolvingCurrentOrigin) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Default.MyLocation, null, Modifier.size(16.dp))
                                    }
                                },
                                label = { Text("当前定位") }
                            )
                        }
                    }
                    items(validFrequentLocations, key = { it.id }) { loc ->
                        val badgePrefix = when (loc.locationType) {
                            com.litetask.app.data.model.UserLocationEntity.TYPE_HOME -> "家 · "
                            com.litetask.app.data.model.UserLocationEntity.TYPE_WORK -> "公司 · "
                            com.litetask.app.data.model.UserLocationEntity.TYPE_SCHOOL -> "学校 · "
                            else -> ""
                        }
                        val isSelected = originName == loc.name &&
                            originLat == loc.latitude &&
                            originLng == loc.longitude
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                originName = loc.name
                                originLat = loc.latitude
                                originLng = loc.longitude
                            },
                            label = { Text("$badgePrefix${loc.name}", maxLines = 1) }
                        )
                    }
                }

                // 2. 目的地输入框 + POI 候选下拉
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = destination,
                        onValueChange = {
                            destination = it
                            if (selectedDest?.endName != it) {
                                selectedDest = null
                                previewDurationSec = null
                                previewDistanceMeters = null
                            }
                            errorMsg = ""
                        },
                        label = { Text("目的地 (例如: 中关村软件园)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { textFieldSize = it },
                        isError = errorMsg.isNotEmpty(),
                        supportingText = if (errorMsg.isNotEmpty()) {
                            { Text(errorMsg) }
                        } else null,
                        singleLine = true
                    )

                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false },
                        offset = androidx.compose.ui.unit.DpOffset(0.dp, 0.dp),
                        modifier = Modifier
                            .width(with(LocalDensity.current) { textFieldSize.width.toDp() })
                            .heightIn(max = 200.dp),
                        properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                    ) {
                        searchResults.forEach { result ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(result.endName, fontWeight = FontWeight.Bold)
                                        if (!result.endAddress.isNullOrBlank()) {
                                            Text(
                                                result.endAddress,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    destination = result.endName
                                    selectedDest = result
                                    showDropdown = false
                                    errorMsg = ""
                                }
                            )
                        }
                    }
                }

                // 3. 出行方式选择
                Text(
                    text = "出行方式",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AMapRouteSupport.TravelMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            leadingIcon = {
                                Icon(
                                    imageVector = travelModeIcon(mode),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(mode.label) }
                        )
                    }
                }

                // 4. 驾车路线策略（仅驾车模式显示）
                if (selectedMode == AMapRouteSupport.TravelMode.DRIVING) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AMapRouteSupport.DrivingStrategy.entries.forEach { st ->
                            FilterChip(
                                selected = selectedStrategy == st.code,
                                onClick = { selectedStrategy = st.code },
                                label = { Text(st.label, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // 5. 实时 ETA 预估预览
                val etaPreviewText = AMapRouteSupport.formatEtaSummary(previewDurationSec, previewDistanceMeters)
                if (isCalculatingEta || etaPreviewText != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isCalculatingEta) "正在计算预计路程时间…" else "预估路程：$etaPreviewText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = if (hasValidKey) {
                        "输入地点后可在下拉中精确选取，未选时确认将自动匹配坐标"
                    } else {
                        "未配置高德 API Key，请先在设置 → 高德地图配置中填写 Key"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedDest = destination.trim()
                    if (trimmedDest.isBlank()) {
                        errorMsg = "请输入目的地"
                        return@TextButton
                    }

                    val readyDest = selectedDest?.takeIf { it.endName == trimmedDest }
                    if (readyDest != null) {
                        onConfirm(
                            readyDest.copy(
                                startName = originName,
                                startLat = originLat,
                                startLng = originLng,
                                travelMode = selectedMode.code,
                                strategy = selectedStrategy,
                                estimatedDurationSec = previewDurationSec ?: readyDest.estimatedDurationSec,
                                estimatedDistanceMeters = previewDistanceMeters ?: readyDest.estimatedDistanceMeters
                            )
                        )
                        return@TextButton
                    }

                    if (effectiveActions.onGeocode == null && effectiveActions.onSearchLocations == null) {
                        errorMsg = "未配置高德 API Key 或搜索服务"
                        return@TextButton
                    }

                    scope.launch {
                        isLoading = true
                        val bias = when {
                            originLat != null && originLng != null -> "${originLng},${originLat}"
                            else -> validFrequentLocations.firstOrNull()?.let { "${it.longitude},${it.latitude}" }
                        }
                        val matched = effectiveActions.onSearchLocations?.invoke(trimmedDest, bias, null)?.firstOrNull()
                            ?: effectiveActions.onGeocode?.invoke(trimmedDest)
                        if (matched == null) {
                            isLoading = false
                            errorMsg = "未找到该地点，请检查关键词或高德 API Key"
                            return@launch
                        }

                        var etaSec: Int? = null
                        var distM: Int? = null
                        val calcFn = effectiveActions.onCalculateRoute
                        if (calcFn != null) {
                            val oCoords = if (originLat != null && originLng != null) {
                                originLat!! to originLng!!
                            } else {
                                effectiveActions.onResolveCurrentLocation?.invoke()?.let { it.lat to it.lng }
                            }
                            if (oCoords != null) {
                                val eta = calcFn(
                                    oCoords.first,
                                    oCoords.second,
                                    matched.endLat,
                                    matched.endLng,
                                    selectedMode.code,
                                    selectedStrategy
                                )
                                etaSec = eta?.first
                                distM = eta?.second
                            }
                        }

                        isLoading = false
                        onConfirm(
                            matched.copy(
                                startName = originName,
                                startLat = originLat,
                                startLng = originLng,
                                travelMode = selectedMode.code,
                                strategy = selectedStrategy,
                                estimatedDurationSec = etaSec,
                                estimatedDistanceMeters = distM
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

private fun travelModeIcon(mode: AMapRouteSupport.TravelMode) = when (mode) {
    AMapRouteSupport.TravelMode.DRIVING -> Icons.Default.DirectionsCar
    AMapRouteSupport.TravelMode.TRANSIT -> Icons.Default.DirectionsBus
    AMapRouteSupport.TravelMode.WALKING -> Icons.Default.DirectionsWalk
    AMapRouteSupport.TravelMode.BICYCLING -> Icons.Default.DirectionsBike
}
/**
 * 组件列表展示
 */
@Composable
fun TaskComponentList(
    components: List<TaskComponent>,
    onRemove: (TaskComponent) -> Unit,
    amapKey: String? = null,
    onGetWeather: (suspend (String) -> Pair<String, String>?)? = null,
    amapRouteActions: AMapRouteActions? = null,
    taskId: Long = 0L,
    taskStartTimeMillis: Long? = null,
    taskDeadlineMillis: Long? = null,
    onUpdateAMapComponent: ((TaskComponent.AMapComponent, AMapRouteData) -> Unit)? = null,
    onUpdateFileComponent: ((TaskComponent.FileComponent, FileAttachmentData) -> Unit)? = null,
    onUpdateLinkComponent: ((TaskComponent.LinkComponent, LinkBookmarkData) -> Unit)? = null,
    onUpdateAccessPassComponent: ((TaskComponent.AccessPassComponent, AccessPassData) -> Unit)? = null,
    onUpdateRecurrenceComponent: ((TaskComponent.RecurrenceComponent, RecurrenceRuleData) -> Unit)? = null,
    onUpdateVoiceNoteComponent: ((TaskComponent.VoiceNoteComponent, VoiceNoteData) -> Unit)? = null,
    voiceNoteActions: VoiceNoteActions? = null,
    modifier: Modifier = Modifier
) {
    if (components.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voicePlaybackState by com.litetask.app.util.VoiceNotePlayerManager.playbackState.collectAsState()
    var componentToDelete by remember { mutableStateOf<TaskComponent?>(null) }
    var editingAMapComponent by remember { mutableStateOf<TaskComponent.AMapComponent?>(null) }
    var editingLinkComponent by remember { mutableStateOf<TaskComponent.LinkComponent?>(null) }
    var editingPassComponent by remember { mutableStateOf<TaskComponent.AccessPassComponent?>(null) }
    var editingRecurrenceComponent by remember { mutableStateOf<TaskComponent.RecurrenceComponent?>(null) }
    var previewingFileComponent by remember { mutableStateOf<TaskComponent.FileComponent?>(null) }
    var renamingFileComponent by remember { mutableStateOf<TaskComponent.FileComponent?>(null) }
    var relinkTargetComponent by remember { mutableStateOf<TaskComponent.FileComponent?>(null) }

    val relinkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val target = relinkTargetComponent
        relinkTargetComponent = null
        if (uri != null && target != null) {
            scope.launch {
                val relinked = withContext(Dispatchers.IO) {
                    AttachmentStorageHelper.relinkAttachment(context, target.data, uri)
                }
                if (relinked != null) {
                    onUpdateFileComponent?.invoke(target, relinked)
                    if (previewingFileComponent?.id == target.id) {
                        previewingFileComponent = target.copy(data = relinked)
                    }
                    Toast.makeText(context, "已重新关联文件", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "重新关联失败，请重试", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val effectiveRouteActions = remember(amapRouteActions, onGetWeather) {
        amapRouteActions ?: AMapRouteActions.fromLegacy(onGetWeather = onGetWeather)
    }

    Column(modifier = modifier) {
        components.forEach { component ->
            when (component) {
                is TaskComponent.AMapComponent -> AMapComponentCard(
                    data = component.data,
                    apiKey = amapKey,
                    onGetWeather = onGetWeather,
                    routeActions = effectiveRouteActions,
                    taskId = if (component.taskId > 0L) component.taskId else taskId,
                    taskStartTimeMillis = taskStartTimeMillis,
                    onEdit = if (onUpdateAMapComponent != null) {
                        { editingAMapComponent = component }
                    } else null,
                    onRemove = { componentToDelete = component }
                )
                is TaskComponent.FileComponent -> FileComponentCard(
                    data = component.data,
                    onPreview = { previewingFileComponent = component },
                    onRename = onUpdateFileComponent?.let {
                        { renamingFileComponent = component }
                    },
                    onRelink = onUpdateFileComponent?.let {
                        {
                            relinkTargetComponent = component
                            relinkLauncher.launch(arrayOf("*/*"))
                        }
                    },
                    onRemove = { componentToDelete = component }
                )
                is TaskComponent.LinkComponent -> LinkBookmarkCard(
                    data = component.data,
                    onEdit = onUpdateLinkComponent?.let {
                        { editingLinkComponent = component }
                    },
                    onRemove = { componentToDelete = component }
                )
                is TaskComponent.AccessPassComponent -> AccessPassCard(
                    data = component.data,
                    onEdit = onUpdateAccessPassComponent?.let {
                        { editingPassComponent = component }
                    },
                    onRemove = { componentToDelete = component }
                )
                is TaskComponent.RecurrenceComponent -> RecurrenceRuleCard(
                    data = component.data,
                    onEdit = onUpdateRecurrenceComponent?.let {
                        { editingRecurrenceComponent = component }
                    },
                    onTogglePause = onUpdateRecurrenceComponent?.let { updateFn ->
                        { updateFn(component, component.data.copy(isPaused = !component.data.isPaused)) }
                    },
                    onStopFuture = { componentToDelete = component },
                    onRemove = { componentToDelete = component }
                )
                is TaskComponent.VoiceNoteComponent -> VoiceNoteCard(
                    componentKey = "vn_${component.id}_${component.data.filePath}",
                    data = component.data,
                    playbackState = voicePlaybackState,
                    onUpdateData = onUpdateVoiceNoteComponent?.let { updateFn ->
                        { updated -> updateFn(component, updated) }
                    },
                    onTranscribe = voiceNoteActions?.onTranscribe,
                    onGenerateSubTasks = voiceNoteActions?.onGenerateSubTasks,
                    onRemove = { componentToDelete = component }
                )
                is TaskComponent.UnknownComponent -> UnknownComponentCard(
                    data = component,
                    onRemove = { componentToDelete = component }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    editingAMapComponent?.let { target ->
        AddAMapRouteDialog(
            initialData = target.data,
            onDismiss = { editingAMapComponent = null },
            onConfirm = { updatedData ->
                onUpdateAMapComponent?.invoke(target, updatedData)
                editingAMapComponent = null
            },
            amapKey = amapKey,
            routeActions = effectiveRouteActions
        )
    }

    previewingFileComponent?.let { target ->
        val latest = components.filterIsInstance<TaskComponent.FileComponent>()
            .firstOrNull { it.id == target.id } ?: target
        AttachmentViewerDialog(
            data = latest.data,
            onDismiss = { previewingFileComponent = null },
            onRequestRename = onUpdateFileComponent?.let {
                { renamingFileComponent = latest }
            },
            onRequestRelink = onUpdateFileComponent?.let {
                {
                    relinkTargetComponent = latest
                    relinkLauncher.launch(arrayOf("*/*"))
                }
            }
        )
    }

    renamingFileComponent?.let { target ->
        val latest = components.filterIsInstance<TaskComponent.FileComponent>()
            .firstOrNull { it.id == target.id } ?: target
        RenameAttachmentDialog(
            data = latest.data,
            onDismiss = { renamingFileComponent = null },
            onConfirm = { newAlias ->
                val updatedData = latest.data.copy(displayAlias = newAlias)
                onUpdateFileComponent?.invoke(latest, updatedData)
                if (previewingFileComponent?.id == latest.id) {
                    previewingFileComponent = latest.copy(data = updatedData)
                }
                renamingFileComponent = null
            }
        )
    }

    editingLinkComponent?.let { target ->
        AddLinkBookmarkDialog(
            initialData = target.data,
            onDismiss = { editingLinkComponent = null },
            onConfirm = { updatedData ->
                onUpdateLinkComponent?.invoke(target, updatedData)
                editingLinkComponent = null
            }
        )
    }

    editingPassComponent?.let { target ->
        AddAccessPassDialog(
            initialData = target.data,
            onDismiss = { editingPassComponent = null },
            onConfirm = { updatedData ->
                onUpdateAccessPassComponent?.invoke(target, updatedData)
                editingPassComponent = null
            }
        )
    }

    editingRecurrenceComponent?.let { target ->
        val fallbackStart = taskStartTimeMillis ?: System.currentTimeMillis()
        val fallbackDeadline = taskDeadlineMillis?.takeIf { it >= fallbackStart } ?: (fallbackStart + 3600_000L)
        AddRecurrenceRuleDialog(
            initialData = target.data,
            anchorStartTimeMs = fallbackStart,
            anchorDeadlineMs = fallbackDeadline,
            onDismiss = { editingRecurrenceComponent = null },
            onConfirm = { updatedData ->
                onUpdateRecurrenceComponent?.invoke(target, updatedData)
                editingRecurrenceComponent = null
            }
        )
    }

    // 二次确认对话框
    if (componentToDelete != null) {
        val title = when (componentToDelete) {
            is TaskComponent.AMapComponent -> "删除路线"
            is TaskComponent.FileComponent -> "删除附件"
            is TaskComponent.LinkComponent -> "删除链接"
            is TaskComponent.AccessPassComponent -> "删除凭证卡"
            is TaskComponent.RecurrenceComponent -> "停止未来重复"
            is TaskComponent.VoiceNoteComponent -> "删除语音备忘"
            is TaskComponent.UnknownComponent -> "移除不兼容组件"
            null -> ""
        }
        val message = when (val c = componentToDelete) {
            is TaskComponent.AMapComponent -> "确定要删除通往“${c.data.endName}”的路线吗？"
            is TaskComponent.FileComponent -> "确定要删除附件“${c.data.effectiveDisplayName()}”吗？"
            is TaskComponent.LinkComponent -> "确定要移除链接“${c.data.effectiveTitle()}”吗？"
            is TaskComponent.AccessPassComponent -> "确定要删除凭证“${c.data.title} (${c.data.primaryCode})”吗？"
            is TaskComponent.RecurrenceComponent -> "确定要移除此任务上的周期规则（仅停止生成未来任务，不影响当前及过往记录）吗？"
            is TaskComponent.VoiceNoteComponent -> "确定要删除这条语音备忘吗？（若不再被其他任务引用，将同步清理本地音频文件）"
            is TaskComponent.UnknownComponent -> "该组件（${c.rawType}）来自其他版本或暂不受支持，确定移除吗？"
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
 * 高德地图卡片（支持出发地/方式/策略展示、任务日期预报天气、实时 ETA 与一键设出发提醒）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AMapComponentCard(
    data: AMapRouteData,
    apiKey: String? = null,
    onGetWeather: (suspend (String) -> Pair<String, String>?)? = null,
    routeActions: AMapRouteActions = AMapRouteActions(),
    taskId: Long = 0L,
    taskStartTimeMillis: Long? = null,
    onEdit: (() -> Unit)? = null,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    var weatherBadgeText by remember(data.adcode, taskStartTimeMillis) { mutableStateOf<String?>(null) }
    var liveDurationSec by remember(data) { mutableStateOf(data.estimatedDurationSec) }
    var liveDistanceMeters by remember(data) { mutableStateOf(data.estimatedDistanceMeters) }
    var reminderCreated by remember(data, taskStartTimeMillis) { mutableStateOf(false) }

    val travelMode = remember(data.travelMode) { data.resolvedTravelMode() }
    val drivingStrategy = remember(data.strategy) { data.resolvedDrivingStrategy() }

    // 优先匹配任务目标日期天气预报，超出预报窗口自动降级到实况天气
    LaunchedEffect(data.adcode, taskStartTimeMillis, apiKey, routeActions.onGetWeatherForecast, onGetWeather) {
        val adcode = data.adcode?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val forecastFn = routeActions.onGetWeatherForecast
        val targetMs = taskStartTimeMillis?.takeIf { it > 0L }
        var resolved: String? = null
        if (!apiKey.isNullOrBlank() && forecastFn != null && targetMs != null) {
            val report = forecastFn(adcode)
            val matched = report?.let { AMapRouteSupport.matchForecastForTaskDate(it, targetMs) }
            if (matched != null) {
                resolved = "${matched.dayLabel} ${matched.weather} ${matched.tempRange}".trim()
            }
        }
        if (resolved == null && !apiKey.isNullOrBlank()) {
            val liveFn = routeActions.onGetWeather ?: onGetWeather
            val live = liveFn?.invoke(adcode)
            if (live != null) {
                resolved = "${live.first} ${live.second}°C"
            }
        }
        weatherBadgeText = resolved
    }

    // 缺少持久化 ETA 且具备计算能力时自动懒加载实时耗时
    LaunchedEffect(
        data.endLat,
        data.endLng,
        data.startLat,
        data.startLng,
        travelMode,
        drivingStrategy,
        routeActions.onCalculateRoute
    ) {
        if (liveDurationSec != null) return@LaunchedEffect
        val calcFn = routeActions.onCalculateRoute ?: return@LaunchedEffect
        val originCoords = if (data.startLat != null && data.startLng != null && data.startLat != 0.0) {
            data.startLat to data.startLng
        } else {
            routeActions.onResolveCurrentLocation?.invoke()?.let { it.lat to it.lng }
        } ?: return@LaunchedEffect

        val eta = calcFn(
            originCoords.first,
            originCoords.second,
            data.endLat,
            data.endLng,
            travelMode.code,
            drivingStrategy.code
        )
        if (eta != null) {
            liveDurationSec = eta.first
            liveDistanceMeters = eta.second
        }
    }

    val staticMapUrl = if (!apiKey.isNullOrBlank()) {
        "https://restapi.amap.com/v3/staticmap?location=${data.endLng},${data.endLat}&zoom=15&size=600*300&markers=mid,0xFF0000,A:${data.endLng},${data.endLat}&key=$apiKey"
    } else null

    val nowMillis = System.currentTimeMillis()
    val suggestedDepartureMillis = remember(taskStartTimeMillis, liveDurationSec) {
        taskStartTimeMillis?.let {
            AMapRouteSupport.calculateSuggestedDepartureMillis(it, liveDurationSec, bufferMinutes = 10)
        }?.takeIf { it > nowMillis + 60_000L }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openAMapNavigation(context, data) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // 地图缩略图
            Box(
                modifier = Modifier
                    .width(108.dp)
                    .fillMaxHeight()
                    .heightIn(min = 112.dp)
                    .background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                if (staticMapUrl != null) {
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
                    .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 标题与编辑/删除操作
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (data.startName.isNotBlank() && data.startName != "我的位置") {
                                "${data.startName} → ${data.endName}"
                            } else {
                                data.endName
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (!data.endAddress.isNullOrBlank()) {
                            Text(
                                text = data.endAddress,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    if (onEdit != null) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Edit, "编辑路线", modifier = Modifier.size(15.dp))
                        }
                    }
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.Close, "删除组件", modifier = Modifier.size(15.dp))
                    }
                }

                // 方式 + ETA + 天气胶囊
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    val modeText = if (travelMode == AMapRouteSupport.TravelMode.DRIVING && data.strategy != 0) {
                        "${travelMode.label}·${drivingStrategy.label}"
                    } else {
                        travelMode.label
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = modeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    val etaText = AMapRouteSupport.formatEtaSummary(liveDurationSec, liveDistanceMeters)
                    if (etaText != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = etaText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (weatherBadgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = weatherBadgeText!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // 底部快捷动作：设出发提醒 & 点击导航
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, end = 6.dp)
                ) {
                    val canOfferReminder = taskId > 0L &&
                        suggestedDepartureMillis != null &&
                        routeActions.onCreateDepartureReminder != null &&
                        !reminderCreated
                    if (canOfferReminder && suggestedDepartureMillis != null) {
                        val depTimeStr = remember(suggestedDepartureMillis) {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(suggestedDepartureMillis))
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    val etaLabel = AMapRouteSupport.formatDurationShort(liveDurationSec) ?: "预估行程"
                                    routeActions.onCreateDepartureReminder?.invoke(
                                        taskId,
                                        suggestedDepartureMillis,
                                        "准备出发前往${data.endName} ($etaLabel)"
                                    )
                                    reminderCreated = true
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "设${depTimeStr}出发提醒",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else if (reminderCreated) {
                        Text(
                            text = "已设出发提醒",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Text(
                        text = "点击导航",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 文件组件卡片（支持应用内预览、受管状态/失效预警、重命名别名、重连与系统分享）
 */
@Composable
fun FileComponentCard(
    data: FileAttachmentData,
    onPreview: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onRelink: (() -> Unit)? = null,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val isImage = remember(data.fileName, data.mimeType) {
        data.mimeType.startsWith("image/") ||
            data.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("jpg", "jpeg", "png", "webp")
    }
    var isAccessible by remember(data) { mutableStateOf(true) }
    LaunchedEffect(data) {
        isAccessible = withContext(Dispatchers.IO) {
            AttachmentStorageHelper.isFileAccessible(context, data)
        }
    }

    val handlePrimaryClick: () -> Unit = {
        if (!isAccessible && onRelink != null) {
            onRelink()
        } else if (onPreview != null) {
            onPreview()
        } else {
            openFileAttachment(context, data)
        }
    }

    val storageTag = if (data.isManagedCopy) "本地托管" else "外部链接"
    val secondaryLine = buildString {
        append(formatFileSize(data.fileSize))
        append(" · ")
        if (!isAccessible) {
            append("源文件已失效")
        } else {
            append(storageTag)
            if (!data.displayAlias.isNullOrBlank() && data.displayAlias != data.fileName) {
                append(" · 原名: ${data.fileName}")
            }
        }
    }

    if (isImage && isAccessible) {
        val imageModel: Any = remember(data) {
            val path = data.managedFilePath
            if (!path.isNullOrBlank()) java.io.File(path).takeIf { it.exists() } ?: data.fileUri else data.fileUri
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .clickable(onClick = handlePrimaryClick),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .background(Color.LightGray)
                ) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = data.effectiveDisplayName(),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = data.effectiveDisplayName(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = secondaryLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { shareFileAttachment(context, data) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Share, "分享", modifier = Modifier.size(16.dp))
                }
                if (onRename != null) {
                    IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, "修改名称", modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "删除", modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(4.dp))
            }
        }
    } else {
        val icon = when {
            !isAccessible -> Icons.Default.BrokenImage
            isImage -> Icons.Default.Image
            data.mimeType == "application/pdf" || data.fileName.endsWith(".pdf", ignoreCase = true) -> Icons.Default.PictureAsPdf
            data.mimeType.contains("word") || data.fileName.endsWith(".doc", ignoreCase = true) || data.fileName.endsWith(".docx", ignoreCase = true) -> Icons.Default.Description
            data.mimeType.contains("text") || data.fileName.endsWith(".txt", ignoreCase = true) || data.fileName.endsWith(".md", ignoreCase = true) -> Icons.Default.Description
            else -> Icons.Default.InsertDriveFile
        }
        val containerColor = if (isAccessible) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        }
        val iconTint = if (isAccessible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

        Surface(
            color = containerColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = handlePrimaryClick)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = iconTint)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = data.effectiveDisplayName(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (!isAccessible && onRelink != null) "$secondaryLine · 点击重连" else secondaryLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAccessible) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!isAccessible && onRelink != null) {
                    IconButton(onClick = onRelink, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = "重连文件",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else if (isAccessible) {
                    IconButton(
                        onClick = { shareFileAttachment(context, data) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.Share, "分享", modifier = Modifier.size(16.dp))
                    }
                }
                if (onRename != null) {
                    IconButton(onClick = onRename, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Edit, "修改名称", modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, "删除", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun UnknownComponentCard(
    data: TaskComponent.UnknownComponent,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "暂不支持的扩展组件",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "类型标识: ${data.rawType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ComponentBarActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isLoading: Boolean = false,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = if (isHighlighted) {
        accentColor.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    }
    val borderColor = if (isHighlighted) {
        accentColor.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }
    val labelColor = if (isHighlighted) accentColor else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(0.6.dp, borderColor),
        modifier = Modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = accentColor
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = labelColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

fun formatFileSize(size: Long): String = TaskComponentRegistry.formatSizeSafe(size)
