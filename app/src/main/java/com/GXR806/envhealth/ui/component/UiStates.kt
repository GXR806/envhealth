package com.GXR806.envhealth.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp)) {
        Text("暂无数据", style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().semantics { error(message); liveRegion = LiveRegionMode.Polite }.padding(16.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("重试") }
    }
}

/** 仅加载阶段运行透明度动画，无无限 Canvas 绘制。 */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "加载")
    val alpha by transition.animateFloat(0.35f, 0.8f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "骨架透明度")
    Column(modifier.fillMaxWidth().semantics { contentDescription = "正在加载数据" }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            Box(Modifier.fillMaxWidth().height(32.dp).alpha(alpha)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small))
        }
    }
}
