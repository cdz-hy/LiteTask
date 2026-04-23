package com.litetask.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.litetask.app.data.model.DailyPlanEntity
import com.litetask.app.data.model.PlanSuggestionEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSuggestionSheet(
    plan: DailyPlanEntity,
    suggestions: List<PlanSuggestionEntity>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp) // 给底部留出安全区
        ) {
            Text(
                text = "今日日程建议与规划",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = "生成时间: ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(plan.generatedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 总体摘要
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "分析摘要",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = plan.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Text(
                text = "详细建议",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (suggestions.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("当前暂无具体建议", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(suggestions) { suggestion ->
                        SuggestionItemCard(suggestion)
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionItemCard(suggestion: PlanSuggestionEntity) {
    val isWarning = suggestion.priority > 5 || suggestion.suggestionType.contains("WARNING")
    val bgColor = if (isWarning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isWarning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val icon = if (isWarning) Icons.Default.Warning else Icons.Default.TipsAndUpdates

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp).padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = suggestion.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = suggestion.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
            }
        }
    }
}
