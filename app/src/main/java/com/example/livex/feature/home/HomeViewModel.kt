package com.example.livex.feature.home

import androidx.lifecycle.viewModelScope
import com.example.livex.core.mvi.MviViewModel
import com.example.livex.data.stream.StreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    init {
        onIntent(HomeIntent.Load)
    }

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Load, HomeIntent.Retry -> load()
            // Navigation is a one-off event: the screen performs it, the ViewModel only requests it.
            is HomeIntent.StreamClicked -> {
                setState { copy(selectedStreamId = intent.streamId) }
                sendEffect(HomeEffect.NavigateToPlayer(intent.streamId))
            }
        }
    }

    private fun load() {
        if (currentState.isLoading) return
        setState { copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val streams = streamRepository.getStreams()
                setState { copy(isLoading = false, streams = streams) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setState { copy(isLoading = false, error = e.message ?: "Something went wrong") }
            }
        }
    }
}
