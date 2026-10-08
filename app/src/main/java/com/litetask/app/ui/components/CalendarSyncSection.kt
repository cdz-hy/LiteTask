package com.litetask.app.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.litetask.app.util.CalendarSyncHelper
import com.litetask.app.util.CalendarSyncPreferencesStore
import com.litetask.app.util.CalendarSyncPrefsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PULL_DAYS_OPTIONS = listOf(7, 14, 30, 90)

/**
 * 统一的日历权限申请与生命周期状态刷新 helper，供设置、数据管理与权限页复用。
 */
@Composable
fun rememberCalendarPermissionRequester(
    store: CalendarSyncPreferencesStore,
    onDenied: (() -> Unit)? = null
): (onGranted: () -> Unit) -> Unit {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    DisposableEffect(lifecycleOwner, store) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                store.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        store.refresh()
        val readGranted = grants[Manifest.permission.READ_CALENDAR] == true
        val writeGranted = grants[Manifest.permission.WRITE_CALENDAR] == true
        if (readGranted && writeGranted) {
            pendingAction?.invoke()
        } else {
            onDenied?.invoke() ?: Toast.makeText(
                context,
                "需要系统日历读写权限才能使用双向同步功能",
                Toast.LENGTH_LONG
            ).show()
        }
        pendingAction = null
    }

    return remember(context, launcher) {
        { onGranted: () -> Unit ->
            if (CalendarSyncHelper.hasCalendarPermissions(context)) {
                store.refresh()
                onGranted()
            } else {
                pendingAction = onGranted
                launcher.launch(
                    arrayOf(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR
                    )
                )
            }
        }
    }
}

/**
 * 绑定 CalendarSyncPreferencesStore 的设置面板封装，避免多个界面重复编写状态绑定。
 */
@Composable
fun CalendarSyncSettingsPanel(
    store: CalendarSyncPreferencesStore,
    onRequestCalendarPermission: () -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by store.state.collectAsState()
    CalendarSyncSettingsPanel(
        state = state,
        onRequestCalendarPermission = onRequestCalendarPermission,
        onRefreshCalendars = { store.refresh() },
        onSelectTargetCalendar = { id, name -> store.setTargetCalendar(id, name) },
        onAutoPushChange = { enabled ->
            store.setAutoPushEnabled(enabled)
            if (enabled && !state.hasCalendarPermission) {
                onRequestCalendarPermission()
            }
        },
        onPushIncludeDoneChange = { store.setPushIncludeDone(it) },
        onPullDaysAheadChange = { store.setPullDaysAhead(it) },
        onSyncRemindersChange = { store.setSyncReminders(it) },
        onIcsExportIncludeDoneChange = { store.setIcsExportIncludeDone(it) },
        onScheduleAdviceUseCalendarChange = { enabled ->
            store.setScheduleAdviceUseCalendarEnabled(enabled)
            if (enabled && !state.hasCalendarPermission) {
                onRequestCalendarPermission()
            }
        },
        onOpenGuide = onOpenGuide,
        modifier = modifier
    )
}

