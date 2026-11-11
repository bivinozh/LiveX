package com.example.livex.feature.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.livex.qoe.EndReason
import com.example.livex.qoe.SessionSummary
import com.example.livex.ui.theme.LiveXTheme
import java.time.ZoneId

@Composable
fun SessionsScreen(
    modifier: Modifier = Modifier,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SessionsContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

/** Per-session QoE summaries, newest first. Rows are focusable so the D-pad can scroll the list. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SessionsContent(
    state: SessionsState,
    onIntent: (SessionsIntent) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val initialFocus = remember { FocusRequester() }
    // TV: something must hold focus for the D-pad. Newest session, or Clear when there are none.
    // Wait one frame: lazy items are composed during layout, after this effect is launched.
    LaunchedEffect(state.isLoading, state.sessions.isEmpty()) {
        if (state.isLoading) return@LaunchedEffect
        withFrameNanos { }
        runCatching { initialFocus.requestFocus() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("QoE sessions", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "One row per play request: what a QoE dashboard (Conviva, Mux…) shows per view.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = { onIntent(SessionsIntent.Clear) },
                    modifier = if (state.sessions.isEmpty()) Modifier.focusRequester(initialFocus) else Modifier,
                ) { Text("Clear") }
            }
        }
        when {
            state.isLoading -> item { Text("Loading…") }
            state.sessions.isEmpty() -> item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Text("No sessions yet. Play a stream, then come back here.")
                }
            }
            else -> items(state.sessions, key = { it.sessionId }) { session ->
                SessionRow(
                    session = session,
                    zone = zone,
                    modifier = if (session == state.sessions.first()) Modifier.focusRequester(initialFocus) else Modifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SessionRow(session: SessionSummary, zone: ZoneId, modifier: Modifier = Modifier) {
    val isBad = session.failures > 0 || session.isEbvs
    Surface(onClick = {}, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                session.title + if (session.isLive) " · Live" else "",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(session.headerText(zone), style = MaterialTheme.typography.bodySmall)
            Text(
                session.metricsText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isBad) ERROR_COLOR else Color.Unspecified,
            )
            session.lastError?.let {
                Text("Last error: $it", style = MaterialTheme.typography.bodySmall, color = ERROR_COLOR)
            }
        }
    }
}

private val ERROR_COLOR = Color(0xFFFF8A80)

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun SessionsContentPreview() {
    LiveXTheme {
        SessionsContent(
            state = SessionsState(
                isLoading = false,
                sessions = listOf(
                    SessionSummary(
                        "a", "bbb", "Big Buck Bunny", false, 1_700_000_000_000, 0, durationMs = 95_000,
                        vstMs = 840, stallCount = 2, stallTimeMs = 1_900, playingTimeMs = 90_000,
                        avgBitrateBps = 2_400_000, bitrateSwitches = 4, endReason = EndReason.EXIT,
                    ),
                    SessionSummary(
                        "b", "wv", "Tears of Steel (Widevine HD)", false, 1_699_999_000_000, 0,
                        durationMs = 4_000, isEbvs = true, ebvsWaitMs = 4_000, endReason = EndReason.EXIT,
                    ),
                ),
            ),
            onIntent = {},
        )
    }
}
