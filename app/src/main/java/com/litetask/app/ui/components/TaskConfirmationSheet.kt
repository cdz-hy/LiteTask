package com.litetask.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.litetask.app.data.model.Task
import com.litetask.app.ui.theme.Indigo600
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskConfirmationSheet(
    tasks: List<Task>,
   onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        tonalElevation = 0.dp
) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // 拖拽指示器
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical =8.dp)
                    .size(48.dp, 6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFC4C7C5))
            )
            
            // 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical =16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                            ),
                            shape =CircleShape
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 使用 Flag 图标替代 Sparkles
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                
                Column(modifier =Modifier.padding(start = 12.dp)) {
                    Text(
                        text = "AI 识别结果",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F1F1F)
                    )
                    Text(
                        text = "已解析关键信息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF444746)
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f, fill =false)
                    .padding(vertical = 8.dp)
            ) {
                items(tasks) { task ->
                    TaskPreviewItem(task)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Indigo600
                    )
                ) {
                    Text("取消")
                }
                Button(
                    onClick= onConfirm,
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Indigo600,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        verticalAlignment= Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "确认添加",
                            modifier = Modifier.padding(start= 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaskPreviewItem(task: Task) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF0F4F8), MaterialTheme.shapes.medium)
            .padding(16.dp)
    ) {
        Text(
            text = task.title,
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF1F1F1F),
            fontWeight = FontWeight.Medium
        )
        
        Row(
            modifier = Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = null,
                tint = Indigo600,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "${formatPreviewTime(task.startTime)}- ${formatPreviewTime(task.deadline)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF444746)
            )
        }
        
        // 位置信息已移除，因为新的 Task 模型中不再包含该字段
        /*
        if (task.location != null) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFF444746),
                   modifier = Modifier.size(16.dp)
                )
                Text(
                    text = task.location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF1F1F1F),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        */

        //时长信息
        val durationMillis = task.deadline - task.startTime
        val durationHours = durationMillis / (1000 * 60 * 60)
        Text(
            text = "时长: ${durationHours} 小时",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF444746),
            modifier = Modifier.padding(top = 8.dp)
        )

        // 如果有截止日期
        if (isUrgentTask(task)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top= 12.dp)
                    .background(Color(0xFFFFD8E4), MaterialTheme.shapes.small)
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = Color(0xFF31111D),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "紧急任务",
                       style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF31111D),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

fun formatPreviewTime(timestamp: Long): String {
    val calendar =Calendar.getInstance()
    calendar.timeInMillis = timestamp
    return SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(Date(timestamp))
}

fun calculateDurationInHours(task: Task): Float {
    val durationMillis = task.deadline - task.startTime
    return durationMillis / (1000f * 60* 60)
}

fun isUrgentTask(task: Task): Boolean {
    // 简单判断逻辑：如果有截止时间且距离现在不到24小时，则认为是紧急任务
    val currentTime = System.currentTimeMillis()
    return task.deadline > 0 && task.deadline - currentTime <24 * 60 * 60 * 1000
}