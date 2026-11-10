package com.example.livex.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.livex.player.PlayerIntent
import com.example.livex.player.ads.AdBreak
import com.example.livex.player.ads.VodProgress
import com.example.livex.ui.theme.LiveXTheme

const val VOD_BAR_TAG = "vod_progress_bar"

private const val SEEK_STEP_MS = 10_000L

private val Accent = Color(0xFF80CBC4)
private val Muted = Color(0xFFB0BEC5)
private val AdYellow = Color(0xFFFFD54F)
private val AdWatched = Color(0xFF9E9E9E)

/**
 * VOD seek bar with ad break markers: yellow = unwatched (seeking past it plays it first),
 * grey = watched. Left/Right seek ±10 s; the engine's AdSeekPolicy decides what actually happens.
 */
@Composable
fun VodProgressBar(progress: VodProgress, onIntent: (PlayerIntent) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(progress.timeLabel, style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(progress.adSummary, style = MaterialTheme.typography.labelMedium, color = Muted)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .testTag(VOD_BAR_TAG)
                .semantics { contentDescription = "Progress ${progress.timeLabel}, ${progress.adSummary}" }
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> { onIntent(PlayerIntent.SeekBy(-SEEK_STEP_MS)); true }
                        Key.DirectionRight -> { onIntent(PlayerIntent.SeekBy(SEEK_STEP_MS)); true }
                        else -> false
                    }
                }
                .focusable()
                .border(2.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(9.dp))
                .padding(horizontal = 6.dp, vertical = 5.dp),
        ) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(Color.White.copy(alpha = 0.2f), cornerRadius = radius)
            drawRoundRect(Accent, size = Size(size.width * progress.positionFraction, size.height), cornerRadius = radius)
            // Ad break markers: a tall tick at each break's content position.
            progress.adBreaks.forEach { adBreak ->
                val x = (size.width * progress.fractionOf(adBreak)).coerceIn(2f, size.width - 2f)
                drawRect(
                    if (adBreak.isPlayed) AdWatched else AdYellow,
                    topLeft = Offset(x - 2.dp.toPx(), -4f),
                    size = Size(4.dp.toPx(), size.height + 8f),
                )
            }
            drawCircle(Color.White, radius = size.height * 0.9f, center = Offset(size.width * progress.positionFraction, size.height / 2))
        }
    }
}

@Preview(widthDp = 520, heightDp = 110, backgroundColor = 0xFF445566, showBackground = true)
@Composable
private fun VodProgressBarPreview() {
    LiveXTheme {
        VodProgressBar(
            progress = VodProgress(
                positionMs = 200_000,
                durationMs = 734_000,
                adBreaks = listOf(AdBreak(0, true), AdBreak(180_000, true), AdBreak(360_000, false), AdBreak(AdBreak.POST_ROLL, false)),
            ),
            onIntent = {},
            modifier = Modifier.padding(12.dp),
        )
    }
}
