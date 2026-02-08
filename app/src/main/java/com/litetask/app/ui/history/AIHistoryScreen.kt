package com.litetask.app.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.litetask.app.R
import com.litetask.app.data.model.AIHistory
import com.litetask.app.data.model.AIHistorySource
import com.litetask.app.ui.theme.LocalExtendedColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIHistoryScreen(
    onBack: () -> Unit,
    viewModel: AIHistoryViewModel = hiltViewModel()
) {
    val historyItems by viewModel.historyItems.collectAsState()
    val context = LocalContext.current
    val extendedColors = LocalExtendedColors.current
    
    var showClearConfirm by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<AIHistory?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = stringResource(R.string.ai_history),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (historyItems.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(
                                Icons.Default.DeleteSweep, 
                                contentDescription = stringResource(R.string.ai_history_clear_all),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (historyItems.isEmpty()) {
                EmptyHistoryView()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = historyItems,
                        key = { it.id }
                    ) { item ->
                        AIHistoryCard(
                            item = item,
                            onCopy = {
                                copyToClipboard(context, item.content)
                                Toast.makeText(context, R.string.ai_history_copy_success, Toast.LENGTH_SHORT).show()
                            },
                            onDelete = { itemToDelete = item }
                        )
                    }
                }
            }
        }
    }

    // 清空确认对话框
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.ai_history_clear_all)) },
            text = { Text(stringResource(R.string.ai_history_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 单条删除确认
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.ai_history_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteHistory(item)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun AIHistoryCard(
    item: AIHistory,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val extendedColors = LocalExtendedColors.current
    val (sourceName, sourceIcon, sourceColor) = getSourceDisplayInfo(item.sourceType)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Source + Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = sourceColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = sourceIcon,
                            contentDescription = null,
                            tint = sourceColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = sourceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = sourceColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = formatHistoryTime(item.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = extendedColors.textTertiary
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Success/Fail indicator
                if (!item.isSuccess) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = "Failed",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Content
            Text(
                text = item.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Footer: Parsed Count + Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (item.parsedCount > 0) {
                    val countText = if (item.sourceType == AIHistorySource.SUBTASK) {
                        stringResource(R.string.ai_history_parsed_subtasks, item.parsedCount)
                    } else {
                        stringResource(R.string.ai_history_parsed_tasks, item.parsedCount)
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelSmall,
                        color = extendedColors.textTertiary
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Action Buttons
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = extendedColors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryView() {
    val extendedColors = LocalExtendedColors.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = extendedColors.textTertiary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.ai_history_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = extendedColors.textTertiary
        )
    }
}

@Composable
private fun getSourceDisplayInfo(source: AIHistorySource): Triple<String, ImageVector, Color> {
    val extendedColors = LocalExtendedColors.current
    return when (source) {
        AIHistorySource.VOICE -> Triple(
            stringResource(R.string.ai_history_source_voice),
            Icons.Default.Mic,
            extendedColors.lifeTask
        )
        AIHistorySource.TEXT -> Triple(
            stringResource(R.string.ai_history_source_text),
            Icons.Default.TextFields,
            extendedColors.workTask
        )
        AIHistorySource.SUBTASK -> Triple(
            stringResource(R.string.ai_history_source_subtask),
            Icons.Default.ListAlt,
            extendedColors.studyTask
        )
    }
}

private fun formatHistoryTime(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(date)
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("AI Content", text)
    clipboard.setPrimaryClip(clip)
}
