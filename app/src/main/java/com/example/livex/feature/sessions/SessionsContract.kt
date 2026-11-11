package com.example.livex.feature.sessions

import com.example.livex.core.mvi.UiEffect
import com.example.livex.core.mvi.UiIntent
import com.example.livex.core.mvi.UiState
import com.example.livex.qoe.SessionSummary

data class SessionsState(
    val isLoading: Boolean = true,
    /** Newest first. */
    val sessions: List<SessionSummary> = emptyList(),
) : UiState

sealed interface SessionsIntent : UiIntent {
    data object Clear : SessionsIntent
}

sealed interface SessionsEffect : UiEffect
