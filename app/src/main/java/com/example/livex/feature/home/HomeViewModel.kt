package com.example.livex.feature.home

import androidx.lifecycle.viewModelScope
import com.example.livex.core.mvi.MviViewModel
import com.example.livex.data.greeting.GreetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val greetingRepository: GreetingRepository,
) :
    MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

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
                val greeting = greetingRepository.getGreeting()
                setState { copy(isLoading = false, greeting = greeting) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setState { copy(isLoading = false, error = e.message ?: "Something went wrong") }
            }
        }
    }
}