/**
 * 日历互通 (.ics) 与系统日历同步设置面板，在「设置」与「数据管理」页共用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSyncSettingsPanel(
    state: CalendarSyncPrefsState,
    onRequestCalendarPermission: () -> Unit,
    onRefreshCalendars: () -> Unit,
    onSelectTargetCalendar: (id: Long, name: String) -> Unit,
    onAutoPushChange: (Boolean) -> Unit,
    onPushIncludeDoneChange: (Boolean) -> Unit,
    onPullDaysAheadChange: (Int) -> Unit,
    onSyncRemindersChange: (Boolean) -> Unit,
    onIcsExportIncludeDoneChange: (Boolean) -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier,
    onScheduleAdviceUseCalendarChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var calendarDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 权限状态提示横幅
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (state.hasCalendarPermission) {
                Color(0xFF4CAF50).copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (state.hasCalendarPermission) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = if (state.hasCalendarPermission) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (state.hasCalendarPermission) "日历读写权限已就绪" else "尚未授予系统日历读写权限",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (state.hasCalendarPermission) {
                                "可正常推送任务、选择目标日历及读取外部日程"
                            } else {
                                "需授权后方可执行系统日历双向同步与账户选择"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!state.hasCalendarPermission) {
                    TextButton(onClick = onRequestCalendarPermission) {
                        Text("去授权")
                    }
                } else {
                    TextButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Text("权限详情")
                    }
                }
            }
        }

        // 目标写入系统日历选择器
        val selectedCalendarLabel = remember(state.targetCalendarId, state.targetCalendarName, state.writableCalendars) {
            if (state.targetCalendarId <= 0L) {
                "自动选择主日历（无账户则新建 LiteTask）"
            } else {
                state.writableCalendars.firstOrNull { it.id == state.targetCalendarId }?.let { cal ->
                    "${cal.displayName} (${cal.accountName})"
                } ?: state.targetCalendarName.takeIf { it.isNotBlank() }?.let { "$it (已保存)" }
                    ?: "自动选择主日历（无账户则新建 LiteTask）"
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded = calendarDropdownExpanded,
                onExpandedChange = { expanded ->
                    if (expanded && !state.hasCalendarPermission) {
                        onRequestCalendarPermission()
                    } else {
                        if (expanded) onRefreshCalendars()
                        calendarDropdownExpanded = expanded
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedCalendarLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("推送目标系统日历") },
                    leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = calendarDropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    singleLine = true
                )

                ExposedDropdownMenu(
                    expanded = calendarDropdownExpanded,
                    onDismissRequest = { calendarDropdownExpanded = false },
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("自动选择主日历", fontWeight = FontWeight.Medium)
                                Text(
                                    "优先系统主日历，无可用账户时自动创建 LiteTask 本地日历",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onSelectTargetCalendar(-1L, "")
                            calendarDropdownExpanded = false
                        }
                    )

                    if (state.writableCalendars.isNotEmpty()) {
                        HorizontalDivider()
                        state.writableCalendars.forEach { cal ->
                            val subtitle = buildString {
                                append(cal.accountName)
                                if (cal.isPrimary) append(" · 主日历")
                                if (cal.isLocalLiteTask) append(" · 本地创建")
                            }
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(cal.displayName, fontWeight = FontWeight.Medium)
                                        Text(
                                            subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    onSelectTargetCalendar(cal.id, cal.displayName)
                                    calendarDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = {
                    if (state.hasCalendarPermission) {
                        onRefreshCalendars()
                    } else {
                        onRequestCalendarPermission()
                    }
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新系统日历列表")
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        CalendarSwitchRow(
            icon = Icons.Default.AutoMode,
            title = "任务变更时自动同步到系统日历",
            description = "新建、修改、完成或删除任务时自动更新系统日历事件（需已授予日历权限）",
            checked = state.autoPushEnabled,
            onCheckedChange = onAutoPushChange
        )

        CalendarSwitchRow(
            icon = Icons.Default.NotificationsActive,
            title = "同步任务提醒时间",
            description = "导出日历文件或同步到手机日历时附带提醒；从外部导入日程时自动设置软件内闹钟提醒",
            checked = state.syncReminders,
            onCheckedChange = onSyncRemindersChange
        )

        CalendarSwitchRow(
            icon = Icons.Default.DoneAll,
            title = "同步手机日历时包含已完成 / 已过期任务",
            description = "关闭后只把进行中的任务同步到手机日历；已同步的任务完成时仍会自动加上 [已完成] 标记",
            checked = state.pushIncludeDone,
            onCheckedChange = onPushIncludeDoneChange
        )

        CalendarSwitchRow(
            icon = Icons.Default.EventNote,
            title = "导出日历文件时包含已完成 / 已过期任务",
            description = "关闭后只导出当前还在进行中的任务日程",
            checked = state.icsExportIncludeDone,
            onCheckedChange = onIcsExportIncludeDoneChange
        )

        CalendarSwitchRow(
            icon = Icons.Default.AutoAwesome,
            title = "智能规划读取系统日历忙碌时段",
            description = "生成每日日程建议时结合手机日历日程避开时间冲突（需先授予日历权限）",
            checked = state.scheduleAdviceUseCalendar,
            onCheckedChange = onScheduleAdviceUseCalendarChange
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 导入未来日程跨度
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "系统日历导入范围",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "从系统日历读取并导入未来指定天数内的外部日程",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PULL_DAYS_OPTIONS.forEach { days ->
                    FilterChip(
                        selected = state.pullDaysAhead == days,
                        onClick = { onPullDaysAheadChange(days) },
                        label = { Text("${days}天", style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 最近同步记录与详细说明入口
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "上次推送：${formatSyncTime(state.lastPushAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "上次导入：${formatSyncTime(state.lastPullAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpenGuide) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("同步说明与规则")
            }
        }
    }
}

@Composable
private fun CalendarSwitchRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
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
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 通用日历文件与手机系统日历双向同步完整说明弹窗。
 */
