package com.litetask.app.ui.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.litetask.app.reminder.PermissionHelper
import com.litetask.app.ui.settings.SettingsViewModel

/**
 * 应用权限界面
 * 
 * 集中管理应用所需的各项系统权限，包括：
 * - 提醒相关权限（通知、精确闹钟、后台弹出、锁屏显示等）
 * - 定位权限（用于地图和位置相关功能）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用权限") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 提醒权限卡片
            ReminderPermissionsCard(context, viewModel)
            
            // 定位权限卡片
            LocationPermissionsCard(context, viewModel)
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 提醒权限卡片
 */
@Composable
private fun ReminderPermissionsCard(
    context: android.content.Context,
    viewModel: SettingsViewModel
) {
    // 检查各项权限状态
    var hasNotificationPermission by remember { mutableStateOf(true) }
    var hasExactAlarmPermission by remember { mutableStateOf(true) }
    var hasBackgroundActivityPermission by remember { mutableStateOf(true) }
    var hasLockScreenPermission by remember { mutableStateOf(true) }
    var hasOverlayPermission by remember { mutableStateOf(true) }
    var hasAutoStartSettings by remember { mutableStateOf(false) }
    
    // 用于触发权限状态刷新
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // 监听生命周期，当页面重新获得焦点时刷新权限状态
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // 刷新权限状态
    LaunchedEffect(refreshTrigger) {
        hasNotificationPermission = PermissionHelper.hasNotificationPermission(context)
        hasExactAlarmPermission = PermissionHelper.canScheduleExactAlarms(context)
        hasBackgroundActivityPermission = PermissionHelper.hasBackgroundActivityPermission(context)
        hasLockScreenPermission = PermissionHelper.hasLockScreenPermission(context)
        hasOverlayPermission = PermissionHelper.hasOverlayPermission(context)
        hasAutoStartSettings = PermissionHelper.hasAutoStartSettings(context)
    }
    
    PermissionCard(
        title = "提醒权限",
        icon = Icons.Default.Notifications
    ) {
        // 说明文字
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "部分手机系统需要手动开启以下权限，否则应用不在后台时提醒可能无法正常触发",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 权限状态列表
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 通知权限
            PermissionItem(
                title = "通知权限",
                description = "允许显示提醒通知",
                isGranted = hasNotificationPermission,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }
                }
            )
            
            // 精确闹钟权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermissionItem(
                    title = "精确闹钟权限",
                    description = "允许设置精确的提醒时间",
                    isGranted = hasExactAlarmPermission,
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )
            }
            
            // 后台弹出界面权限（Android 10+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PermissionItem(
                    title = "后台弹出界面",
                    description = "允许在后台弹出提醒界面",
                    isGranted = hasBackgroundActivityPermission,
                    onClick = {
                        val intent = PermissionHelper.getBackgroundActivitySettingsIntent(context)
                        context.startActivity(intent)
                    }
                )
            }
            
            // 锁屏显示权限
            PermissionItem(
                title = "锁屏显示",
                description = "允许在锁屏界面显示提醒",
                isGranted = hasLockScreenPermission,
                onClick = {
                    val intent = PermissionHelper.getLockScreenSettingsIntent(context)
                    context.startActivity(intent)
                }
            )
            
            // 悬浮窗权限（可选，用于增强提醒效果）
            PermissionItem(
                title = "悬浮窗权限（可选）",
                description = "增强亮屏时的提醒效果",
                isGranted = hasOverlayPermission,
                onClick = {
                    val intent = PermissionHelper.getOverlaySettingsIntent(context)
                    context.startActivity(intent)
                }
            )
            
            // 自启动权限（仅在有可用设置页面时显示）
            if (hasAutoStartSettings) {
                PermissionItem(
                    title = "自启动权限",
                    description = "允许应用在后台被清理后自动重启",
                    isGranted = false, // 无法检测，始终显示"去开启"
                    onClick = {
                        context.startActivity(PermissionHelper.getAutoStartSettingsIntent(context))
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 跳转到应用详情设置
        Text(
            text = "如果提醒仍然无法正常工作，请在系统设置中：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "• 开启「自启动」权限\n• 将后台限制设为「无限制」\n• 关闭应用「电池优化」",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = {
                // 跳转到应用详情设置页面
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("打开应用设置")
        }
    }
}

/**
 * 定位权限卡片
 */
@Composable
private fun LocationPermissionsCard(
    context: android.content.Context,
    viewModel: SettingsViewModel
) {
    // 检查定位权限状态
    var hasLocationPermission by remember { mutableStateOf(true) }
    
    // 用于触发权限状态刷新
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // 监听生命周期，当页面重新获得焦点时刷新权限状态
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // 刷新权限状态
    LaunchedEffect(refreshTrigger) {
        hasLocationPermission = PermissionHelper.hasLocationPermission(context)
    }
    
    PermissionCard(
        title = "定位权限",
        icon = Icons.Default.LocationOn
    ) {
        // 说明文字
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "用于任务地图预览、路线规划等位置相关功能",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 权限状态列表
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 位置权限
            PermissionItem(
                title = "位置权限",
                description = "允许访问设备位置信息",
                isGranted = hasLocationPermission,
                onClick = {
                    val intent = PermissionHelper.getLocationSettingsIntent(context)
                    context.startActivity(intent)
                }
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 提示信息
        Text(
            text = "注意：定位权限仅在使用地图相关功能时需要，不会在后台收集位置信息",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 权限卡片组件
 */
@Composable
private fun PermissionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            content()
        }
    }
}

/**
 * 权限项组件
 */
@Composable
private fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (isGranted) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "已开启",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(24.dp)
            )
        } else {
            TextButton(onClick = onClick) {
                Text("去开启")
            }
        }
    }
}
