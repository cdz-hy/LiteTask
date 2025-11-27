package com.litetask.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.litetask.app.data.model.Task
import com.litetask.app.ui.theme.Primary
import java.util.Calendar

@Composable
fun DeadlineView(
    tasks: List<Task>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF2F6FC))
            .padding(horizontal = 16.dp)
    ) {
        // 紧迫任务头部摘要
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF3E4855), MaterialTheme.shapes.large)
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = "紧迫任务",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "今天有 ${tasks.count { it.deadline != null }} 个任务必须完成",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                
                // 进度条
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(top = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .fillMaxWidth(0.4f) // 示例进度40%
                            .background(Color(0xFFF43F5E))
                    )
                }
            }
        }
        
        // 倒计时列表标题
        item {
            Text(
                text = "Counting Down",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF747775),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 24.dp, bottom = 12.dp)
                    .fillMaxWidth()
            )
        }
        
        // 有截止日期的任务
        items(tasks.filter { it.deadline != null }.sortedBy { it.deadline }) { task ->
            CountdownTaskItem(task = task)
        }
        
        // 稍后安排标题
        item {
            Text(
                text = "Later",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF747775),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 24.dp, bottom = 12.dp)
                    .fillMaxWidth()
            )
        }
        
        // 无截止日期的任务
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                tasks.filter { it.deadline == null && !it.isDone }.forEach { task ->
                    LaterTaskItem(task = task)
                }
            }
        }
        
        // 底部填充空间
        item {
            Box(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun CountdownTaskItem(task: Task) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Color.White, MaterialTheme.shapes.large)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 视觉倒计时圆环（简化为静态展示）
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFFE3E3E3), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "3",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF43F5E)
                    )
                    Text(
                        text = "HRS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF43F5E)
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF1F1F1F)
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = Color(0xFFF43F5E),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "截止: ${formatDeadlineTime(task.deadline!!)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF747775),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFEEF2F6), CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = Primary
                )
            }
        }
    }
}

@Composable
fun LaterTaskItem(task: Task) {
    Box(
        modifier = Modifier
            .weight(1f)
            .background(Color.White, MaterialTheme.shapes.large)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = task.title,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF444746),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Box(
                modifier = Modifier
                    .background(Color(0xFFEEF2F6), MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "无具体截止",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF747775)
                )
            }
        }
    }
}

fun formatDeadlineTime(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    return String.format("%02d:%02d", hour, minute)
}