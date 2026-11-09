package com.example.livex.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.livex.qoe.BufferPoint
import com.example.livex.qoe.StatsState
import com.example.livex.ui.theme.LiveXTheme

private val LabelColor = Color(0xFFB0BEC5)
private val TitleColor = Color(0xFF80CBC4)

/**
 * "Stats for nerds"-style panel drawn over the video. Stateless and not focusable: it's purely
 * informational, so it never steals D-pad focus from the guide or the error card.
 */
@Composable
fun StatsOverlay(stats: StatsState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(280.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("Stats", style = MaterialTheme.typography.labelLarge, color = TitleColor)
        stats.rows().forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = LabelColor,
                )
                Text(
                    row.value,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        BufferChart(stats.bufferHistory)
        if (stats.recentSwitches.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Quality switches", style = MaterialTheme.typography.labelMedium, color = TitleColor)
            // Newest last; the full lines (with bitrates) are in Logcat, tag LiveX-ABR.
            stats.recentSwitches.takeLast(3).forEach { switch ->
                Text(
                    switch.compact(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                )
            }
        }
    }
}

@Preview(widthDp = 320, heightDp = 460, backgroundColor = 0xFF556677, showBackground = true)
@Composable
private fun StatsOverlayPreview() {
    LiveXTheme {
        StatsOverlay(
            StatsState(
                playerState = "Ready",
                videoWidth = 1280,
                videoHeight = 720,
                videoBitrateBps = 2_500_000,
                bandwidthEstimateBps = 8_400_000,
                bufferedDurationMs = 21_400,
                liveOffsetMs = 6_200,
                playbackSpeed = 1.01f,
                droppedFrames = 3,
                bufferHistory = (0..120).map { i ->
                    val stalled = i in 70..78
                    BufferPoint(
                        atMs = i * 500L,
                        bufferedMs = if (stalled) 0 else minOf(i * 400L, 30_000L) - if (i > 78) 20_000 else 0,
                        isStalled = stalled,
                    )
                },
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
