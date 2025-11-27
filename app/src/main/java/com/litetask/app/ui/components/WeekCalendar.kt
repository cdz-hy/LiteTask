package com.litetask.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litetask.app.ui.theme.Primary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun WeekCalendar(
    selectedDate: Long,
    onDateSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val calendar = Calendar.getInstance()
    // 简单的周视图逻辑：显示当前周的7天
    // 实际项目中可能需要 InfinitePager
    val days = (0..6).map { i ->
        val c = calendar.clone() as Calendar
        c.set(Calendar.DAY_OF_WEEK, c.firstDayOfWeek + i)
        c
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        days.forEach { date ->
            val isSelected = isSameDay(date.timeInMillis, selectedDate)
            DayItem(
                date = date,
                isSelected = isSelected,
                onClick = { onDateSelected(date.timeInMillis) }
            )
        }
    }
}

@Composable
fun DayItem(
    date: Calendar,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dayOfWeek = SimpleDateFormat("E", Locale.getDefault()).format(date.time).first().toString()
    val dayOfMonth = date.get(Calendar.DAY_OF_MONTH).toString()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(if (isSelected) Primary else Color.Transparent)
            .padding(vertical = 8.dp, horizontal = 12.dp)
            .width(24.dp) // 固定宽度保持对齐
    ) {
        Text(
            text = dayOfWeek,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) Color.White.copy(alpha = 0.8f) else Color(0xFF747775)
        )
        Text(
            text = dayOfMonth,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (isSelected) Color.White else Color(0xFF1F1F1F)
        )
        
        if (isSelected) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(4.dp)
                    .background(Color.White, CircleShape)
            )
        }
    }
}

fun isSameDay(date1: Long, date2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = date1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = date2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
           c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}