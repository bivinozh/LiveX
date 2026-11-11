package com.example.livex.device

import androidx.lifecycle.viewModelScope
import com.example.livex.core.mvi.MviViewModel
import com.example.livex.core.mvi.UiEffect
import com.example.livex.core.mvi.UiIntent
import com.example.livex.core.mvi.UiState
import com.example.livex.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DeviceInfoState(
    val isLoading: Boolean = true,
    val info: DeviceInfo? = null,
    val error: String? = null,
) : UiState

sealed interface DeviceInfoIntent : UiIntent {
    data object Refresh : DeviceInfoIntent
}

sealed interface DeviceInfoEffect : UiEffect

@HiltViewModel
class DeviceInfoViewModel @Inject constructor(
    private val reader: DeviceInfoReader,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MviViewModel<DeviceInfoState, DeviceInfoIntent, DeviceInfoEffect>(DeviceInfoState()) {

    init {
        load()
    }

    override fun handleIntent(intent: DeviceInfoIntent) {
        when (intent) {
            DeviceInfoIntent.Refresh -> load()
        }
    }

    private fun load() {
        setState { copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                // MediaCodecList and MediaDrm do binder calls to the media server: not on the main thread.
                val info = withContext(ioDispatcher) { reader.read() }
                setState { copy(isLoading = false, info = info) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setState { copy(isLoading = false, error = e.message ?: "Couldn't read device capabilities") }
            }
        }
    }
}
