package com.example.livex.feature.home

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.livex.core.mvi.CollectEffects
import com.example.livex.ui.theme.LiveXTheme

/** Stateful entry point: wires the ViewModel to the stateless [HomeContent]. */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is HomeEffect.ShowMessage ->
                Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
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
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            state.isLoading -> Text("Loading…")
            state.error != null -> {
                Text(state.error)
                Button(onClick = { onIntent(HomeIntent.Retry) }) { Text("Retry") }
            }
            else -> Button(onClick = { onIntent(HomeIntent.GreetingClicked) }) {
                Text(state.greeting)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    LiveXTheme {
        HomeContent(state = HomeState(greeting = "Hello Android!"), onIntent = {})
    }
}
