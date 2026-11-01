package com.litetask.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litetask.app.data.model.AMapRouteData
import com.litetask.app.data.model.AMapRouteSupport
import com.litetask.app.data.model.ComponentType
import com.litetask.app.data.model.FileAttachmentData
import com.litetask.app.data.model.LinkAndPassSupport
import com.litetask.app.data.model.LinkBookmarkData
import com.litetask.app.data.model.TaskComponent
import com.litetask.app.data.model.TaskComponentRegistry
import com.litetask.app.data.model.UserLocationEntity
import com.litetask.app.data.model.VoiceNoteData
import com.litetask.app.data.repository.AMapWeatherReport

/**
 * 语音备忘录增强动作包（异步转文字 + 由转写文本生成子任务）。
 */
data class VoiceNoteActions(
    val onTranscribe: (suspend (VoiceNoteData) -> Result<String>)? = null,
    val onGenerateSubTasks: ((String) -> Unit)? = null
)

data class OriginOption(
    val name: String,
    val address: String? = null,
    val lat: Double,
    val lng: Double,
    val locationType: String? = null
)

/**
 * 高德路线组件增强动作包（避免多层 Compose 组件反复透传大量独立回调）。
 */
data class AMapRouteActions(
    val frequentLocations: List<UserLocationEntity> = emptyList(),
    val onSearchLocations: (suspend (keyword: String, biasLocation: String?, city: String?) -> List<AMapRouteData>)? = null,
    val onGeocode: (suspend (String) -> AMapRouteData?)? = null,
    val onGetWeather: (suspend (String) -> Pair<String, String>?)? = null,
    val onGetWeatherForecast: (suspend (String) -> AMapWeatherReport?)? = null,
    val onCalculateRoute: (suspend (originLat: Double, originLng: Double, destLat: Double, destLng: Double, mode: String, strategy: Int) -> Pair<Int, Int>?)? = null,
    val onResolveCurrentLocation: (suspend () -> OriginOption?)? = null,
    val onCreateDepartureReminder: ((taskId: Long, triggerAtMillis: Long, label: String) -> Unit)? = null
) {
    companion object {
        fun fromLegacy(
            onGeocode: (suspend (String) -> AMapRouteData?)? = null,
            onSearchLocations: (suspend (String) -> List<AMapRouteData>)? = null,
            onGetWeather: (suspend (String) -> Pair<String, String>?)? = null
        ): AMapRouteActions = AMapRouteActions(
            onSearchLocations = onSearchLocations?.let { legacy ->
                { keyword, _, _ -> legacy(keyword) }
            },
            onGeocode = onGeocode,
            onGetWeather = onGetWeather
        )
    }
}

data class TaskComponentChipVisual(
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val iconTint: Color
)

/**
 * 统一的组件类型图标与色板解析。
 */
@Composable
fun rememberComponentChipVisual(
    type: ComponentType,
    isExpired: Boolean = false
): TaskComponentChipVisual {
    if (isExpired) {
        val mutedContainer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        val mutedText = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
        return TaskComponentChipVisual(
            icon = componentTypeIcon(type),
            containerColor = mutedContainer,
            contentColor = mutedText,
            iconTint = mutedText
        )
    }
    return when (type) {
        ComponentType.AMAP_ROUTE -> TaskComponentChipVisual(
            icon = Icons.Default.Place,
            containerColor = Color(0xFFE0F2FE),
            contentColor = Color(0xFF0369A1),
            iconTint = Color(0xFF0284C7)
        )
        ComponentType.FILE_ATTACHMENT -> TaskComponentChipVisual(
            icon = Icons.Default.AttachFile,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconTint = MaterialTheme.colorScheme.primary
        )
        ComponentType.LINK_BOOKMARK -> TaskComponentChipVisual(
            icon = Icons.Default.Link,
            containerColor = Color(0xFFEDE9FE),
            contentColor = Color(0xFF5B21B6),
            iconTint = Color(0xFF7C3AED)
        )
        ComponentType.ACCESS_PASS -> TaskComponentChipVisual(
            icon = Icons.Default.ConfirmationNumber,
            containerColor = Color(0xFFFEF3C7),
            contentColor = Color(0xFF92400E),
            iconTint = Color(0xFFD97706)
        )
        ComponentType.RECURRENCE_RULE -> TaskComponentChipVisual(
            icon = Icons.Default.Repeat,
            containerColor = Color(0xFFCCFBF1),
            contentColor = Color(0xFF115E59),
            iconTint = Color(0xFF0D9488)
        )
        ComponentType.VOICE_NOTE -> TaskComponentChipVisual(
            icon = Icons.Default.Mic,
            containerColor = Color(0xFFFFE4E6),
            contentColor = Color(0xFF9F1239),
            iconTint = Color(0xFFE11D48)
        )
        ComponentType.UNKNOWN -> TaskComponentChipVisual(
            icon = Icons.Default.Extension,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconTint = MaterialTheme.colorScheme.outline
        )
    }
}

