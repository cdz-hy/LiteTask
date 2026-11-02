package com.litetask.app.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litetask.app.data.model.AccessPassData
import com.litetask.app.data.model.LinkAndPassSupport
import com.litetask.app.data.model.LinkAndPassSupport.effectiveTitle
import com.litetask.app.data.model.LinkBookmarkData

/**
 * 网页/深链接书签卡片（带品牌识别徽章、直达与一键复制）
 */
@Composable
fun LinkBookmarkCard(
    data: LinkBookmarkData,
    onEdit: (() -> Unit)? = null,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val brand = remember(data.url, data.linkCategory) {
        LinkAndPassSupport.resolveBrandMeta(data.url, data.linkCategory)
    }
    val hostText = remember(data.url) {
        LinkAndPassSupport.extractHostDisplay(data.url).ifBlank { data.url }
    }
    val brandColor = remember(brand.accentHex) { Color(brand.accentHex) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { openLinkBookmark(context, data) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(brandColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = brand.badgeText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = brandColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.effectiveTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$hostText · ${brand.category.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                data.note?.takeIf { it.isNotBlank() }?.let { noteText ->
                    Text(
                        text = noteText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = { copyToClipboard(context, "链接", data.url, "已复制链接") },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "复制链接", modifier = Modifier.size(16.dp))
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑链接", modifier = Modifier.size(16.dp))
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "删除", modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * 凭证/号牌高对比度卡片（会议号/密码、取件码、车次票务、核销码）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccessPassCard(
    data: AccessPassData,
    onEdit: (() -> Unit)? = null,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val passType = remember(data.passType) {
        LinkAndPassSupport.AccessPassType.fromCode(data.passType)
    }
    val accent = when (passType) {
        LinkAndPassSupport.AccessPassType.MEETING -> Color(0xFF006EFF)
        LinkAndPassSupport.AccessPassType.PICKUP -> Color(0xFFD97706)
        LinkAndPassSupport.AccessPassType.TICKET -> Color(0xFF059669)
        LinkAndPassSupport.AccessPassType.VERIFY -> Color(0xFF7C3AED)
        LinkAndPassSupport.AccessPassType.SEAT -> Color(0xFF0284C7)
        LinkAndPassSupport.AccessPassType.OTHER -> Color(0xFF475569)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accent.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = buildString {
                            append(passType.label)
                            data.providerName?.takeIf { it.isNotBlank() }?.let {
                                append(" · ").append(it)
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "删除", modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 大号等宽核心凭证码 + 一键复制
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = data.primaryCode,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = {
                        copyToClipboard(
                            context = context,
                            label = data.title,
                            text = data.primaryCode,
                            toastMessage = "已复制：${data.primaryCode}"
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制", fontSize = 12.sp)
                }
            }

            val hasSecret = !data.secondarySecret.isNullOrBlank()
            val hasActionUrl = !data.actionUrl.isNullOrBlank()
            if (hasSecret || hasActionUrl) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    data.secondarySecret?.takeIf { it.isNotBlank() }?.let { secret ->
                        val secretValue = secret.removePrefix("密码").removePrefix("口令").trim().ifBlank { secret }
                        AssistChip(
                            onClick = { copyToClipboard(context, "密码/口令", secretValue, "已复制：$secretValue") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp)) },
                            label = { Text(secret, maxLines = 1) }
                        )
                    }
                    data.actionUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        AssistChip(
                            onClick = { openLinkBookmark(context, LinkBookmarkData(url = url, title = data.title)) },
                            leadingIcon = { Icon(Icons.Default.Launch, null, modifier = Modifier.size(14.dp)) },
                            label = { Text("一键进入") }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 添加 / 编辑「网页/深链接」对话框（支持自动剥离粘贴分享文案）
 */
@Composable
fun AddLinkBookmarkDialog(
    initialData: LinkBookmarkData? = null,
    onDismiss: () -> Unit,
    onConfirm: (LinkBookmarkData) -> Unit
) {
    val context = LocalContext.current
    var rawUrl by remember(initialData) { mutableStateOf(initialData?.url.orEmpty()) }
    var title by remember(initialData) { mutableStateOf(initialData?.title.orEmpty()) }
    var note by remember(initialData) { mutableStateOf(initialData?.note.orEmpty()) }

    fun tryAutoExtract(sourceText: String) {
        val extracted = LinkAndPassSupport.extractLinkFromSharedText(sourceText)
        if (extracted != null) {
            rawUrl = extracted.url
            if (title.isBlank()) {
                title = extracted.title.orEmpty()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialData == null) "添加网页/深链接" else "编辑网页/深链接") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        val extracted = LinkAndPassSupport.extractLinkFromSharedText(clip)
                        if (extracted != null) {
                            rawUrl = extracted.url
                            title = extracted.title.orEmpty()
                            Toast.makeText(context, "已从剪贴板识别链接与标题", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "剪贴板中未检测到有效链接", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("一键识别剪贴板分享链接")
                }

                OutlinedTextField(
                    value = rawUrl,
                    onValueChange = { next ->
                        // 若直接粘贴带中文前缀的分享文案，自动分离 URL 与标题
                        if (next.length - rawUrl.length > 8 && next.contains("http")) {
                            tryAutoExtract(next)
                        } else {
                            rawUrl = next
                        }
                    },
                    label = { Text("链接 URL 或 App 深链") },
                    placeholder = { Text("https://... 或粘贴整段微信/飞书分享") },
                    leadingIcon = { Icon(Icons.Default.Link, null) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题（可选，留空自动取域名）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanedUrl = LinkAndPassSupport.normalizeLaunchUrl(rawUrl)
                    if (cleanedUrl.isBlank()) {
                        Toast.makeText(context, "请输入有效的 URL 或链接", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    val brand = LinkAndPassSupport.resolveBrandMeta(cleanedUrl)
                    onConfirm(
                        LinkBookmarkData(
                            url = cleanedUrl,
                            title = title.trim().takeIf { it.isNotEmpty() } ?: brand.brandName,
                            linkCategory = brand.category.code,
                            note = note.trim().takeIf { it.isNotEmpty() }
                        )
                    )
                }
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
 * 添加 / 编辑「凭证/号牌卡」对话框（支持从剪贴板短信/会议邀请一键自动识别填充）
 */
@OptIn(ExperimentalLayoutApi::class)
private data class AccessPassFieldSpec(
    val codeLabel: String,
    val codePlaceholder: String,
    val quickSecretLabel: String,
    val quickSecretPlaceholder: String,
    val titleLabel: String,
    val titlePlaceholder: String
)

private fun accessPassFieldSpec(type: LinkAndPassSupport.AccessPassType): AccessPassFieldSpec =
    when (type) {
        LinkAndPassSupport.AccessPassType.MEETING -> AccessPassFieldSpec(
            codeLabel = "会议号 *",
            codePlaceholder = "如 882 341 990",
            quickSecretLabel = "入会密码",
            quickSecretPlaceholder = "可选",
            titleLabel = "会议主题（选填，留空自动命名）",
            titlePlaceholder = "如：每周产品评审"
        )
        LinkAndPassSupport.AccessPassType.PICKUP -> AccessPassFieldSpec(
            codeLabel = "取件码 *",
            codePlaceholder = "如 3-2-1048",
            quickSecretLabel = "柜门/备注",
            quickSecretPlaceholder = "可选",
            titleLabel = "驿站 / 快递名称（选填）",
            titlePlaceholder = "如：菜鸟驿站 · 京东快递"
        )
        LinkAndPassSupport.AccessPassType.TICKET -> AccessPassFieldSpec(
            codeLabel = "车次 / 座位号 *",
            codePlaceholder = "如 G1234 05车12A",
            quickSecretLabel = "检票口",
            quickSecretPlaceholder = "如 A12",
            titleLabel = "行程摘要（选填）",
            titlePlaceholder = "如：北京南 → 上海虹桥"
        )
        LinkAndPassSupport.AccessPassType.VERIFY -> AccessPassFieldSpec(
            codeLabel = "核销 / 预约码 *",
            codePlaceholder = "如 XY9988",
            quickSecretLabel = "凭证密码",
            quickSecretPlaceholder = "可选",
            titleLabel = "预约门店 / 事项（选填）",
            titlePlaceholder = "如：口腔医院 / 展馆门票"
        )
        LinkAndPassSupport.AccessPassType.SEAT -> AccessPassFieldSpec(
            codeLabel = "考场 / 座位号 *",
            codePlaceholder = "如 第12考场 08座",
            quickSecretLabel = "准考证号",
            quickSecretPlaceholder = "可选",
            titleLabel = "考试科目 / 地点（选填）",
            titlePlaceholder = "如：雅思口语 · 教学楼302"
        )
        LinkAndPassSupport.AccessPassType.OTHER -> AccessPassFieldSpec(
            codeLabel = "凭证码 *",
            codePlaceholder = "输入验证码或号牌",
            quickSecretLabel = "附加密码",
            quickSecretPlaceholder = "可选",
            titleLabel = "凭证名称（选填）",
            titlePlaceholder = "如：门禁通行码"
        )
    }

@Composable
fun AddAccessPassDialog(
    initialData: AccessPassData? = null,
    onDismiss: () -> Unit,
    onConfirm: (AccessPassData) -> Unit
) {
    val context = LocalContext.current
    var passType by remember(initialData) {
        mutableStateOf(LinkAndPassSupport.AccessPassType.fromCode(initialData?.passType))
    }
    var title by remember(initialData) { mutableStateOf(initialData?.title.orEmpty()) }
    var primaryCode by remember(initialData) { mutableStateOf(initialData?.primaryCode.orEmpty()) }
    var secondarySecret by remember(initialData) { mutableStateOf(initialData?.secondarySecret.orEmpty()) }
    var providerName by remember(initialData) { mutableStateOf(initialData?.providerName.orEmpty()) }
    var actionUrl by remember(initialData) { mutableStateOf(initialData?.actionUrl.orEmpty()) }
    var showMoreDetails by remember(initialData) {
        mutableStateOf(
            !initialData?.providerName.isNullOrBlank() || !initialData?.actionUrl.isNullOrBlank()
        )
    }

    var clipboardPreview by remember(initialData) { mutableStateOf<AccessPassData?>(null) }
    LaunchedEffect(initialData) {
        if (initialData == null) {
            clipboardPreview = runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                LinkAndPassSupport.extractPassesFallbackFromText(clip).firstOrNull()
            }.getOrNull()
        }
    }

    fun applyDetectedPass(detected: AccessPassData) {
        passType = LinkAndPassSupport.AccessPassType.fromCode(detected.passType)
        title = detected.title
        primaryCode = detected.primaryCode
        secondarySecret = detected.secondarySecret.orEmpty()
        providerName = detected.providerName.orEmpty()
        actionUrl = detected.actionUrl.orEmpty()
        if (providerName.isNotBlank() || actionUrl.isNotBlank()) {
            showMoreDetails = true
        }
        clipboardPreview = null
        Toast.makeText(context, "已自动填入：${detected.title}", Toast.LENGTH_SHORT).show()
    }

    val fieldSpec = remember(passType) { accessPassFieldSpec(passType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialData == null) "添加凭证/号牌" else "编辑凭证/号牌") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val preview = clipboardPreview
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (preview != null) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (preview != null) {
                                applyDetectedPass(preview)
                            } else {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                val detected = LinkAndPassSupport.extractPassesFallbackFromText(clip).firstOrNull()
                                if (detected != null) {
                                    applyDetectedPass(detected)
                                } else {
                                    Toast.makeText(context, "剪贴板未检测到取件码、会议号或车次", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (preview != null) {
                                "识别到${LinkAndPassSupport.AccessPassType.fromCode(preview.passType).label} ${preview.primaryCode} · 点击一键填入"
                            } else {
                                "粘贴剪贴板自动识别（会议号 / 取件码 / 车次）"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (preview != null) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 单行横向滑动类型选择条（节省纵向空间）
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = LinkAndPassSupport.AccessPassType.entries,
                        key = { it.code }
                    ) { option ->
                        FilterChip(
                            selected = passType == option,
                            onClick = { passType = option },
                            label = {
                                Text(
                                    text = option.label,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                // 核心行：凭证码 + 对应附加码/密码 并排展示，打开即聚焦输入核心号码
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = primaryCode,
                        onValueChange = { primaryCode = it },
                        label = { Text(fieldSpec.codeLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text(fieldSpec.codePlaceholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.weight(1.45f)
                    )
                    OutlinedTextField(
                        value = secondarySecret,
                        onValueChange = { secondarySecret = it },
                        label = { Text(fieldSpec.quickSecretLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text(fieldSpec.quickSecretPlaceholder, maxLines = 1) },
                        singleLine = true,
                        modifier = Modifier.weight(0.95f)
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(fieldSpec.titleLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    placeholder = { Text(fieldSpec.titlePlaceholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(
                    onClick = { showMoreDetails = !showMoreDetails },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier
                        .align(Alignment.Start)
                        .height(32.dp)
                ) {
                    Text(
                        text = if (showMoreDetails) "收起更多信息（来源平台 / 直达链接）" else "展开更多（来源平台 / 直达链接）",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                androidx.compose.animation.AnimatedVisibility(visible = showMoreDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = providerName,
                            onValueChange = { providerName = it },
                            label = { Text("平台 / 驿站名（可选）") },
                            placeholder = { Text("如：腾讯会议 / 顺丰 / 12306") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = actionUrl,
                            onValueChange = { actionUrl = it },
                            label = { Text("直达链接（可选，如入会 URL）") },
                            placeholder = { Text("https://...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanCode = primaryCode.trim()
                    if (cleanCode.isEmpty()) {
                        Toast.makeText(context, "请填写${fieldSpec.codeLabel.removeSuffix(" *")}", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    val resolvedProvider = providerName.trim().takeIf { it.isNotEmpty() }
                    val resolvedTitle = title.trim().ifBlank {
                        resolvedProvider ?: passType.label
                    }
                    onConfirm(
                        AccessPassData(
                            passType = passType.code,
                            title = resolvedTitle,
                            primaryCode = cleanCode,
                            secondarySecret = secondarySecret.trim().takeIf { it.isNotEmpty() },
                            providerName = resolvedProvider,
                            actionUrl = actionUrl.trim().takeIf { it.isNotEmpty() }
                                ?.let { LinkAndPassSupport.normalizeLaunchUrl(it) }
                        )
                    )
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
