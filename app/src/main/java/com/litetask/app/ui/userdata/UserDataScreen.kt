package com.litetask.app.ui.userdata

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.litetask.app.data.model.UserProfileHistoryEntity
import com.litetask.app.data.model.UserLocationEntity
import com.litetask.app.ui.components.AgentThinkingOverlay
import com.litetask.app.ui.components.SegmentedTabBar
import com.litetask.app.ui.components.formatFocusDurationShort
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDataScreen(
    onNavigateBack: () -> Unit,
    viewModel: UserDataViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // 防抖状态
    var isNavigating by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val overviewScrollState = rememberScrollState()
    val profileScrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据分析与个人画像") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!isNavigating) {
                                isNavigating = true
                                onNavigateBack()
                            }
                        },
                        enabled = !isNavigating
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SegmentedTabBar(
                tabs = listOf("数据概览", "画像与足迹"),
                selectedIndex = selectedTab,
                onSelectTab = { selectedTab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )

            Crossfade(
                targetState = selectedTab,
                label = "UserDataTabCrossfade",
                modifier = Modifier.weight(1f)
            ) { tabIndex ->
                if (tabIndex == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(overviewScrollState)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 任务统计概览（已整合 AI 解析次数徽章）
                        ObjectiveDataCard(statistics = uiState.statistics)

                        // 专注计时统计
                        FocusStatsCard(focusStats = uiState.statistics.focusStats)

                        // 最近30天完成趋势（支持点击柱状条查看当日明细）
                        MonthlyTrendCard(trendData = uiState.statistics.monthlyTrend)

                        // 分类分布环形图与完整两列图例
                        CategoryDistributionCard(categoryStats = uiState.statistics.categoryStats)

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(profileScrollState)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 智能画像分析入口置于标签页顶部，无需长距滑动即可生成或更新
                        AnalyzeButton(
                            isAnalyzing = uiState.isAnalyzing,
                            onClick = { viewModel.triggerAnalysis() }
                        )

                        // 用户画像（含历史查看、清空与修正）
                        UserProfileCard(
                            profile = uiState.userProfile,
                            profileHistory = uiState.profileHistory,
                            onProfileUpdate = viewModel::updateUserProfile,
                            onDeleteSnapshot = viewModel::deleteProfileSnapshot,
                            onClearHistory = viewModel::clearUserProfileHistory
                        )

                        // 地点统计（整合显示 + 全部管理）
                        LocationStatsCard(
                            locationStats = uiState.statistics.locationStats,
                            allLocations = uiState.locations,
                            onUpdateLocation = viewModel::updateLocation,
                            onMergeLocations = viewModel::mergeLocations,
                            onDeleteLocation = viewModel::deleteLocation,
                            onClearLocations = viewModel::clearAllLocations
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
    
    // 个人画像分析 BottomSheet（与日程规划建议视觉风格统一）
    ProfileAnalysisSheet(
        visible = uiState.showAnalysisOverlay,
        isAnalyzing = uiState.isAnalyzing,
        status = uiState.agentStatus,
        logs = uiState.agentLogs,
        completedProfile = uiState.completedAnalysisResult,
        previousProfile = uiState.previousProfileBeforeAnalysis,
        errorMessage = uiState.analysisError,
        onDismiss = { viewModel.dismissAnalysisOverlay() },
        onCancel = { viewModel.cancelAnalysis() },
        onRetry = { viewModel.triggerAnalysis() }
    )

    
    // 错误提示
    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("错误") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("确定")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileAnalysisSheet(
    visible: Boolean,
    isAnalyzing: Boolean,
    status: String,
    logs: List<String>,
    completedProfile: UserProfileHistoryEntity?,
    previousProfile: UserProfileHistoryEntity?,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // 统一顶部栏（与日程规划建议一致）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "智能个人画像",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                isAnalyzing -> "多维度行为习惯与专注节律推理中"
                                completedProfile != null -> "分析完成 · 已同步为最新个人画像"
                                errorMessage != null -> "分析未能完成，请重试或检查配置"
                                else -> "AI 画像分析"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FilledTonalIconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                AnimatedContent(
                    targetState = Triple(isAnalyzing, completedProfile?.id, errorMessage),
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(200))
                    },
                    label = "ProfileAnalysisState"
                ) { (analyzing, _, err) ->
                    when {
                        analyzing -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AgentThinkingOverlay(
                                    visible = true,
                                    status = status,
                                    logs = logs,
                                    subtitle = "正在结合任务完成度、出行路线与专注习惯综合建模...",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = onCancel,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("取消分析")
                                }
                            }
                        }

                        completedProfile != null -> {
                            ProfileAnalysisSuccessContent(
                                profile = completedProfile,
                                previousProfile = previousProfile,
                                logs = logs,
                                onRetry = onRetry,
                                onDone = onDismiss
                            )
                        }

                        else -> {
                            ProfileAnalysisErrorContent(
                                errorMessage = err ?: "画像分析未返回有效结果，请稍后重试",
                                logs = logs,
                                onRetry = onRetry,
                                onDismiss = onDismiss
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileAnalysisSuccessContent(
    profile: UserProfileHistoryEntity,
    previousProfile: UserProfileHistoryEntity?,
    logs: List<String>,
    onRetry: () -> Unit,
    onDone: () -> Unit
) {
    var logsExpanded by rememberSaveable(profile.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 更新成功提示卡片
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "画像已更新",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    val headlineParts = listOfNotNull(
                        profile.identity?.takeIf { it.isNotBlank() },
                        profile.industry?.takeIf { it.isNotBlank() }
                    )
                    if (headlineParts.isNotEmpty()) {
                        Text(
                            text = headlineParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // 推演特质摘要
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!profile.personality.isNullOrBlank()) {
                    Text(
                        text = profile.personality,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val habitTags = listOfNotNull(
                    profile.executionRhythm?.takeIf { it.isNotBlank() }?.let { "节奏: $it" },
                    profile.travelPreference?.takeIf { it.isNotBlank() }?.let { "出行: $it" },
                    profile.aiTone?.takeIf { it.isNotBlank() }?.let { "语气: $it" },
                    profile.categoryFocus?.takeIf { it.isNotBlank() }?.let { "重心: $it" }
                )
                if (habitTags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        habitTags.forEach { tag ->
                            ProfileTraitPill(text = tag)
                        }
                    }
                }
            }
        }

        // 三维评分与变化对比
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProfileDeltaScoreCard(
                label = "规划能力",
                score = profile.planAbility,
                previousScore = previousProfile?.planAbility,
                description = planAbilityLabel(profile.planAbility),
                modifier = Modifier.weight(1f)
            )
            ProfileDeltaScoreCard(
                label = "压力指数",
                score = profile.stressLevel,
                previousScore = previousProfile?.stressLevel,
                description = stressLevelLabel(profile.stressLevel),
                modifier = Modifier.weight(1f)
            )
            ProfileDeltaScoreCard(
                label = "拖延指数",
                score = profile.delayIndex,
                previousScore = previousProfile?.delayIndex,
                description = delayIndexLabel(profile.delayIndex),
                modifier = Modifier.weight(1f)
            )
        }

        // 可折叠推理步骤日志
        if (logs.isNotEmpty()) {
            AnalysisLogAccordion(
                logs = logs,
                expanded = logsExpanded,
                onToggle = { logsExpanded = !logsExpanded }
            )
        }

        // 底部按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("重新分析")
            }
            Button(
                onClick = onDone,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("完成查看")
            }
        }
    }
}

@Composable
private fun ProfileAnalysisErrorContent(
    errorMessage: String,
    logs: List<String>,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    var logsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "画像分析未能完成",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        if (logs.isNotEmpty()) {
            AnalysisLogAccordion(
                logs = logs,
                expanded = logsExpanded,
                onToggle = { logsExpanded = !logsExpanded }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("关闭")
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("立即重试")
            }
        }
    }
}

@Composable
private fun ProfileDeltaScoreCard(
    label: String,
    score: Int,
    previousScore: Int?,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$score",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "/5",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp, start = 1.dp)
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (previousScore != null && previousScore != score) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "$previousScore → $score",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalysisLogAccordion(
    logs: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "查看 AI 推理过程（${logs.size} 条）",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    logs.forEach { log ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(5.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    headerColor: Color = MaterialTheme.colorScheme.primary,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = headerColor, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = headerColor)
                }
                action?.invoke()
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun ObjectiveDataCard(statistics: TaskStatistics) {
    DataCard(
        title = "任务统计",
        icon = Icons.Default.Assessment,
        headerColor = MaterialTheme.colorScheme.primary,
        action = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "AI 解析 ${statistics.aiAnalysisCount} 次",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 第一行：总任务、已完成、进行中
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItemCompact(
                    label = "总任务",
                    value = statistics.totalTasks.toString(),
                    icon = Icons.Default.Task,
                    color = MaterialTheme.colorScheme.primary
                )
                StatItemCompact(
                    label = "已完成",
                    value = statistics.completedTasks.toString(),
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFF4CAF50)
                )
                StatItemCompact(
                    label = "进行中",
                    value = statistics.activeTasks.toString(),
                    icon = Icons.Default.PlayArrow,
                    color = Color(0xFF2196F3)
                )
                StatItemCompact(
                    label = "子任务",
                    value = "${statistics.completedSubTasks}/${statistics.totalSubTasks}",
                    icon = Icons.AutoMirrored.Filled.List,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
            
            // 完成率进度条
            val percentage = statistics.completionRate
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("任务完成率", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${(percentage * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                LinearProgressIndicator(
                    progress = { percentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            }
            
            // 按时完成率进度条
            val onTimePercentage = statistics.onTimeRate
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("按时完成率", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${statistics.onTimeCompletedTasks} / ${statistics.completedTasks} (${(onTimePercentage * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }
                
                LinearProgressIndicator(
                    progress = { onTimePercentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                    color = Color(0xFF4CAF50),
                    trackColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                )
            }
        }
    }
}

@Composable
fun StatItemCompact(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun FocusStatsCard(focusStats: FocusStats) {
    val tealColor = Color(0xFF00897B)
    DataCard(
        title = "专注统计",
        icon = Icons.Default.Timer,
        headerColor = tealColor
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItemCompact(
                    label = "今日专注",
                    value = formatFocusStatValue(focusStats.todayFocusSeconds),
                    icon = Icons.Default.Today,
                    color = tealColor
                )
                StatItemCompact(
                    label = "近7天",
                    value = formatFocusStatValue(focusStats.weekFocusSeconds),
                    icon = Icons.Default.DateRange,
                    color = MaterialTheme.colorScheme.primary
                )
                StatItemCompact(
                    label = "累计时长",
                    value = formatFocusStatValue(focusStats.totalFocusSeconds),
                    icon = Icons.Default.Schedule,
                    color = MaterialTheme.colorScheme.secondary
                )
                StatItemCompact(
                    label = "专注次数",
                    value = "${focusStats.sessionCount}次",
                    icon = Icons.Default.Timelapse,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            if (focusStats.sessionCount == 0 && focusStats.topFocusTasks.isEmpty()) {
                Text(
                    text = "暂无专注记录，点击任务详情里的「开始专注」或侧边栏「专注计时器」即可累计真实耗时。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (focusStats.topFocusTasks.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Text(
                        text = "任务专注排行",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val maxSec = focusStats.topFocusTasks.maxOf { it.focusSeconds }.coerceAtLeast(1L)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        focusStats.topFocusTasks.forEachIndexed { index, item ->
                            val ratio = (item.focusSeconds.toFloat() / maxSec.toFloat()).coerceIn(0f, 1f)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}. ${item.taskTitle}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Text(
                                        text = formatFocusDurationShort(item.focusSeconds),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = tealColor
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
                                    color = tealColor,
                                    trackColor = tealColor.copy(alpha = 0.12f)
                                )
                            }
                        }
                    }
                }

                if (focusStats.recentSessions.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Text(
                        text = "最近专注记录",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        focusStats.recentSessions.forEach { session ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = session.taskTitle.ifBlank { "自由专注" },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val modeLabel = if (session.mode == "POMODORO") "番茄钟" else "正计时"
                                    Text(
                                        text = "$modeLabel · ${dateFormat.format(Date(session.startedAt))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                    color = tealColor.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = formatFocusDurationShort(session.actualSeconds),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = tealColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatFocusStatValue(seconds: Long): String {
    return if (seconds <= 0L) "0分钟" else formatFocusDurationShort(seconds)
}

@Composable
fun LocationStatsCard(
    locationStats: LocationStats,
    allLocations: List<UserLocationEntity>,
    onUpdateLocation: (Long, String, String) -> Unit,
    onMergeLocations: (Long, Long) -> Unit,
    onDeleteLocation: (Long) -> Unit,
    onClearLocations: () -> Unit
) {
    var showManageDialog by remember { mutableStateOf(false) }

    DataCard(
        title = "常去地点足迹",
        icon = Icons.Default.Place,
        headerColor = MaterialTheme.colorScheme.tertiary,
        action = if (allLocations.isNotEmpty()) {
            {
                TextButton(onClick = { showManageDialog = true }) {
                    Text("管理 (${allLocations.size})")
                }
            }
        } else null
    ) {
        if (locationStats.totalLocations == 0) {
            Text(
                "暂无地点足迹记录。确认添加带有地址的任务，或用助手查询周边后，常在地和常去地会保存在本机。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // 统计概览
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LocationStatItem(
                        label = "总地点数",
                        value = locationStats.totalLocations.toString(),
                        icon = Icons.Default.LocationOn,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    LocationStatItem(
                        label = "常在地",
                        value = locationStats.topOrigins.firstOrNull()?.name ?: "暂无数据",
                        icon = Icons.Default.Home,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LocationStatItem(
                        label = "常去地",
                        value = locationStats.topDestinations.firstOrNull()?.name ?: "暂无数据",
                        icon = Icons.Default.Flag,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 常在地（出发地）Top 5
                if (locationStats.topOrigins.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "常在地 (出发地)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        locationStats.topOrigins.forEach { location ->
                            LocationItem(
                                name = location.name,
                                count = location.originCount,
                                color = MaterialTheme.colorScheme.primary,
                                locationType = location.locationType,
                                address = location.address
                            )
                        }
                    }
                }

                // 常去地（目的地）Top 5
                if (locationStats.topDestinations.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "常去地 (目的地)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        locationStats.topDestinations.forEach { location ->
                            LocationItem(
                                name = location.name,
                                count = location.destinationCount,
                                color = MaterialTheme.colorScheme.secondary,
                                locationType = location.locationType,
                                address = location.address
                            )
                        }
                    }
                }
            }
        }
    }

    if (showManageDialog) {
        ManageLocationsDialog(
            locations = allLocations,
            onUpdate = onUpdateLocation,
            onMerge = onMergeLocations,
            onDelete = onDeleteLocation,
            onClearAll = onClearLocations,
            onDismiss = { showManageDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageLocationsDialog(
    locations: List<UserLocationEntity>,
    onUpdate: (Long, String, String) -> Unit,
    onMerge: (Long, Long) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var editingLocation by remember { mutableStateOf<UserLocationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(UserLocationEntity.TYPE_FREQUENT) }
    var mergingSource by remember { mutableStateOf<UserLocationEntity?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

    if (editingLocation != null) {
        val target = editingLocation!!
        AlertDialog(
            onDismissRequest = { editingLocation = null },
            title = { Text("编辑地点信息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("地点名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "地点标签（帮助 AI 更懂你的生活圈）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            UserLocationEntity.ALL_TYPES.forEach { typeOption ->
                                FilterChip(
                                    selected = selectedType == typeOption,
                                    onClick = { selectedType = typeOption },
                                    label = { Text(typeOption) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onUpdate(target.id, renameText, selectedType)
                        }
                        editingLocation = null
                    },
                    enabled = renameText.isNotBlank()
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingLocation = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (mergingSource != null) {
        val source = mergingSource!!
        val candidates = locations.filter { it.id != source.id }
        AlertDialog(
            onDismissRequest = { mergingSource = null },
            title = { Text("合并到已有地点") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "选择要将「${source.name}」并入的目标地点（合并后将保留目标名称和标签，并累加记录次数）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        candidates.forEach { candidate ->
                            Surface(
                                onClick = {
                                    onMerge(source.id, candidate.id)
                                    mergingSource = null
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (candidate.locationType != UserLocationEntity.TYPE_FREQUENT) {
                                            LocationTypeBadge(candidate.locationType)
                                        }
                                        Text(
                                            text = candidate.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "出发 ${candidate.originCount} 次 · 到达 ${candidate.destinationCount} 次",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { mergingSource = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("清空全部地点足迹") },
            text = { Text("确定删除全部 ${locations.size} 条本地常去地点记录吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        confirmClearAll = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text("取消")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理所有地点 (${locations.size})") },
        text = {
            if (locations.isEmpty()) {
                Text("暂无地点记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    locations.forEach { loc ->
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (loc.locationType != UserLocationEntity.TYPE_FREQUENT) {
                                            LocationTypeBadge(loc.locationType)
                                        }
                                        Text(
                                            text = loc.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (loc.address.isNotBlank()) {
                                        Text(
                                            text = loc.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "出发 ${loc.originCount} 次 · 到达 ${loc.destinationCount} 次",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (locations.size > 1) {
                                    TextButton(
                                        onClick = { mergingSource = loc },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.heightIn(min = 32.dp)
                                    ) {
                                        Text("合并", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        editingLocation = loc
                                        renameText = loc.name
                                        selectedType = loc.locationType
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "编辑",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { onDelete(loc.id) }) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (locations.isNotEmpty()) {
                TextButton(
                    onClick = { confirmClearAll = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("清空全部")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun LocationTypeBadge(locationType: String) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = locationType,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun LocationStatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(100.dp) // 限制宽度
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp) // 减小图标尺寸
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium, // 从 titleMedium 改为 bodyMedium
            fontWeight = FontWeight.SemiBold, // 从 Bold 改为 SemiBold
            color = color,
            maxLines = 1, // 限制为单行
            overflow = TextOverflow.Ellipsis, // 超出部分显示省略号
            textAlign = TextAlign.Center // 居中对齐
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LocationItem(
    name: String,
    count: Int,
    color: Color,
    locationType: String = UserLocationEntity.TYPE_FREQUENT,
    address: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color.copy(alpha = 0.08f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (locationType != UserLocationEntity.TYPE_FREQUENT) {
                    LocationTypeBadge(locationType)
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (address.isNotBlank()) {
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = "次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun emptyDraftProfile(): UserProfileHistoryEntity {
    return UserProfileHistoryEntity(
        id = 0,
        createdAt = System.currentTimeMillis(),
        completionRate = 0.0,
        delayedRate = 0.0,
        identity = "",
        industry = "",
        travelPreference = "",
        categoryFocus = "",
        inputPreference = ""
    )
}

@Composable
fun UserProfileCard(
    profile: UserProfileHistoryEntity?,
    profileHistory: List<UserProfileHistoryEntity>,
    onProfileUpdate: (UserProfileHistoryEntity) -> Unit,
    onDeleteSnapshot: (Long) -> Unit,
    onClearHistory: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var editedProfile by remember(profile) {
        mutableStateOf(profile ?: emptyDraftProfile())
    }

    LaunchedEffect(isEditing, profile) {
        if (!isEditing) {
            editedProfile = profile ?: emptyDraftProfile()
        }
    }

    DataCard(
        title = "用户画像",
        icon = Icons.Default.Person,
        headerColor = MaterialTheme.colorScheme.secondary,
        action = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (profileHistory.isNotEmpty()) {
                    IconButton(onClick = { showHistoryDialog = true }) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "画像历史记录"
                        )
                    }
                }
                IconButton(
                    onClick = {
                        if (isEditing) {
                            editedProfile = profile ?: emptyDraftProfile()
                        }
                        isEditing = !isEditing
                    }
                ) {
                    Icon(
                        if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                        contentDescription = if (isEditing) "取消编辑" else "编辑"
                    )
                }
            }
        }
    ) {
        androidx.compose.animation.Crossfade(
            targetState = isEditing,
            animationSpec = androidx.compose.animation.core.tween(500),
            label = "ProfileEditTransition"
        ) { editing ->
            if (editing) {
                Column {
                    // 编辑模式
                    ProfileEditFields(
                        profile = editedProfile,
                        onProfileChange = { editedProfile = it }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                editedProfile = profile ?: emptyDraftProfile()
                                isEditing = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Text("取消")
                        }
                        Button(
                            onClick = {
                                onProfileUpdate(editedProfile)
                                isEditing = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("保存修改")
                        }
                    }
                }
            } else {
                // 显示模式
                ProfileDisplayFields(profile = profile)
            }
        }
    }

    if (showHistoryDialog) {
        ProfileHistoryDialog(
            history = profileHistory,
            onDeleteSnapshot = onDeleteSnapshot,
            onClearAll = onClearHistory,
            onDismiss = { showHistoryDialog = false }
        )
    }
}

@Composable
private fun ProfileHistoryDialog(
    history: List<UserProfileHistoryEntity>,
    onDeleteSnapshot: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmClearAll by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("清空全部用户画像") },
            text = { Text("确定要清空所有历史和当前画像快照吗？清空后日程建议将不再参考旧画像，直到您重新生成或填写。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        confirmClearAll = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text("取消")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("画像演变记录 (${history.size})") },
        text = {
            if (history.isEmpty()) {
                Text("暂无画像历史记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    history.forEachIndexed { index, item ->
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = if (index == 0) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val sourceTag = if (item.source == UserProfileHistoryEntity.SOURCE_MANUAL) "手动修订" else "AI分析"
                                    Text(
                                        text = if (index == 0) "当前生效 · $sourceTag · ${dateFormat.format(Date(item.createdAt))}"
                                        else "$sourceTag · ${dateFormat.format(Date(item.createdAt))}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    IconButton(
                                        onClick = { onDeleteSnapshot(item.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "删除此条快照",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = listOfNotNull(
                                        item.identity?.takeIf { it.isNotBlank() },
                                        item.industry?.takeIf { it.isNotBlank() }
                                    ).joinToString(" · ").ifBlank { "未填写身份" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (!item.personality.isNullOrBlank()) {
                                    Text(
                                        text = item.personality,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "规划 ${item.planAbility}/5 · 压力 ${item.stressLevel}/5 · 拖延 ${item.delayIndex}/5",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (history.isNotEmpty()) {
                TextButton(
                    onClick = { confirmClearAll = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("清空画像")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun ProfileEditFields(
    profile: UserProfileHistoryEntity,
    onProfileChange: (UserProfileHistoryEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // 第一组：基本身份信息
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "基本身份",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = profile.identity ?: "",
                    onValueChange = { onProfileChange(profile.copy(identity = it)) },
                    label = { Text("身份") },
                    placeholder = { Text("学生...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = profile.industry ?: "",
                    onValueChange = { onProfileChange(profile.copy(industry = it)) },
                    label = { Text("行业") },
                    placeholder = { Text("IT...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
            }
        }

        // 第二组：行为与偏好
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "行为偏好",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = profile.personality ?: "",
                onValueChange = { onProfileChange(profile.copy(personality = it)) },
                label = { Text("综合性格特点") },
                placeholder = { Text("如完美主义、实干派") },
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = profile.travelPreference ?: "",
                    onValueChange = { onProfileChange(profile.copy(travelPreference = it)) },
                    label = { Text("出行偏好") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = profile.executionRhythm ?: "",
                    onValueChange = { onProfileChange(profile.copy(executionRhythm = it)) },
                    label = { Text("执行节奏") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
            }

            OutlinedTextField(
                value = profile.aiTone ?: "",
                onValueChange = { onProfileChange(profile.copy(aiTone = it)) },
                label = { Text("AI 反馈语气偏好") },
                placeholder = { Text("温和、严厉、极简干练") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))

        // 第三组：能力分值
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "能力与状态指数",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            ProfileSliderField(
                label = "规划能力 (1较弱 ~ 5极强)",
                value = profile.planAbility,
                levelDescription = planAbilityLabel(profile.planAbility)
            ) { onProfileChange(profile.copy(planAbility = it)) }
            ProfileSliderField(
                label = "当前压力指数 (1轻松 ~ 5极大)",
                value = profile.stressLevel,
                levelDescription = stressLevelLabel(profile.stressLevel)
            ) { onProfileChange(profile.copy(stressLevel = it)) }
            ProfileSliderField(
                label = "拖延指数 (1极自律 ~ 5较严重)",
                value = profile.delayIndex,
                levelDescription = delayIndexLabel(profile.delayIndex)
            ) { onProfileChange(profile.copy(delayIndex = it)) }
        }
    }
}

private fun planAbilityLabel(score: Int): String = when (score) {
    1 -> "较弱"
    2 -> "一般"
    3 -> "良好"
    4 -> "较强"
    5 -> "极强"
    else -> "一般"
}

private fun stressLevelLabel(score: Int): String = when (score) {
    1 -> "轻松从容"
    2 -> "较为松弛"
    3 -> "适中可控"
    4 -> "较为紧张"
    5 -> "压力极大"
    else -> "适中"
}

private fun delayIndexLabel(score: Int): String = when (score) {
    1 -> "极度自律"
    2 -> "基本准时"
    3 -> "偶有拖延"
    4 -> "较常拖延"
    5 -> "经常踩点/逾期"
    else -> "一般"
}

@Composable
fun ProfileSliderField(
    label: String,
    value: Int,
    levelDescription: String,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "$value · $levelDescription",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(1, 5)) },
            valueRange = 1f..5f,
            steps = 3,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileDisplayFields(profile: UserProfileHistoryEntity?) {
    if (profile == null) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "暂无画像数据，点击右上角编辑或上方“智能分析用户画像”开始",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        val isManual = profile.source == UserProfileHistoryEntity.SOURCE_MANUAL
        val timeAgo = getTimeAgo(profile.createdAt)
        val sourceLabel = if (isManual) "手动修订时间" else "AI 分析时间"

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 1. 身份与核心性格卡片
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = profile.identity?.takeIf { it.isNotBlank() } ?: "未设置身份",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val industry = profile.industry?.takeIf { it.isNotBlank() }
                            if (industry != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = industry,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            color = if (isManual) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isManual) "手动修订" else "AI 推演",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isManual) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = profile.personality?.takeIf { it.isNotBlank() } ?: "暂无性格特征描述",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. 行为习惯标签流
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileTraitPill("出行 · ${profile.travelPreference?.takeIf { it.isNotBlank() } ?: "未设置"}")
                ProfileTraitPill("节奏 · ${profile.executionRhythm?.takeIf { it.isNotBlank() } ?: "未设置"}")
                ProfileTraitPill("语气 · ${profile.aiTone?.takeIf { it.isNotBlank() } ?: "未分析"}")
                ProfileTraitPill("重心 · ${profile.categoryFocus?.takeIf { it.isNotBlank() } ?: "未设置"}")
                ProfileTraitPill("录入 · ${profile.inputPreference?.takeIf { it.isNotBlank() } ?: "未设置"}")
            }

            // 3. 三维能力与状态进度条
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileScoreBar(
                        label = "规划能力",
                        score = profile.planAbility,
                        description = planAbilityLabel(profile.planAbility),
                        barColor = if (profile.planAbility >= 3) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
                    )
                    ProfileScoreBar(
                        label = "当前压力指数",
                        score = profile.stressLevel,
                        description = stressLevelLabel(profile.stressLevel),
                        barColor = when {
                            profile.stressLevel >= 4 -> MaterialTheme.colorScheme.error
                            profile.stressLevel == 3 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    ProfileScoreBar(
                        label = "拖延指数",
                        score = profile.delayIndex,
                        description = delayIndexLabel(profile.delayIndex),
                        barColor = when {
                            profile.delayIndex >= 4 -> MaterialTheme.colorScheme.error
                            profile.delayIndex == 3 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            // 4. 客观统计指标与更新时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileMetricTile(
                    label = "总体完成率",
                    value = "${(profile.completionRate * 100).roundToInt()}%",
                    modifier = Modifier.weight(1f)
                )
                ProfileMetricTile(
                    label = "近30天拖延率",
                    value = "${(profile.delayedRate * 100).roundToInt()}%",
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "$sourceLabel：$timeAgo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileTraitPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ProfileScoreBar(
    label: String,
    score: Int,
    description: String,
    barColor: Color
) {
    val clamped = score.coerceIn(1, 5)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$clamped/5 · $description",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped / 5f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun ProfileMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun AnalyzeButton(
    isAnalyzing: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = !isAnalyzing,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    ) {
        if (isAnalyzing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("分析中...")
        } else {
            Icon(Icons.Default.Psychology, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("智能分析用户画像", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun getTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        diff < 2592000_000 -> "${diff / 86400_000}天前"
        else -> "${diff / 2592000_000}个月前"
    }
}

@Composable
fun MonthlyTrendCard(trendData: List<DailyCompletionData>) {
    var selectedBarIndex by remember(trendData) { mutableStateOf<Int?>(null) }
    val selectedItem = selectedBarIndex?.let { idx -> trendData.getOrNull(idx) }

    DataCard(
        title = "最近30天完成趋势",
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        action = if (trendData.isNotEmpty()) {
            {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedItem != null) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Text(
                        text = if (selectedItem != null) {
                            "${selectedItem.date} · 完成 ${selectedItem.completedCount} 个"
                        } else {
                            "点按柱条看当日"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selectedItem != null) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedItem != null) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        } else null
    ) {
        if (trendData.isEmpty()) {
            Text(
                "暂无数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            SimpleBarChart(
                data = trendData,
                selectedIndex = selectedBarIndex,
                onSelectBar = { idx ->
                    selectedBarIndex = if (selectedBarIndex == idx) null else idx
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}

@Composable
fun SimpleBarChart(
    data: List<DailyCompletionData>,
    selectedIndex: Int? = null,
    onSelectBar: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val maxValue = (data.maxOfOrNull { it.completedCount } ?: 0).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    val selectedBarColor = MaterialTheme.colorScheme.tertiary
    val highlightTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    
    // 增加左右边距
    Row(
        modifier = modifier.padding(horizontal = 4.dp), 
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 左侧纵坐标
        Column(
            modifier = Modifier
                .wrapContentWidth()
                .fillMaxHeight()
                .padding(bottom = 24.dp), // 对齐图表区域，底部预留标签空间
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "$maxValue",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp
            )
            if (maxValue >= 2) {
                Text(
                    text = "${maxValue / 2}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
            }
            Text(
                text = "0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp
            )
        }
        
        // 右侧图表区域
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val tapModifier = if (onSelectBar != null && data.isNotEmpty()) {
                Modifier.pointerInput(data) {
                    detectTapGestures { offset ->
                        val barWidth = size.width.toFloat() / data.size
                        if (barWidth > 0f) {
                            val idx = (offset.x / barWidth).toInt().coerceIn(0, data.lastIndex)
                            onSelectBar(idx)
                        }
                    }
                }
            } else {
                Modifier
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp)
                    .then(tapModifier)
            ) {
                val barWidth = size.width / data.size
                val maxHeight = size.height
                
                data.forEachIndexed { index, item ->
                    val isSelected = selectedIndex == index
                    val barHeight = (item.completedCount.toFloat() / maxValue) * maxHeight
                    
                    // 更加精确的间距计算
                    val barActualWidth = barWidth * 0.7f
                    val slotX = index * barWidth
                    val x = slotX + (barWidth - barActualWidth) / 2

                    if (isSelected) {
                        drawRoundRect(
                            color = highlightTrackColor,
                            topLeft = androidx.compose.ui.geometry.Offset(slotX, 0f),
                            size = androidx.compose.ui.geometry.Size(barWidth, maxHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                        )
                    }

                    val activeColor = if (isSelected) selectedBarColor else barColor
                    if (barHeight > 0) {
                        drawRoundRect(
                            color = activeColor,
                            topLeft = androidx.compose.ui.geometry.Offset(x, maxHeight - barHeight),
                            size = androidx.compose.ui.geometry.Size(barActualWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                        )
                    } else {
                        // 绘制一个极低高度的底色，表示有日期但无完成数
                        drawRect(
                            color = activeColor.copy(alpha = if (isSelected) 0.55f else 0.1f),
                            topLeft = androidx.compose.ui.geometry.Offset(x, maxHeight - 2.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(barActualWidth, 2.dp.toPx())
                        )
                    }
                }
            }
            
            // X轴标签使用自定义 Layout 以保证与柱子中心对齐
            Layout(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(20.dp),
                content = {
                    // 根据数据量决定步长，避免拥挤
                    val step = when {
                        data.size > 25 -> 6
                        data.size > 15 -> 4
                        else -> 2
                    }
                    data.forEachIndexed { index, item ->
                        if (index % step == 0 || index == data.size - 1) {
                            Text(
                                text = item.date,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 8.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible
                            )
                        }
                    }
                }
            ) { measurables, constraints ->
                val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val step = when {
                        data.size > 25 -> 6
                        data.size > 15 -> 4
                        else -> 2
                    }
                    var measurableIndex = 0
                    data.forEachIndexed { index, _ ->
                        if (index % step == 0 || index == data.size - 1) {
                            if (measurableIndex < placeables.size) {
                                val placeable = placeables[measurableIndex++]
                                val barWidth = constraints.maxWidth.toFloat() / data.size
                                val barCenterX = (index + 0.5f) * barWidth
                                val x = (barCenterX - placeable.width / 2f).toInt()
                                // 边界锁定：确保第一个和最后一个标签不超出 Canvas 范围
                                val finalX = x.coerceIn(0, (constraints.maxWidth - placeable.width))
                                placeable.placeRelative(finalX, 0)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryDistributionCard(categoryStats: List<CategoryStats>) {
    DataCard(
        title = "分类分布",
        icon = Icons.Default.PieChart,
        headerColor = MaterialTheme.colorScheme.secondary
    ) {
        if (categoryStats.isEmpty()) {
            Text(
                "暂无任务数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            val totalCount = categoryStats.sumOf { it.taskCount }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 居中现代环形图（中心展示任务总量）
                Box(
                    modifier = Modifier.size(168.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SimplePieChart(
                        data = categoryStats,
                        modifier = Modifier.fillMaxSize()
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = totalCount.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "任务总数",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 完整 2 列对称图例，不截断任何自定义分类
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    categoryStats.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CategoryLegendItem(
                                stat = rowItems[0],
                                modifier = Modifier.weight(1f)
                            )
                            if (rowItems.size > 1) {
                                CategoryLegendItem(
                                    stat = rowItems[1],
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimplePieChart(
    data: List<CategoryStats>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val outerRadius = minOf(centerX, centerY) * 0.92f
        val strokeWidth = outerRadius * 0.4f
        val arcRadius = outerRadius - strokeWidth / 2f
        val gapDegrees = if (data.size > 1) 1.5f else 0f

        var startAngle = -90f

        data.forEach { stat ->
            val rawSweep = stat.percentage * 360f
            val sweepAngle = (rawSweep - gapDegrees).coerceAtLeast(1f)
            val color = parseColor(stat.categoryColor)

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(centerX - arcRadius, centerY - arcRadius),
                size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )

            startAngle += rawSweep
        }
    }
}

// 辅助函数：解析颜色字符串
private fun parseColor(colorHex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: Exception) {
        Color(0xFF0B57D0) // 默认蓝色
    }
}

@Composable
fun CategoryLegendItem(
    stat: CategoryStats,
    modifier: Modifier = Modifier
) {
    val categoryColor = parseColor(stat.categoryColor)

    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 颜色圆点
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(categoryColor)
        )

        // 分类名称和数量
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stat.categoryName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${stat.taskCount} 个 (${(stat.percentage * 100).toInt()}%)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}
