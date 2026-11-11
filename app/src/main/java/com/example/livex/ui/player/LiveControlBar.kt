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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.livex.player.PlayerIntent
import com.example.livex.player.live.LiveLatency
import com.example.livex.player.live.LiveWindow
import com.example.livex.ui.theme.LiveXTheme

const val DVR_BAR_TAG = "dvr_seek_bar"

/** D-pad seek step inside the live window. */
private const val DVR_STEP_MS = 10_000L

private val LiveRed = Color(0xFFE53935)
private val Accent = Color(0xFF80CBC4)
private val Muted = Color(0xFFB0BEC5)

/**
 * Bottom bar for live streams: LIVE status / how far behind, GO LIVE, the DVR seek bar (Left/Right
 * = ±10 s) and the target-latency choice. Stateless; [live] is sampled every 500 ms by the engine.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveControlBar(
    live: LiveWindow,
    latency: LiveLatency,
    onIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onPlayPause: () -> Unit = {},
    seekBarFocus: FocusRequester = remember { FocusRequester() },
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Three short rows: the video area left of the guide is only ~450 dp wide on a 960 dp TV.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Pausing live = DVR: the position falls behind the edge; GO LIVE catches up.
            PlayPauseButton(isPlaying, onPlayPause)
            Text(
                live.statusText,
                style = MaterialTheme.typography.titleSmall,
                color = if (live.isAtLiveEdge) LiveRed else Color.White,
            )
            Button(onClick = { onIntent(PlayerIntent.GoLive) }) { Text("GO LIVE") }
            Text(live.windowLabel, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Target latency", style = MaterialTheme.typography.labelMedium, color = Muted)
            LiveLatency.entries.forEach { option ->
                val selected = option == latency
                Button(onClick = { if (!selected) onIntent(PlayerIntent.SetLiveLatency(option)) }) {
                    Text(if (selected) "✓ ${option.label}" else option.label)
                }
            }
        }
        // Tick = GO LIVE point (live edge − target offset).
        DvrSeekBar(live, onSeekBy = { onIntent(PlayerIntent.SeekBy(it)) }, onPlayPause, seekBarFocus)
    }
}

/** Track = the DVR window; fill = playhead; tick = default position (live edge − target offset). */
@Composable
private fun DvrSeekBar(
    live: LiveWindow,
    onSeekBy: (Long) -> Unit,
    onPlayPause: () -> Unit,
    focusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .testTag(DVR_BAR_TAG)
            .semantics { contentDescription = "DVR seek bar: ${live.statusText}" }
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeekBy(-DVR_STEP_MS); true }
                    Key.DirectionRight -> { onSeekBy(DVR_STEP_MS); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onPlayPause(); true }
                    else -> false
                }
            }
            .focusable()
            .border(2.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(9.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(Color.White.copy(alpha = 0.2f), cornerRadius = radius)
        drawRoundRect(
            if (live.isAtLiveEdge) LiveRed else Accent,
            size = Size(size.width * live.positionFraction, size.height),
            cornerRadius = radius,
        )
        val edgeX = size.width * live.liveEdgeFraction
        drawLine(Color.White, Offset(edgeX, -4f), Offset(edgeX, size.height + 4f), strokeWidth = 3f)
        // Playhead knob.
        drawCircle(Color.White, radius = size.height * 0.9f, center = Offset(size.width * live.positionFraction, size.height / 2))
    }
}

@Preview(widthDp = 560, heightDp = 160, backgroundColor = 0xFF445566, showBackground = true)
@Composable
private fun LiveControlBarPreview() {
    LiveXTheme {
        LiveControlBar(
            live = LiveWindow(windowDurationMs = 120_000, positionMs = 80_000, defaultPositionMs = 110_000, targetOffsetMs = 10_000),
            latency = LiveLatency.STANDARD,
            onIntent = {},
            modifier = Modifier.padding(12.dp),
        )
    }
}
