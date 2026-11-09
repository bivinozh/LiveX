package com.example.livex.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.livex.qoe.BufferHistory
import com.example.livex.qoe.BufferPoint
import java.util.Locale
import kotlin.math.ceil

/** Accessibility label of the chart (also how tests find it). */
const val BUFFER_CHART_DESCRIPTION = "Buffer level chart"

private val LineColor = Color(0xFF80CBC4)
private val FillColor = Color(0xFF80CBC4).copy(alpha = 0.18f)
private val StallColor = Color(0xFFEF5350).copy(alpha = 0.55f)
private val GridColor = Color.White.copy(alpha = 0.15f)
private val AxisLabelColor = Color(0xFFB0BEC5)

/**
 * Buffer level over the last 60 s. X = time (right edge = now), Y = seconds buffered ahead.
 * Red bands mark stalls: the line touches zero, then playback waits for it to climb again.
 */
@Composable
fun BufferChart(points: List<BufferPoint>, modifier: Modifier = Modifier) {
    // Y scale: at least 10 s, rounded up to a multiple of 10 s so the axis doesn't jitter.
    val maxBufferedMs = points.maxOfOrNull { it.bufferedMs } ?: 0L
    val yMaxMs = (ceil(maxOf(maxBufferedMs, 10_000L) / 10_000.0) * 10_000).toLong()

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ChartLabel("Buffer, last 60 s")
            ChartLabel(String.format(Locale.US, "max %d s", yMaxMs / 1000))
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .semantics { contentDescription = BUFFER_CHART_DESCRIPTION },
        ) {
            val windowMs = BufferHistory.DEFAULT_WINDOW_MS.toFloat()
            val nowMs = points.lastOrNull()?.atMs ?: 0L
            fun x(atMs: Long) = size.width * (1f - (nowMs - atMs) / windowMs)
            fun y(bufferedMs: Long) = size.height * (1f - bufferedMs.toFloat() / yMaxMs)

            // Grid: 0 s, half, max.
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val gy = size.height * fraction
                drawLine(GridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            }
            if (points.size < 2) return@Canvas

            // Stall bands: one band per sample interval that was stalled.
            points.zipWithNext().forEach { (a, b) ->
                if (a.isStalled || b.isStalled) {
                    drawRect(StallColor, topLeft = Offset(x(a.atMs), 0f), size = Size(x(b.atMs) - x(a.atMs), size.height))
                }
            }

            val line = Path().apply {
                points.forEachIndexed { i, p ->
                    if (i == 0) moveTo(x(p.atMs), y(p.bufferedMs)) else lineTo(x(p.atMs), y(p.bufferedMs))
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(x(points.last().atMs), size.height)
                lineTo(x(points.first().atMs), size.height)
                close()
            }
            drawPath(fill, FillColor)
            drawPath(line, LineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ChartLabel("-60 s")
            ChartLabel("now")
        }
    }
}

@Composable
private fun ChartLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = AxisLabelColor)
}
