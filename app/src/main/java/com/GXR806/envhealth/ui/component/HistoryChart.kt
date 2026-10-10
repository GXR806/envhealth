package com.GXR806.envhealth.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.GXR806.envhealth.domain.analysis.ChartPoint
import kotlin.math.max

/** Canvas 只接收聚合后的有限点，不在绘制阶段查询、聚合或启动动画。 */
@Composable
fun HistoryChart(title: String, unit: String, points: List<ChartPoint>, bars: Boolean = false, stacked: Boolean = false) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(unit, style = MaterialTheme.typography.bodySmall)
            if (points.none { it.value != null }) {
                EmptyState("所选时间范围没有已保存的数据")
            } else {
                val primary = MaterialTheme.colorScheme.primary
                val secondary = MaterialTheme.colorScheme.secondary
                val grid = MaterialTheme.colorScheme.outlineVariant
                val ceiling = max(1f, points.maxOf { (it.value ?: 0f) + if (stacked) it.secondary else 0f })
                val description = "$title，$unit；" + points.joinToString("；") {
                    "${it.label}：" + (it.value?.let { value ->
                        if (stacked) "有线或USB %.1f，蓝牙 %.1f".format(value, it.secondary)
                        else "%.1f".format(value)
                    } ?: "无数据")
                }
                Text("纵轴 0–${"%.1f".format(ceiling)}", style = MaterialTheme.typography.labelSmall)
                Canvas(Modifier.fillMaxWidth().height(156.dp).semantics { contentDescription = description }) {
                    val step = size.width / points.size.coerceAtLeast(1)
                    fun y(v: Float) = size.height * (1 - v / ceiling)
                    repeat(3) { i -> val level = size.height * i / 2f
                        drawLine(grid, Offset(0f, level), Offset(size.width, level), 1.dp.toPx()) }
                    points.forEachIndexed { index, point -> point.value?.let { value ->
                        val x = step * (index + 0.5f)
                        if (bars) {
                            val width = step * 0.62f
                            drawRect(primary, Offset(x - width / 2, y(value)), Size(width, size.height - y(value)))
                            if (stacked && point.secondary > 0) drawRect(secondary,
                                Offset(x - width / 2, y(value + point.secondary)), Size(width, size.height * point.secondary / ceiling))
                        } else {
                            drawCircle(primary, 3.dp.toPx(), Offset(x, y(value)))
                            if (index > 0) points[index - 1].value?.let { prev ->
                                drawLine(primary, Offset(x - step, y(prev)), Offset(x, y(value)), 2.dp.toPx())
                            }
                        }
                    } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(points.first().label, style = MaterialTheme.typography.labelSmall)
                    Text(points.last().label, style = MaterialTheme.typography.labelSmall)
                }
                if (stacked) Text("青绿：有线/USB · 蓝色：蓝牙", style = MaterialTheme.typography.bodySmall)
                if (bars && !stacked) Text(points.joinToString(" · ") { "${it.label}：${it.value?.toInt() ?: 0}" },
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
