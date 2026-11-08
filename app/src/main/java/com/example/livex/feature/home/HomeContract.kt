package com.example.livex.feature.home

import com.example.livex.core.mvi.UiEffect
import com.example.livex.core.mvi.UiIntent
import com.example.livex.core.mvi.UiState
import com.example.livex.data.Stream

data class HomeState(
    val isLoading: Boolean = false,
    val streams: List<Stream> = emptyList(),
    val error: String? = null,
) : UiState

sealed interface HomeIntent : UiIntent {
    data object Load : HomeIntent
    data object Retry : HomeIntent
    data class StreamClicked(val streamId: String) : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data class NavigateToPlayer(val streamId: String) : HomeEffect
}
