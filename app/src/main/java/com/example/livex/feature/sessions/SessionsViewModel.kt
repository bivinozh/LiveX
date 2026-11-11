package com.example.livex.feature.sessions

import androidx.lifecycle.viewModelScope
import com.example.livex.core.mvi.MviViewModel
import com.example.livex.qoe.store.QoeSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val repository: QoeSessionRepository,
) : MviViewModel<SessionsState, SessionsIntent, SessionsEffect>(SessionsState()) {

    init {
        // Room re-emits on every write, so an open session updates live.
        repository.sessions
            .onEach { sessions -> setState { copy(isLoading = false, sessions = sessions) } }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: SessionsIntent) {
        when (intent) {
            SessionsIntent.Clear -> viewModelScope.launch { repository.clear() }
        }
    }
}
