package com.example.livex.settings

import androidx.lifecycle.viewModelScope
import com.example.livex.core.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BufferSettingsViewModel @Inject constructor(
    private val repository: BufferSettingsRepository,
) : MviViewModel<BufferSettingsState, BufferSettingsIntent, BufferSettingsEffect>(BufferSettingsState()) {

    init {
        viewModelScope.launch {
            val saved = repository.settings.first()
            setState { copy(isLoading = false, saved = saved, draft = saved) }
        }
    }

    override fun handleIntent(intent: BufferSettingsIntent) {
        when (intent) {
            is BufferSettingsIntent.Change -> setState { copy(draft = draft.with(intent.field, intent.valueMs)) }
            is BufferSettingsIntent.ApplyPreset -> setState { copy(draft = intent.preset.settings) }
            BufferSettingsIntent.ResetToMedia3Default -> setState { copy(draft = BufferSettings.MEDIA3_DEFAULT) }
            BufferSettingsIntent.Save -> save()
        }
    }

    private fun save() {
        val draft = currentState.draft
        viewModelScope.launch {
            repository.save(draft)
            setState { copy(saved = draft) }
            sendEffect(BufferSettingsEffect.Saved)
        }
    }
}
