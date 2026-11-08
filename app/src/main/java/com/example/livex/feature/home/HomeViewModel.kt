package com.example.livex.feature.home

import androidx.lifecycle.viewModelScope
import com.example.livex.core.mvi.MviViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class HomeViewModel : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    init {
        onIntent(HomeIntent.Load)
    }

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Load, HomeIntent.Retry -> load()
            HomeIntent.GreetingClicked ->
                sendEffect(HomeEffect.ShowMessage("Welcome to LiveX!"))
        }
    }

    private fun load() {
        if (currentState.isLoading) return
        setState { copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                // TODO: replace with a real repository call.
                delay(500.milliseconds)
                setState { copy(isLoading = false, greeting = "Hello Android!") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setState { copy(isLoading = false, error = e.message ?: "Something went wrong") }
            }
        }
    }
}
