package com.example.livex.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.livex.player.drm.WidevineInfo
import com.example.livex.ui.theme.LiveXTheme

private val Good = Color(0xFF81C784)
private val Bad = Color(0xFFFF8A80)
private val Muted = Color(0xFFB0BEC5)

@Composable
fun DeviceInfoScreen(modifier: Modifier = Modifier, viewModel: DeviceInfoViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DeviceInfoContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

/**
 * Sections are focusable cards: on TV, a list only scrolls when focus moves through it, so
 * read-only content still needs focus targets. 10-foot UI: few large lines, high contrast.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DeviceInfoContent(state: DeviceInfoState, onIntent: (DeviceInfoIntent) -> Unit, modifier: Modifier = Modifier) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(state.info != null) {
        if (state.info == null) return@LaunchedEffect
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Device info", style = MaterialTheme.typography.headlineSmall)
                    state.info?.let {
                        Text(
                            "${it.model} · Android API ${it.sdkInt} · ${if (it.isTelevision) "TV" else "phone/tablet"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                }
                Button(onClick = { onIntent(DeviceInfoIntent.Refresh) }) { Text("Refresh") }
            }
        }
        val info = state.info
        when {
            state.isLoading && info == null -> item { Text("Reading MediaCodecList…") }
            state.error != null -> item { Text(state.error, color = Bad) }
            info != null -> {
                item { Section("What this device can stream", info.summaryRows(), Modifier.focusRequester(firstFocus)) }
                item {
                    Section(
                        "Video decoders (MediaCodecList)",
                        info.codecs.map { InfoRow(it.codec.label, it.featureText, it.isHardware) },
                    )
                }
                item { Section("Display", info.displayRows()) }
                item { Section("Memory", info.memoryRows()) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Section(title: String, rows: List<InfoRow>, modifier: Modifier = Modifier) {
    Surface(onClick = {}, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        row.value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = when (row.isGood) {
                            true -> Good
                            false -> Bad
                            null -> Color.Unspecified
                        },
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun DeviceInfoPreview() {
    LiveXTheme {
        DeviceInfoContent(
            state = DeviceInfoState(
                isLoading = false,
                info = DeviceInfo(
                    model = "Google Chromecast",
                    sdkInt = 36,
                    isTelevision = true,
                    codecs = CodecSupport.merge(
                        listOf(
                            DecoderEntry(VideoCodec.AVC, "avc", true, false),
                            DecoderEntry(VideoCodec.HEVC, "hevc", true, true, setOf(HdrFormat.HDR10), isTunneled = true),
                        ),
                    ),
                    display = DisplayInfo(1920, 1080, 60f, 3840, 2160, setOf(HdrFormat.HDR10)),
                    memory = MemoryInfo(2048, 192, false),
                    widevine = WidevineInfo.Available("L1", "HDCP-2.2", "1"),
                ),
            ),
            onIntent = {},
        )
    }
}
