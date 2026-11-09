package com.example.livex.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.livex.data.Optimization
import com.example.livex.player.PlayerIntent
import com.example.livex.player.PlayerState
import com.example.livex.qoe.MetricKey
import com.example.livex.qoe.QoeMetrics
import com.example.livex.qoe.StallStats
import com.example.livex.qoe.formatted
import com.example.livex.ui.theme.LiveXTheme

private val PanelBackground = Color.Black.copy(alpha = 0.8f)
private val Accent = Color(0xFF80CBC4)
private val Muted = Color(0xFFB0BEC5)

/** Width of the guide panel on the right; the status overlay centers itself in the space left of it. */
val GuidePanelWidth = 460.dp

/** Stateless guide drawn on top of the video: topic description, guidance, optimization demo and live metrics. */
@Composable
fun GuideOverlay(
    state: PlayerState,
    onIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (state.isGuideVisible) {
            GuidePanel(state, onIntent, Modifier.align(Alignment.CenterEnd))
        } else {
            ShowGuideButton(onIntent, Modifier.align(Alignment.BottomEnd).padding(32.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GuidePanel(state: PlayerState, onIntent: (PlayerIntent) -> Unit, modifier: Modifier) {
    val topic = state.topic
    val nextFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { nextFocus.requestFocus() }

    Column(
        modifier = modifier
            .width(GuidePanelWidth)
            .fillMaxHeight()
            .background(PanelBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VideoInfo(state, onIntent)

        Text(
            "Topic ${state.topicIndex + 1} / ${state.topics.size}",
            style = MaterialTheme.typography.labelMedium,
            color = Muted,
        )
        Text(topic.title, style = MaterialTheme.typography.titleLarge, color = Color.White)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onIntent(PlayerIntent.PreviousTopic) }) { Text("◀ Prev") }
            Button(
                onClick = { onIntent(PlayerIntent.NextTopic) },
                modifier = Modifier.focusRequester(nextFocus),
            ) { Text("Next ▶") }
            Button(onClick = { onIntent(PlayerIntent.ToggleGuide) }) { Text("Hide") }
        }

        topic.optimization?.let { OptimizationSection(it, state.isOptimizationEnabled, onIntent) }

        SectionTitle("Live metrics")
        MetricsTable(state.metrics, topic.metrics)

        SectionTitle("About")
        BodyText(topic.description)

        SectionTitle("Guidance")
        topic.guidance.forEach { BodyText("• $it") }
    }
}

/** Which stream is playing. To change it, press Back and pick another one from the list. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VideoInfo(state: PlayerState, onIntent: (PlayerIntent) -> Unit) {
    Text(state.stream.formatLabel, style = MaterialTheme.typography.labelMedium, color = Muted)
    Text(state.stream.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onIntent(PlayerIntent.OpenManifest) }) { Text("View manifest") }
        Button(onClick = { onIntent(PlayerIntent.ToggleStats) }) {
            Text(if (state.isStatsVisible) "Stats: ON" else "Stats: OFF")
        }
    }
    // A 60 s jump usually lands beyond the buffer: watch "Seek buffering" (not "Stalls") go up.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onIntent(PlayerIntent.SeekBy(-10_000)) }) { Text("⏪ 10 s") }
        Button(onClick = { onIntent(PlayerIntent.SeekBy(60_000)) }) { Text("60 s ⏩") }
    }
    // Throttle simulator: watch Est. bandwidth fall, Resolution step down (ABR) and, if the buffer
    // can't keep up, Stalls and red bands in the buffer chart.
    NetworkDropdown(mode = state.throttleMode, onSelect = { onIntent(PlayerIntent.SetThrottleMode(it)) })
    Spacer(Modifier.height(4.dp))
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OptimizationSection(optimization: Optimization, enabled: Boolean, onIntent: (PlayerIntent) -> Unit) {
    SectionTitle("Optimization demo")
    Button(onClick = { onIntent(PlayerIntent.ToggleOptimization) }) {
        Text(if (enabled) "Optimized: ON" else "Optimized: OFF")
    }
    BodyText(
        "Now: " + if (enabled) optimization.optimizedLabel else optimization.defaultLabel,
        color = Accent,
    )
    BodyText(
        "Switch to: " + if (enabled) optimization.defaultLabel else optimization.optimizedLabel,
        color = Muted,
    )
    BodyText("Trade-off: ${optimization.tradeOff}", color = Muted)
}

@Composable
private fun MetricsTable(metrics: QoeMetrics, keys: List<MetricKey>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        keys.forEach { key ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(key.label, style = MaterialTheme.typography.bodySmall, color = Muted)
                Text(
                    metrics.formatted(key),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, color = Accent)
}

@Composable
private fun BodyText(text: String, color: Color = Color.White) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ShowGuideButton(onIntent: (PlayerIntent) -> Unit, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Button(
        onClick = { onIntent(PlayerIntent.ToggleGuide) },
        modifier = modifier.focusRequester(focus),
    ) { Text("Show guide") }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun GuideOverlayPreview() {
    LiveXTheme {
        GuideOverlay(
            state = PlayerState(
                topicIndex = 1,
                metrics = QoeMetrics(
                    playbackState = "Ready",
                    bufferedDurationMs = 21_400,
                    stalls = StallStats(startupTimeMs = 840),
                ),
            ),
            onIntent = {},
        )
    }
}
