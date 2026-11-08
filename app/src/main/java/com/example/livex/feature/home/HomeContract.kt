package com.example.livex.feature.home

import com.example.livex.core.mvi.UiEffect
import com.example.livex.core.mvi.UiIntent
import com.example.livex.core.mvi.UiState
import com.example.livex.core.utils.Constants.EMPTY

data class HomeState(
    val isLoading: Boolean = false,
    val greeting: String = EMPTY,
    val error: String? = null,
) : UiState

sealed interface HomeIntent : UiIntent {
    data object Load : HomeIntent
    data object Retry : HomeIntent
    data object GreetingClicked : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data class ShowMessage(val message: String) : HomeEffect
}
