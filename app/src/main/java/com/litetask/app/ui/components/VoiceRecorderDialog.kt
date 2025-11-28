package com.litetask.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun VoiceRecorderDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false) // 全屏覆盖
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1F1F1F).copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                // 录音动画
                RecordingAnimation()
                
                Spacer(modifier = Modifier.height(40.dp))
                
                Text(
                    text = "正在聆听...",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "\"帮我把下午5点的会议加进去,持续一小时,是关于Q4预算的...\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFC4C7C5),
                    textAlign = TextAlign.Center
                )
            }
            
            // 关闭按钮
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                // X 图标
                Text(
                    text = "✕",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            
            // 说完了按钮
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
                    .background(Color(0xFFE3E3E3), CircleShape)
                    .clickable { /* TODO: 完成录音 */ }
                    .padding(horizontal = 40.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "说完了",
                    color = Color(0xFF1F1F1F),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun RecordingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    // 第一个脉冲
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale1"
    )
    
    // 第二个脉冲
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, delayMillis = 300),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale2"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(120.dp)
    ) {
        // 外层脉冲环
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale1)
                .background(Color(0xFFA8C7FA).copy(alpha = 0.2f), CircleShape)
        )
        
        // 中间脉冲环
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale2)
                .background(Color(0xFFA8C7FA).copy(alpha = 0.1f), CircleShape)
        )
        
        // 内层麦克风按钮
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color(0xFF0B57D0), Color(0xFF0842A0))
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VoiceRecorderDialogPreview() {
    MaterialTheme {
        VoiceRecorderDialog(
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RecordingAnimationPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1F1F1F)),
            contentAlignment = Alignment.Center
        ) {
            RecordingAnimation()
        }
    }
}