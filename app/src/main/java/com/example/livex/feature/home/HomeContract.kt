package com.example.livex.feature.home

import com.example.livex.core.mvi.UiEffect
import com.example.livex.core.mvi.UiIntent
import com.example.livex.core.mvi.UiState
import com.example.livex.data.Stream

data class HomeState(
    val isLoading: Boolean = false,
    val streams: List<Stream> = emptyList(),
    val error: String? = null,
    /**
     * Last stream the user opened. The Home composable is disposed while the player is on screen,
     * but this ViewModel lives in the back stack entry, so focus can return to this item on back.
     */
    val selectedStreamId: String? = null,
) : UiState

sealed interface HomeIntent : UiIntent {
    data object Load : HomeIntent
    data object Retry : HomeIntent
    data class StreamClicked(val streamId: String) : HomeIntent
    data object OpenSessions : HomeIntent
    data object OpenDeviceInfo : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data class NavigateToPlayer(val streamId: String) : HomeEffect
    data object NavigateToSessions : HomeEffect
    data object NavigateToDeviceInfo : HomeEffect
}