fun componentTypeIcon(type: ComponentType): ImageVector = when (type) {
    ComponentType.AMAP_ROUTE -> Icons.Default.Place
    ComponentType.FILE_ATTACHMENT -> Icons.Default.AttachFile
    ComponentType.LINK_BOOKMARK -> Icons.Default.Link
    ComponentType.ACCESS_PASS -> Icons.Default.ConfirmationNumber
    ComponentType.RECURRENCE_RULE -> Icons.Default.Repeat
    ComponentType.VOICE_NOTE -> Icons.Default.Mic
    ComponentType.UNKNOWN -> Icons.Default.Extension
}

/**
 * 多地图降级导航跳转：高德 App -> 百度地图 -> 腾讯地图 -> 系统 geo URI -> 高德网页版。
 */
fun openAMapNavigation(context: Context, data: AMapRouteData) {
    val targets = AMapRouteSupport.buildMapNavigationTargets(data)
    for (target in targets) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target.uriString)).apply {
            if (!target.packageName.isNullOrBlank()) {
                setPackage(target.packageName)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            // 未安装或无法处理该 Scheme，自动尝试下一个降级方案
        }
    }
    Toast.makeText(context, "未找到可用的地图导航应用或浏览器", Toast.LENGTH_SHORT).show()
}

/**
 * 使用系统应用打开文件附件（优先使用本地托管 FileProvider URI）。
 */
fun openFileAttachment(context: Context, data: FileAttachmentData) {
    if (!com.litetask.app.util.AttachmentStorageHelper.isFileAccessible(context, data)) {
        Toast.makeText(context, "源文件已失效，请点击附件卡片进行重连", Toast.LENGTH_SHORT).show()
        return
    }
    com.litetask.app.util.AttachmentStorageHelper.openFileExternally(context, data)
}

/**
 * 调用系统分享面板分享文件附件。
 */
fun shareFileAttachment(context: Context, data: FileAttachmentData) {
    com.litetask.app.util.AttachmentStorageHelper.shareAttachment(context, data)
}

fun copyToClipboard(
    context: Context,
    label: String,
    text: String,
    toastMessage: String = "已复制到剪贴板：$text"
) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}

fun openLinkBookmark(context: Context, data: LinkBookmarkData) {
    val normalized = LinkAndPassSupport.normalizeLaunchUrl(data.url)
    if (normalized.isBlank()) {
        Toast.makeText(context, "链接地址无效", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "未找到可打开该链接的应用或浏览器", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 执行任务组件的主快捷动作（点击胶囊时触发）。
 */
fun executeTaskComponentPrimaryAction(context: Context, component: TaskComponent) {
    when (component) {
        is TaskComponent.AMapComponent -> openAMapNavigation(context, component.data)
        is TaskComponent.FileComponent -> openFileAttachment(context, component.data)
        is TaskComponent.LinkComponent -> openLinkBookmark(context, component.data)
        is TaskComponent.AccessPassComponent -> copyToClipboard(
            context = context,
            label = component.data.title,
            text = component.data.primaryCode,
            toastMessage = "已复制${LinkAndPassSupport.AccessPassType.fromCode(component.data.passType).label}：${component.data.primaryCode}"
        )
        is TaskComponent.RecurrenceComponent -> {
            val summary = com.litetask.app.data.model.RecurrenceEngine.formatRuleSummary(component.data)
            Toast.makeText(context, "周期重复：$summary（完成后自动生成下期）", Toast.LENGTH_SHORT).show()
        }
        is TaskComponent.VoiceNoteComponent -> {
            com.litetask.app.util.VoiceNotePlayerManager.togglePlayPause(
                context = context,
                key = "chip_${component.id}_${component.data.filePath}",
                data = component.data
            )
        }
        is TaskComponent.UnknownComponent -> {
            Toast.makeText(context, "当前版本暂不支持该组件", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * 共享的任务组件胶囊摘要条，供时间线卡片、截止视图、AI 确认弹窗统一渲染。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskComponentPreviewChipsRow(
    components: List<TaskComponent>,
    modifier: Modifier = Modifier,
    isExpired: Boolean = false,
    maxVisible: Int = 3,
    interactive: Boolean = true
) {
    if (components.isEmpty()) return
    val context = LocalContext.current
    var expanded by remember(components) { mutableStateOf(false) }
    val hasOverflow = components.size > maxVisible.coerceAtLeast(1)
    val visible = if (expanded || !hasOverflow) {
        components
    } else {
        components.take(maxVisible.coerceAtLeast(1))
    }
    val overflowCount = (components.size - visible.size).coerceAtLeast(0)

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        visible.forEach { component ->
            val visual = rememberComponentChipVisual(component.type, isExpired = isExpired)
            val label = TaskComponentRegistry.chipSummary(component)
            val shape = RoundedCornerShape(8.dp)

            Surface(
                color = visual.containerColor,
                shape = shape,
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (interactive && component !is TaskComponent.UnknownComponent) {
                            Modifier.clickable { executeTaskComponentPrimaryAction(context, component) }
                        } else {
                            Modifier
                        }
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = null,
                        tint = visual.iconTint,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = visual.contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp)
                    )
                }
            }
        }

        if (overflowCount > 0 || (hasOverflow && expanded)) {
            val badgeShape = RoundedCornerShape(8.dp)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
                shape = badgeShape,
                modifier = Modifier
                    .clip(badgeShape)
                    .clickable { expanded = !expanded }
            ) {
                Text(
                    text = if (expanded) "收起" else "+$overflowCount",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