@Composable
fun CalendarSyncGuideDialog(
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "日历导出与同步使用说明",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GuideSectionBlock(
                    title = "1. 通用日历文件导出与导入",
                    items = listOf(
                        "• 导出给其他日历：把任务标题、备注、子任务打勾进度、分类、起止时间和提醒时间打包成通用日历文件（后缀 .ics），可直接在苹果日历、Google 日历、Outlook 等日历软件里打开导入。",
                        "• 从其他日历导入：导入外部日历文件时，系统会自动识别全天日程、跨时区时间和提前提醒设置，并按名称匹配分类及设置软件内提醒闹钟。",
                        "• 控制导出范围：可在下方开关中选择导出全部任务，或只导出还在进行中的任务。"
                    )
                )

                HorizontalDivider()

                GuideSectionBlock(
                    title = "2. 手机系统日历双向同步",
                    items = listOf(
                        "• 选择写入的日历：支持自选同步到手机里的哪个日历账户；如果设备里还没有可用日历，软件会自动新建一个名为「LiteTask」的本地日历。",
                        "• 更新不会产生重复项：已推送到手机日历的任务，下次修改时间、标题或设为完成后再同步时，会直接更新手机日历里原来的那条日程，不会越传越多；已完成任务会自动加上 [已完成] 标记。",
                        "• 从手机日历读回日程：会读取未来 7/14/30/90 天内手机日历里的外部日程安排（包含每天或每周重复的日程实例），并自动过滤原本就是由 LiteTask 传过去的事件。",
                        "• 自动防止重复导入：无论从日历文件还是手机日历导入，只要软件里已经有同名且开始时间相差不到 1 分钟的任务，就会自动跳过。"
                    )
                )

                HorizontalDivider()

                GuideSectionBlock(
                    title = "3. 隐私权限与手机系统设置提示",
                    items = listOf(
                        "• 本地直传隐私安全：日历读取和写入权限只用于在您手机本地的 LiteTask 与系统日历之间交换日程，不会把日历内容上传到外部服务器。",
                        "• 手机日历里没看到日程怎么办：若使用的是自动新建的「LiteTask」本地日历，请打开手机自带日历的「日历账户 / 显示管理」，勾选开启「LiteTask」日历的显示开关。",
                        "• 跨设备同步建议：在小米、OPPO、vivo、华为等系统上，请确保权限管理里已允许 LiteTask「读写日历」；若希望日程随系统账号同步到其他设备，请在下方把目标日历选成您已经开启云同步的系统日历。"
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我已了解")
            }
        }
    )
}

@Composable
private fun GuideSectionBlock(
    title: String,
    items: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        items.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.35f
            )
        }
    }
}

fun formatSyncTime(timestamp: Long): String {
    if (timestamp <= 0L) return "尚未记录"
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
