package com.litetask.app.ui.userdata

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import com.litetask.app.data.model.UserProfileHistoryEntity
import com.litetask.app.data.model.UserLocationEntity
import com.litetask.app.ui.components.AgentThinkingOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDataScreen(
    onNavigateBack: () -> Unit,
    viewModel: UserDataViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // 防抖状态
    var isNavigating by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据分析") },
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
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // AI分析次数提示
                AIAnalysisCountCard(count = uiState.statistics.aiAnalysisCount)
                
                // 任务统计概览
                ObjectiveDataCard(statistics = uiState.statistics)
                
                // 最近30天完成趋势
                MonthlyTrendCard(trendData = uiState.statistics.monthlyTrend)
                
                // 分类分布饼图
                CategoryDistributionCard(categoryStats = uiState.statistics.categoryStats)
                
                // 地点统计（整合显示）
                LocationStatsCard(locationStats = uiState.statistics.locationStats)
                
                // 用户画像
                UserProfileCard(
                    profile = uiState.userProfile,
                    onProfileUpdate = { viewModel.updateUserProfile(it) }
                )
                
                // 智能分析
                AnalyzeButton(
                    isAnalyzing = uiState.isAnalyzing,
                    onClick = { viewModel.triggerAnalysis() }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    
    // 画像分析悬浮窗（类似AI任务分析）
    AnalysisProgressDialog(
        visible = uiState.showAnalysisOverlay,
        onDismiss = { 
            if (uiState.isAnalyzing) {
                viewModel.cancelAnalysis()
            } else {
                viewModel.dismissAnalysisOverlay()
            }
        },
        isAnalyzing = uiState.isAnalyzing,
        status = uiState.agentStatus,
        logs = uiState.agentLogs
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

@Composable
fun AnalysisProgressDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    isAnalyzing: Boolean,
    status: String,
    logs: List<String>
) {
    if (!visible) return
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶部关闭按钮
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 24.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, androidx.compose.foundation.shape.CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // AI 图标
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                androidx.compose.foundation.shape.CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = if (isAnalyzing) "正在智能分析" else "分析完成",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "深度推演您的用户画像特征...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AgentThinkingOverlay(
                            visible = true,
                            status = status,
                            logs = logs,
                            modifier = Modifier.fillMaxSize()
                        )
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
        headerColor = MaterialTheme.colorScheme.primary
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
fun LocationStatsCard(locationStats: LocationStats) {
    DataCard(
        title = "地点活动分析",
        icon = Icons.Default.Place,
        headerColor = MaterialTheme.colorScheme.tertiary
    ) {
        if (locationStats.totalLocations == 0) {
            Text(
                "暂无位置活动记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
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
                                color = MaterialTheme.colorScheme.primary
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
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
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
    color: Color
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
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

@Composable
fun UserLocationsCard(locations: List<UserLocationEntity>) {
    DataCard(
        title = "物理空间映射",
        icon = Icons.Default.Place,
        headerColor = MaterialTheme.colorScheme.tertiary
    ) {
        if (locations.isEmpty()) {
            Text(
                "暂无位置活动记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("地点名称", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelMedium)
                    Text("作为出发地", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    Text("作为目的地", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f))
                
                locations.sortedByDescending { it.originCount + it.destinationCount }.take(10).forEach { location ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = location.name,
                            modifier = Modifier.weight(2f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = location.originCount.toString(),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = location.destinationCount.toString(),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserProfileCard(
    profile: UserProfileHistoryEntity?,
    onProfileUpdate: (UserProfileHistoryEntity) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editedProfile by remember(profile) {
        mutableStateOf(
            profile ?: UserProfileHistoryEntity(
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
        )
    }
    
    DataCard(
        title = "用户画像",
        icon = Icons.Default.Person,
        headerColor = MaterialTheme.colorScheme.secondary,
        action = {
            IconButton(onClick = { isEditing = !isEditing }) {
                Icon(
                    if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                    contentDescription = if (isEditing) "取消编辑" else "编辑"
                )
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
                            onClick = { isEditing = false },
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
                "能力与倾向指标",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            ProfileSliderField("规划能力级别 (1-5)", profile.planAbility) { onProfileChange(profile.copy(planAbility = it)) }
            ProfileSliderField("抗压级别 (1-5)", profile.stressLevel) { onProfileChange(profile.copy(stressLevel = it)) }
            ProfileSliderField("拖延指数级别 (1-5)", profile.delayIndex) { onProfileChange(profile.copy(delayIndex = it)) }
        }
    }
}

@Composable
fun ProfileSliderField(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 1f..5f,
            steps = 3,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
fun ProfileDisplayFields(profile: UserProfileHistoryEntity?) {
    if (profile == null) {
        Text(
            "暂无画像数据，点击下方按钮进行智能分析",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileField("身份", profile.identity ?: "未设置")
            ProfileField("行业", profile.industry ?: "未设置")
            ProfileField("性格特征", profile.personality ?: "未分析")
            ProfileField("出行偏好", profile.travelPreference ?: "未设置")
            ProfileField("近期重心", profile.categoryFocus ?: "未设置")
            ProfileField("输入偏好", profile.inputPreference ?: "未设置")
            ProfileField("执行节奏", profile.executionRhythm ?: "未设置")
            ProfileField("AI语气偏好", profile.aiTone ?: "未分析")
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
            
            ProfileField("规划能力级别", "${profile.planAbility}/5")
            ProfileField("抗压级别", "${profile.stressLevel}/5")
            ProfileField("拖延指数级别", "${profile.delayIndex}/5")
            ProfileField("总体完成率", "${(profile.completionRate * 100).toInt()}%")
            ProfileField("近期拖延率", "${(profile.delayedRate * 100).toInt()}%")
            
            val timeAgo = getTimeAgo(profile.createdAt)
            Text(
                "最后分析：$timeAgo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ProfileField(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 80.dp, max = 100.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
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

@Composable
fun AnalysisLogsView(logs: List<String>) {
    val scrollState = rememberScrollState()
    
    // 自动滚动到底部
    LaunchedEffect(logs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp, max = 250.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Agent 分析日志：",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            logs.forEach { log ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp)
                    )
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
fun AIAnalysisCountCard(count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        "AI 分析条数",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "累计智能分析",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            Text(
                "$count 次",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
fun MonthlyTrendCard(trendData: List<DailyCompletionData>) {
    DataCard(
        title = "最近30天完成趋势",
        icon = Icons.AutoMirrored.Filled.TrendingUp
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
    modifier: Modifier = Modifier
) {
    val maxValue = (data.maxOfOrNull { it.completedCount } ?: 0).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    
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
            Text(
                text = "${maxValue / 2}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp
            )
            Text(
                text = "0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp
            )
        }
        
        // 右侧图表区域
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Canvas(modifier = Modifier.fillMaxSize().padding(bottom = 24.dp)) {
                val barWidth = size.width / data.size
                val maxHeight = size.height
                
                data.forEachIndexed { index, item ->
                    val barHeight = (item.completedCount.toFloat() / maxValue) * maxHeight
                    
                    // 更加精确的间距计算
                    val barActualWidth = barWidth * 0.7f
                    val x = index * barWidth + (barWidth - barActualWidth) / 2
                    
                    if (barHeight > 0) {
                        drawRoundRect(
                            color = barColor,
                            topLeft = androidx.compose.ui.geometry.Offset(x, maxHeight - barHeight),
                            size = androidx.compose.ui.geometry.Size(barActualWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                        )
                    } else {
                        // 绘制一个极低高度的底色，表示有日期但无完成数
                        drawRect(
                            color = barColor.copy(alpha = 0.1f),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp), // 增加间距使标签向右移动
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 饼图 - 占据更多空间
                SimplePieChart(
                    data = categoryStats,
                    modifier = Modifier
                        .size(160.dp)
                )
                
                // 图例 - 紧凑布局
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categoryStats.take(6).forEach { stat ->
                        CategoryLegendItem(stat)
                    }
                    if (categoryStats.size > 6) {
                        Text(
                            "还有 ${categoryStats.size - 6} 个分类...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
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
        val radius = minOf(centerX, centerY) * 0.9f
        
        var startAngle = -90f
        
        data.forEach { stat ->
            val sweepAngle = stat.percentage * 360f
            val color = parseColor(stat.categoryColor)
            
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = androidx.compose.ui.geometry.Offset(centerX - radius, centerY - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )
            
            startAngle += sweepAngle
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
fun CategoryLegendItem(stat: CategoryStats) {
    val categoryColor = parseColor(stat.categoryColor)
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 颜色方块
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                .background(categoryColor)
        )
        
        // 分类名称和数量
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stat.categoryName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
            Text(
                "${stat.taskCount} 个 (${(stat.percentage * 100).toInt()}%)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}
