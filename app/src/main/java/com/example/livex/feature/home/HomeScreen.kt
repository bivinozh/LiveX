package com.example.livex.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.livex.core.mvi.CollectEffects
import com.example.livex.data.SampleStreams
import com.example.livex.data.Stream
import com.example.livex.ui.theme.LiveXTheme

/** Stateful entry point: wires the ViewModel to the stateless [HomeContent]. */
@Composable
fun HomeScreen(
    onStreamSelected: (streamId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is HomeEffect.NavigateToPlayer -> onStreamSelected(effect.streamId)
        }
    }

    HomeContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

/** Stateless UI: renders [HomeState] and emits [HomeIntent]s. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> Text("Loading…", modifier = Modifier.align(Alignment.Center))
            state.error != null -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(state.error)
                Button(onClick = { onIntent(HomeIntent.Retry) }) { Text("Retry") }
            }
            else -> StreamList(state.streams, onIntent)
        }
    }
}

@Composable
private fun StreamList(streams: List<Stream>, onIntent: (HomeIntent) -> Unit) {
    val firstItemFocus = remember { FocusRequester() }
    // TV has no touch: something must hold focus so the D-pad works immediately.
    LaunchedEffect(streams) { if (streams.isNotEmpty()) firstItemFocus.requestFocus() }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Choose a stream",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        itemsIndexed(streams, key = { _, stream -> stream.id }) { index, stream ->
            StreamItem(
                stream = stream,
                onClick = { onIntent(HomeIntent.StreamClicked(stream.id)) },
                modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StreamItem(stream: Stream, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(stream.title, style = MaterialTheme.typography.titleMedium)
            Text(stream.formatLabel, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun HomeContentPreview() {
    LiveXTheme {
        HomeContent(state = HomeState(streams = SampleStreams.all), onIntent = {})
    }
}
